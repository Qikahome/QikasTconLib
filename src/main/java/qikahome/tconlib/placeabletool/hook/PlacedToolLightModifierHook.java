package qikahome.tconlib.placeabletool.hook;

import java.util.Collection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import qikahome.tconlib.placeabletool.PlacedToolBlock.PlacedToolBlockEntity;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * 放置工具方块的发光钩子：按工具 modifier 顺序折叠计算方块的发光等级（0-15）。
 * 每个模块接收前一个模块算出的 {@code light}，返回自己的结果（通常是取更大值/覆盖）。
 */
public interface PlacedToolLightModifierHook {

    /**
     * 计算发光等级。
     *
     * @param tool     方块中放置的工具
     * @param modifier 触发本钩子的修饰器条目
     * @param state    方块的方块状态
     * @param level    方块所在世界
     * @param pos      方块位置
     * @param ptbe     放置工具方块实体
     * @param light    前一个钩子算出的发光等级
     * @return 新的发光等级（调用方会夹取到 0-15）
     */
    int getLightLevel(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level, BlockPos pos,
            PlacedToolBlockEntity ptbe, int light);

    /** 按集合顺序折叠执行所有钩子：前一个的结果作为后一个的输入 */
    record AllMerger(Collection<PlacedToolLightModifierHook> modules) implements PlacedToolLightModifierHook {
        @Override
        public int getLightLevel(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level,
                BlockPos pos, PlacedToolBlockEntity ptbe, int light) {
            for (PlacedToolLightModifierHook hook : modules) {
                light = hook.getLightLevel(tool, modifier, state, level, pos, ptbe, light);
            }
            return light;
        }
    }
}
