package qikahome.tconlib.placeabletool.hook;

import java.util.Collection;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import qikahome.tconlib.TconLib;
import qikahome.tconlib.placeabletool.PlacedToolBlock.IToolBlockEntity;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.build.VolatileDataModifierHook;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;

/**
 * 放置工具方块的 tick 钩子：服务端每 tick 调用一次，供模块在方块存在期间持续工作（如计时、充能）。
 * <p>
 * 本接口同时是 {@link VolatileDataModifierHook}：实现它的模块默认写入 {@link #TICK_FLAG} 易失标记，
 * 并由 {@link #getDefaultHooks()} 自动同时注册 tick 钩子与易失数据钩子——模块只需实现
 * {@link #onTick}，无需重复声明 hook 或标记。
 * <p>
 * 标记用于 {@code PlacedToolBlock#getTicker} 判断是否需要 ticker：既无标记又无 tank 容量的放置工具
 * 完全不注册 ticker，避免大量放置工具时每 tick 的固定开销。
 */
public interface PlacedToolTickModifierHook extends VolatileDataModifierHook, HookProvider {
    /** 易失标记：工具带任意 tick 模块时为 true（由本接口的默认 {@link #addVolatileData} 写入） */
    ResourceLocation TICK_FLAG = TconLib.getResource("placed_tool_tick");

    /** 默认同时声明 tick 钩子与易失数据钩子 */
    @Override
    default List<ModuleHook<?>> getDefaultHooks() {
        return HookProvider.defaultHooks(TconLib.PLACED_TOOL_TICK_HOOK, ModifierHooks.VOLATILE_DATA);
    }

    /** 默认写入 tick 标记 */
    @Override
    default void addVolatileData(IToolContext context, ModifierEntry modifier, ToolDataNBT volatileData) {
        volatileData.putBoolean(TICK_FLAG, true);
    }

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
