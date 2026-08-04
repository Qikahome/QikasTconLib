package qikahome.tconlib.placeabletool;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import qikahome.tconlib.TconLib;
import slimeknights.mantle.data.loadable.primitive.BooleanLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.build.VolatileDataModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.BlockInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InteractionSource;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;

import static net.minecraft.world.InteractionResult.*;

@SuppressWarnings({ "null" })
public record PlacingModule(boolean requireSneaking, boolean beforeBlockUse)
        implements ModifierModule, BlockInteractionModifierHook, VolatileDataModifierHook {
    /** 工具是否具备"放置"能力的易失标记（护甲等不走 useOn 的工具可在交互事件里读取它判断是否调用放置逻辑） */
    public static final ResourceLocation CAN_PLACE = TconLib.getResource("can_place");
    private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.defaultHooks(ModifierHooks.BLOCK_INTERACT,
            ModifierHooks.VOLATILE_DATA);
    public static final RecordLoadable<PlacingModule> LOADER = RecordLoadable.create(
            BooleanLoadable.DEFAULT.defaultField("require_sneaking", true, PlacingModule::requireSneaking),
            BooleanLoadable.DEFAULT.defaultField("before_block_use", false, PlacingModule::beforeBlockUse),
            PlacingModule::new);

    @Override
    public List<ModuleHook<?>> getDefaultHooks() {
        return DEFAULT_HOOKS;
    }

    @Override
    public RecordLoadable<? extends ModifierModule> getLoader() {
        return LOADER;
    }

    @Override
    public void addVolatileData(IToolContext context, ModifierEntry modifier, ToolDataNBT volatileData) {
        volatileData.putBoolean(CAN_PLACE, true);
    }

    @Override
    public InteractionResult beforeBlockUse(IToolStackView tool, ModifierEntry modifier, UseOnContext context,
            InteractionSource source) {
        if (beforeBlockUse)
            return afterBlockUse(tool, modifier, context, source);
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult afterBlockUse(IToolStackView tool, ModifierEntry modifier, UseOnContext context,
            InteractionSource source) {
        // 工具已损坏或当前交互来源不允许时，跳过
        if (tool.isBroken() || !tool.getHook(ToolHooks.INTERACTION).canInteract(tool, modifier.getId(), source)
                || source == InteractionSource.ARMOR) {
            return PASS;
        }
        Player player = context.getPlayer();
        // 需要潜行时，必须处于潜行状态
        if (player == null || (requireSneaking && !player.isShiftKeyDown())) {
            return PASS;
        }
        BlockPlaceContext placingContext = new BlockPlaceContext(context);
        if (!placingContext.canPlace()) {
            return PASS;
        }
        Level level = context.getLevel();
        // 客户端只返回 CONSUME 以播放手臂动画，实际放置由服务端执行
        if (level.isClientSide) {
            return CONSUME;
        }
        BlockPos pos = placingContext.getClickedPos();
        ItemStack stack = context.getItemInHand().copy();
        BlockState state = TconLib.PLACED_TOOL.get().getStateForPlacement(placingContext);
        for (var entry : tool.getModifierList()) {
            state = entry.getHook(TconLib.TOOL_PLACING_MODIFIER_HOOK).onPlace(tool, modifier, context, placingContext,
                    source, state, stack);
            if (state == null)
                break;
        }
        if (state == null || !level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return FAIL;
        }
        // 注意：不能用 player.getUseItem()，那是"使用中"的物品（吃食物/拉弓时才有值）；
        // TCon 交互钩子不会触发物品 use 动画，所以这里返回的是 AIR。
        // 正确做法是从 UseOnContext 直接取被点击那只手里的物品。

        // setBlock 时 BaseEntityBlock 已自动创建了 BlockEntity，这里取出并写入工具
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PlacedToolBlock.PlacedToolBlockEntity ptbe)) {
            TconLib.LOGGER.error("Failed to place tool at {}: expected PlacedToolBlockEntity, got {}", pos,
                    be == null ? "null" : be.getClass().getName());
            return FAIL;
        }
        ptbe.setStack(stack);
        // 播放放置音效
        SoundType soundType = state.getSoundType(level, pos, context.getPlayer());
        level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        // 消耗手中的工具（缩原物品，stack 是副本不受影响）
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return SUCCESS;
    }

}
