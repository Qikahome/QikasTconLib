package qikahome.tconlib.placeabletool.hook;

import java.util.Collection;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InteractionSource;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * 放置逻辑钩子：在工具落块前对"最终要放置进方块的方块状态和物品"做最后定制。
 * <p>
 * 多个模块挂载时按模块顺序串联执行：上一个钩子返回的 {@link BlockState} 会作为下一个钩子的入参，
 * 任意钩子返回 {@code null} 都会立即中断整条链（见 {@link AllMerger}）。
 */
public interface ToolPlacingModifierHook {
    /**
     * 决定最终放置到方块里的 {@link BlockState} 与物品。
     * <p>
     * {@code state} 和 {@code toolStack} 即最终要写入放置方块的内容：
     * <ul>
     *   <li>{@code state} — 当前拟放置的方块状态。{@link BlockState} 是不可变实例，无法原地修改，
     *       要覆盖放置结果必须基于它构造新状态（如 {@code state.setValue(...)}）作为返回值返回；</li>
     *   <li>{@code toolStack} — 将要存入方块实体的物品，可直接就地修改（如调整 NBT），修改结果随物品一起落块。</li>
     * </ul>
     *
     * @param tool         当前放置操作涉及的完整工具视图（含全部修饰器数据，可读写易失/持久数据）
     * @param modifier     触发本钩子的修饰器条目
     * @param context      原始交互上下文（手持物品、玩家、被点击的面等）
     * @param placeContext 已由交互上下文推导出的放置上下文（含最终落块位置）
     * @param source       交互来源（右手/左手/护甲）；{@code null} 表示非交互放置（如弹射物命中放置）
     * @param state        当前拟放置的方块状态（不可变实例，需覆盖时构造新状态返回）
     * @param toolStack    将要存入方块实体的物品，可直接就地修改
     * @return 最终放置的方块状态；不改动时原样返回 {@code state}，返回 {@code null} 表示终止后续钩子处理（调用方将其视为放置失败/取消）
     */
    BlockState onPlace(IToolStackView tool, ModifierEntry modifier, UseOnContext context,
            BlockPlaceContext placeContext, @Nullable InteractionSource source, BlockState state, ItemStack toolStack);

    /** 将多个钩子按集合顺序合并：每次把上一个返回值传给下一个，任一返回 {@code null} 即中断整条链并返回 {@code null} */
    record AllMerger(Collection<ToolPlacingModifierHook> modules) implements ToolPlacingModifierHook {
        @Override
        public BlockState onPlace(IToolStackView tool, ModifierEntry modifier, UseOnContext context,
                BlockPlaceContext placeContext, @Nullable InteractionSource source, BlockState state, ItemStack toolStack) {
            for (ToolPlacingModifierHook hook : modules) {
                state = hook.onPlace(tool, modifier, context, placeContext, source, state, toolStack);
                if (state == null) {
                    break;
                }
            }
            return state;
        }
    }
}
