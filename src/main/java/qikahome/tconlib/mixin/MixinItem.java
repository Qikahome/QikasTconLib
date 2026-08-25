package qikahome.tconlib.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import qikahome.tconlib.TconLib;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

@Mixin(Item.class)
public class MixinItem {
    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    public void tconlib$getTooltipImage(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cb) {
        if (this instanceof IModifiable) {
            var tool = ToolStack.from(stack);
            var mod = tool.getModifiers();
            TooltipComponent result;
            for (var entry : mod) {
                result = entry.getHook(TconLib.IMAGE_TOOLTIP_MODIFIER_HOOK).getTooltipImage(tool, entry);
                if (result != null) {
                    cb.setReturnValue(Optional.of(result));
                    return;
                }
            }
        }
    }
}
