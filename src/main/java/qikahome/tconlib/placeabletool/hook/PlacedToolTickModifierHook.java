package qikahome.tconlib.placeabletool.hook;

import java.util.Collection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import qikahome.tconlib.placeabletool.PlacedToolBlock.IToolBlockEntity;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/** 放置工具方块的 tick 钩子：服务端每 tick 调用一次，供模块在方块存在期间持续工作（如计时、充能） */
public interface PlacedToolTickModifierHook {

    /**
     * 服务端每 tick 执行（方块存在期间持续调用）。
     *
     * @param tool     方块中放置的工具
     * @param modifier 触发本钩子的修饰器条目
     * @param state    方块的方块状态
     * @param level    方块所在世界（服务端）
     * @param pos      方块位置
     * @param ptbe     放置工具方块实体
     */
    void onTick(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level, BlockPos pos,
            IToolBlockEntity ptbe);

    /** 按集合顺序依次执行所有钩子 */
    record AllMerger(Collection<PlacedToolTickModifierHook> modules) implements PlacedToolTickModifierHook {
        @Override
        public void onTick(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level, BlockPos pos,
                IToolBlockEntity ptbe) {
            for (PlacedToolTickModifierHook hook : modules) {
                hook.onTick(tool, modifier, state, level, pos, ptbe);
            }
        }
    }
}
