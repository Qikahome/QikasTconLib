package qikahome.tconlib.placeabletool;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import qikahome.tconlib.TconLib;
import qikahome.tconlib.placeabletool.hook.PlacedToolFluidStateChangeModifierHook;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.loadable.record.SingletonLoader;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/** 放置工具方块的"可被水冲走"模块：包含它的 modifier 使方块允许被水流冲掉 */
public record WashableModule() implements ModifierModule, PlacedToolFluidStateChangeModifierHook {
    /** 单例（无字段模块） */
    public static final WashableModule INSTANCE = new WashableModule();
    private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider
            .defaultHooks(TconLib.PLACED_TOOL_FLUID_STATE_CHANGE_HOOK);
    public static final RecordLoadable<WashableModule> LOADER = new SingletonLoader<>(INSTANCE);

    @Override
    public List<ModuleHook<?>> getDefaultHooks() {
        return DEFAULT_HOOKS;
    }

    @Override
    public RecordLoadable<? extends ModifierModule> getLoader() {
        return LOADER;
    }

    @Override
    public boolean canBeWashedAway(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level,
            BlockPos pos, FluidState fluid) {
        // 允许被水流冲掉：水流动过时方块被替换为流体，工具经 onRemove 掉落
        return true;
    }

    // onWaterloggedChanged 保持默认空实现（void 通知型，本模块不关心含水变化）
}
