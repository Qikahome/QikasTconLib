package qikahome.tconlib.hook;

import java.util.Collection;

import javax.annotation.Nullable;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

public interface ImageTooltipModifierHook {
    @Nullable
    TooltipComponent getTooltipImage(IToolStackView tool, ModifierEntry modifier);

    record FirstMerger(Collection<ImageTooltipModifierHook> modules) implements ImageTooltipModifierHook {

        @Override
        public TooltipComponent getTooltipImage(IToolStackView tool, ModifierEntry modifier) {
            TooltipComponent result = null;
            for (var module : modules) {
                result = module.getTooltipImage(tool, modifier);
                if (result != null)
                    break;
            }
            return result;
        }

    }
}
