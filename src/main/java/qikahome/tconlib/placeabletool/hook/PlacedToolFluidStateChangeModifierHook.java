package qikahome.tconlib.placeabletool.hook;

import java.util.Collection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * 放置工具方块的流体状态钩子：决定方块能否被水流冲掉，并接收含水状态变化通知。
 * <p>
 * 两个触发场景：
 * <ul>
 *   <li>水流动过：流体扩散到方块时调用 {@link #canBeWashedAway}，返回 {@code true} 表示允许被水冲掉
 *       （方块被替换为流体状态，工具经 onRemove 掉落）；默认 {@code false} 保持不可被冲；</li>
 *   <li>直接把水放进来：方块的 WATERLOGGED 状态变化（变 true/变 false）时调用
 *       {@link #onWaterloggedChanged} 通知模块。</li>
 * </ul>
 */
public interface PlacedToolFluidStateChangeModifierHook {

    /**
     * 水流动过该方块时调用：决定方块是否允许被水流冲掉。
     * <p>
     * 对应原版 {@code Block.canBeReplacedByFluid} 的判定，默认返回 {@code false}（不可被冲）。
     * 返回 {@code true} 时流体扩散会把本方块替换为流体状态，工具经 {@code onRemove} 掉落。
     *
     * @param tool     方块中放置的工具
     * @param modifier 触发本钩子的修饰器条目
     * @param state    方块的方块状态
     * @param level    方块所在世界
     * @param pos      方块位置
     * @param fluid    尝试占据该位置的流体状态
     * @return 是否允许被水流冲掉；默认 {@code false}
     */
    default boolean canBeWashedAway(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level,
            BlockPos pos, FluidState fluid) {
        return false;
    }

    /**
     * 方块的含水状态变化时调用（水被直接放入方块或从方块排出）。
     *
     * @param tool        方块中放置的工具
     * @param modifier    触发本钩子的修饰器条目
     * @param state       变化后的方块状态
     * @param level       方块所在世界
     * @param pos         方块位置
     * @param waterlogged 新的含水状态（true = 现在含水）
     */
    default void onWaterloggedChanged(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level,
            BlockPos pos, boolean waterlogged) {
    }

    /** 将多个钩子按集合顺序合并：{@link #canBeWashedAway} 任一返回 true 即短路返回 true；{@link #onWaterloggedChanged} 全部依次调用 */
    record AllMerger(Collection<PlacedToolFluidStateChangeModifierHook> modules)
            implements PlacedToolFluidStateChangeModifierHook {
        @Override
        public boolean canBeWashedAway(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level,
                BlockPos pos, FluidState fluid) {
            for (PlacedToolFluidStateChangeModifierHook hook : modules) {
                if (hook.canBeWashedAway(tool, modifier, state, level, pos, fluid)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onWaterloggedChanged(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level,
                BlockPos pos, boolean waterlogged) {
            for (PlacedToolFluidStateChangeModifierHook hook : modules) {
                hook.onWaterloggedChanged(tool, modifier, state, level, pos, waterlogged);
            }
        }
    }
}
