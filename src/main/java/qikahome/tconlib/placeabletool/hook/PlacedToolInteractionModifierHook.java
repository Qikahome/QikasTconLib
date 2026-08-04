package qikahome.tconlib.placeabletool.hook;

import java.util.Collection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import qikahome.tconlib.placeabletool.PlacedToolBlock.PlacedToolBlockEntity;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * 放置工具方块的交互钩子：在方块被右键时，于内建逻辑（流体交互/打开物品栏）之前和之后
 * 给模块自定义执行的机会。返回 {@code PASS} 表示不处理、继续后续逻辑；
 * 返回消耗动作的结果（{@link InteractionResult#consumesAction()}）则会立即短路返回（见 {@link AllMerger}）。
 */
public interface PlacedToolInteractionModifierHook {

    /**
     * 打开物品栏之前执行：可在此短路交互（返回消耗动作的结果）以完全自定义行为，或返回 {@code PASS} 放行内建逻辑。
     *
     * @param tool     方块中放置的工具
     * @param modifier 触发本钩子的修饰器条目
     * @param state    方块的方块状态
     * @param level    方块所在世界
     * @param pos      方块位置
     * @param player   交互的玩家
     * @param hand     交互使用的手
     * @param hit      点击命中信息
     * @param ptbe     放置工具方块实体
     * @return 交互结果；默认 {@code PASS}
     */
    default InteractionResult before(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, PlacedToolBlockEntity ptbe) {
        return InteractionResult.PASS;
    }

    /**
     * 打开物品栏之后执行：内建逻辑已处理完交互后的兜底/扩展机会，同样返回 {@code PASS} 表示不处理。
     *
     * @param tool     方块中放置的工具
     * @param modifier 触发本钩子的修饰器条目
     * @param state    方块的方块状态
     * @param level    方块所在世界
     * @param pos      方块位置
     * @param player   交互的玩家
     * @param hand     交互使用的手
     * @param hit      点击命中信息
     * @param ptbe     放置工具方块实体
     * @return 交互结果；默认 {@code PASS}
     */
    default InteractionResult after(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, PlacedToolBlockEntity ptbe) {
        return InteractionResult.PASS;
    }

    /** 将多个钩子按集合顺序合并：返回消耗动作的结果（consumesAction）即短路并返回该结果，否则汇总最后一次的结果（可能为 FAIL） */
    record AllMerger(Collection<PlacedToolInteractionModifierHook> modules) implements PlacedToolInteractionModifierHook {
        @Override
        public InteractionResult before(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level,
                BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, PlacedToolBlockEntity ptbe) {
            InteractionResult result = InteractionResult.PASS;
            for (PlacedToolInteractionModifierHook hook : modules) {
                result = hook.before(tool, modifier, state, level, pos, player, hand, hit, ptbe);
                if (result.consumesAction()) {
                    return result;
                }
            }
            return result;
        }

        @Override
        public InteractionResult after(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level,
                BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, PlacedToolBlockEntity ptbe) {
            InteractionResult result = InteractionResult.PASS;
            for (PlacedToolInteractionModifierHook hook : modules) {
                result = hook.after(tool, modifier, state, level, pos, player, hand, hit, ptbe);
                if (result.consumesAction()) {
                    return result;
                }
            }
            return result;
        }
    }
}
