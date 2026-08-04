package qikahome.tconlib.placeabletool;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import qikahome.tconlib.TconLib;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.loadable.record.SingletonLoader;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.build.VolatileDataModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileHitModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.entity.ToolProjectile;

public enum ProjectileToolPlacingModule implements ModifierModule, ProjectileHitModifierHook, VolatileDataModifierHook {
    INSTANCE;

    public static final RecordLoadable<ProjectileToolPlacingModule> LOADER = new SingletonLoader(INSTANCE);
    private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.defaultHooks(ModifierHooks.PROJECTILE_HIT,
            ModifierHooks.VOLATILE_DATA);
    public static final ResourceLocation HIT_PLACE = TconLib.getResource("hit_place");

    @Override
    public List<ModuleHook<?>> getDefaultHooks() {
        return DEFAULT_HOOKS;
    }

    @Override
    public RecordLoadable<? extends ModifierModule> getLoader() {
        return LOADER;
    }

    @Override
    public boolean onProjectileHitsBlock(ModifierNBT modifiers, ModDataNBT persistentData, ModifierEntry modifier,
            Projectile projectile, BlockHitResult hit, @Nullable LivingEntity owner) {
        // 仅处理工具投掷物（ThrownTool / ThrownShuriken 均实现 ToolProjectile），普通箭矢不处理
        if (!(projectile instanceof ToolProjectile toolProjectile)) {
            return false;
        }
        ItemStack stack = toolProjectile.getDisplayTool().copy();
        if (stack.isEmpty()) {
            return false;
        }
        ToolStack tool = ToolStack.from(stack);
        // 未带"命中放置"模块（volatile 标记）或工具已损坏时，走默认逻辑
        if (tool.isBroken() || !tool.getVolatileData().getBoolean(HIT_PLACE)) {
            return false;
        }
        Level level = projectile.level();
        // 实际放置由服务端执行，客户端直接放行
        if (level.isClientSide) {
            return false;
        }
        Player player = owner instanceof Player p ? p : null;
        UseOnContext context = new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, hit);
        BlockPlaceContext placingContext = new BlockPlaceContext(context);
        if (!placingContext.canPlace()) {
            return false;
        }
        BlockPos pos = placingContext.getClickedPos();
        BlockState state = TconLib.PLACED_TOOL.get().getStateForPlacement(placingContext);
        for (var entry : tool.getModifierList()) {
            state = entry.getHook(TconLib.TOOL_PLACING_MODIFIER_HOOK).onPlace(tool, entry, context, placingContext,
                    null, state, stack);
            if (state == null) {
                break;
            }
        }
        if (state == null || !level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof PlacedToolBlock.PlacedToolBlockEntity ptbe)) {
            TconLib.LOGGER.error("Failed to place thrown tool at {}: expected PlacedToolBlockEntity, got {}", pos,
                    level.getBlockEntity(pos) == null ? "null" : level.getBlockEntity(pos).getClass().getName());
            return false;
        }
        ptbe.setStack(stack);
        // 播放放置音效
        SoundType soundType = state.getSoundType(level, pos, null);
        level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        // 销毁投掷物（ThrownTool 落地点会掉出工具 / ThrownShuriken 会回收弹回），避免重复掉落
        projectile.discard();
        // 取消命中事件，阻止其余逻辑
        return true;
    }

    @Override
    public void addVolatileData(IToolContext context, ModifierEntry modifier, ToolDataNBT volatileData) {
        volatileData.putBoolean(HIT_PLACE, true);
    }

}
