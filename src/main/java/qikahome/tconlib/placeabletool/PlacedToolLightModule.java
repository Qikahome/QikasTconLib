package qikahome.tconlib.placeabletool;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import qikahome.tconlib.TconLib;
import qikahome.tconlib.placeabletool.PlacedToolBlock.IToolBlockEntity;
import qikahome.tconlib.placeabletool.hook.PlacedToolLightModifierHook;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.loadable.record.SingletonLoader;
import slimeknights.tconstruct.library.json.LevelingInt;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolTankHelper;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/** 放置工具方块的发光模块集合（{@link PlacedToolLightModifierHook} 的默认实现） */
public final class PlacedToolLightModule {
    private PlacedToolLightModule() {
    }

    /** 按工具 tank 中的流体光等级决定发光（熔岩亮、水不亮）；空 tank 保持原值 */
    public record TankLightModule() implements ModifierModule, PlacedToolLightModifierHook {
        /** 单例（无字段模块） */
        public static final TankLightModule INSTANCE = new TankLightModule();
        private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider
                .defaultHooks(TconLib.PLACED_TOOL_LIGHT_HOOK);
        public static final RecordLoadable<TankLightModule> LOADER = new SingletonLoader<>(INSTANCE);

        @Override
        public List<ModuleHook<?>> getDefaultHooks() {
            return DEFAULT_HOOKS;
        }

        @Override
        public RecordLoadable<? extends ModifierModule> getLoader() {
            return LOADER;
        }

        @Override
        public int getLightLevel(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level,
                BlockPos pos, IToolBlockEntity ptbe, int light) {
            FluidStack fluid = ToolTankHelper.TANK_HELPER.getFluid(tool);
            return fluid.isEmpty() ? light
                    : Math.max(light, fluid.getFluid().getFluidType().getLightLevel(fluid));
        }
    }

    /** 亮度下限：使最终发光等级至少达到给定值，随 modifier 等级成长（flat + each_level × level） */
    public record MinimumLightModule(LevelingInt light) implements ModifierModule, PlacedToolLightModifierHook {
        private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider
                .defaultHooks(TconLib.PLACED_TOOL_LIGHT_HOOK);
        public static final RecordLoadable<MinimumLightModule> LOADER = RecordLoadable.create(
                LevelingInt.LOADABLE.defaultField("light", LevelingInt.ZERO, false, MinimumLightModule::light),
                MinimumLightModule::new);

        @Override
        public List<ModuleHook<?>> getDefaultHooks() {
            return DEFAULT_HOOKS;
        }

        @Override
        public RecordLoadable<? extends ModifierModule> getLoader() {
            return LOADER;
        }

        @Override
        public int getLightLevel(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level,
                BlockPos pos, IToolBlockEntity ptbe, int light) {
            return Math.max(light, this.light.compute(modifier.getLevel()));
        }
    }
}
