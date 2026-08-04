package qikahome.tconlib.placeabletool;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import qikahome.tconlib.TconLib;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 发射器放置行为，按优先级处理：
 * <ol>
 *   <li>盔甲先尝试给发射口前方实体穿上（原版行为），穿不上再往下走；</li>
 *   <li>带 placing（CAN_PLACE）且未损坏的工具在发射口前方放置工具方块；</li>
 *   <li>其余弹出掉落物。</li>
 * </ol>
 * <p>
 * 直接实现 {@link DispenseItemBehavior} 而非继承 {@link net.minecraft.core.dispenser.OptionalDispenseItemBehavior}：
 * 后者是单例复用时 success 字段会跨 dispense 调用残留，导致"某次失败后所有放置成功都播失败音效"。
 * 本类每次 dispense 显式计算成功与否，并自行播放音效（1000=成功/1001=失败）与发射动画（2000）。
 */
public class ToolDispenserBehavior implements DispenseItemBehavior {

    /** 对所有 ModifiableItem / ModifiableLauncherItem / ModifiableArmorItem 复用同一个单例行为 */
    public static final ToolDispenserBehavior INSTANCE = new ToolDispenserBehavior();

    @Override
    public ItemStack dispense(BlockSource source, ItemStack stack) {
        boolean success = execute(source, stack);
        // 模拟 DefaultDispenseItemBehavior 的音效与发射动画
        Level level = source.getLevel();
        level.levelEvent(success ? 1000 : 1001, source.getPos(), 0);
        level.levelEvent(2000, source.getPos(),
                source.getBlockState().getValue(DispenserBlock.FACING).get3DDataValue());
        return stack;
    }

    /** 执行放置/穿戴/弹出，返回是否成功（决定发射器音效） */
    private static boolean execute(BlockSource source, ItemStack stack) {
        // 盔甲优先试穿：前方有可装备实体则穿上，否则继续走放置/弹出
        if (stack.getItem() instanceof ArmorItem) {
            if (tryEquipArmor(source, stack)) {
                return true;
            }
        }
        ToolStack tool = ToolStack.from(stack);
        if (!tool.isBroken() && tool.getVolatileData().getBoolean(PlacingModule.CAN_PLACE)) {
            return tryPlace(source, stack, tool);
        }
        // 无 placing：弹出掉落物（算成功）
        return shootOut(source, stack);
    }

    /** 给发射口前方单位方块内的实体穿上盔甲（原版 ArmorItem.DispenseArmorBehavior 语义） */
    private static boolean tryEquipArmor(BlockSource source, ItemStack stack) {
        Level level = source.getLevel();
        Direction facing = source.getBlockState().getValue(DispenserBlock.FACING);
        BlockPos pos = source.getPos().relative(facing);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos),
                entity -> !entity.isSpectator() && entity.getItemBySlot(Mob.getEquipmentSlotForItem(stack)).isEmpty());
        if (entities.isEmpty()) {
            return false;
        }
        LivingEntity target = entities.get(0);
        EquipmentSlot slot = Mob.getEquipmentSlotForItem(stack);
        target.setItemSlot(slot, stack.split(1));
        if (target instanceof Mob mob) {
            mob.setDropChance(slot, 2.0F);
        }
        return true;
    }

    /** 放置工具方块；失败返回 false（物品退回发射器） */
    private static boolean tryPlace(BlockSource source, ItemStack stack, ToolStack tool) {
        Level level = source.getLevel();
        Direction facing = source.getBlockState().getValue(DispenserBlock.FACING);
        BlockPos pos = source.getPos().relative(facing);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), facing.getOpposite(), pos, false);
        // player 为 null 与发射器放置的标准用法一致（原版 DispenserBlock 放 BlockItem 即如此）
        UseOnContext useContext = new UseOnContext(level, null, InteractionHand.MAIN_HAND, stack, hit);
        BlockPlaceContext placeContext = new BlockPlaceContext(useContext);
        if (!placeContext.canPlace()) {
            return false;
        }
        BlockState state = TconLib.PLACED_TOOL.get().getStateForPlacement(placeContext);
        // 放置 hook 链：modifier 可调整最终 state；source 传 null 表示非交互放置
        for (ModifierEntry entry : tool.getModifierList()) {
            state = entry.getHook(TconLib.TOOL_PLACING_MODIFIER_HOOK).onPlace(tool, entry, useContext, placeContext,
                    null, state, stack);
            if (state == null) {
                return false;
            }
        }
        if (state == null || !level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof PlacedToolBlock.PlacedToolBlockEntity ptbe) {
            ptbe.setStack(stack.copy());
        } else {
            // BE 创建失败：回滚并视为失败
            level.removeBlock(pos, false);
            return false;
        }
        level.playSound(null, pos, state.getSoundType(level, pos, null).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        stack.shrink(1);
        return true;
    }

    /** 无 placing 的普通工具：从发射口弹出掉落物（原版默认行为） */
    private static boolean shootOut(BlockSource source, ItemStack stack) {
        Direction facing = source.getBlockState().getValue(DispenserBlock.FACING);
        Position position = DispenserBlock.getDispensePosition(source);
        double x = position.x() + facing.getStepX() * 0.1;
        double y = position.y() + facing.getStepY() * 0.1;
        double z = position.z() + facing.getStepZ() * 0.1;
        ItemEntity entity = new ItemEntity(source.getLevel(), x, y, z, stack);
        entity.setDeltaMovement(facing.getStepX() * 0.1, 0.1, facing.getStepZ() * 0.1);
        source.getLevel().addFreshEntity(entity);
        return true;
    }
}
