package qikahome.tconlib.mixin;

import java.util.ArrayList;
import java.util.List;
import org.anti_ad.mc.ipnext.inventory.ItemArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import slimeknights.tconstruct.tools.menu.ToolContainerMenu;

// import org.anti_ad.mc.ipnext.inventory.AreaTypes$sortableItemStorage$1;
// import org.anti_ad.mc.ipnext.inventory.AreaTypes$itemStorage$1;
@Mixin(targets = { "org.anti_ad.mc.ipnext.inventory.AreaTypes$sortableItemStorage$1",
        "org.anti_ad.mc.ipnext.inventory.AreaTypes$itemStorage$1" }, remap = false)
@Pseudo
public class MixinAreaTypes {
    @Inject(method = "getItemArea", at = @At("HEAD"), cancellable = true)
    private void tconlib$ToolContainerMenu(AbstractContainerMenu var1, List<Slot> var2,
            CallbackInfoReturnable<ItemArea> ci) {
        if (var1 instanceof ToolContainerMenu toolMenu) {
            List<Integer> list = new ArrayList<>();
            int h = toolMenu.getCraftingHeight();
            for (int i = h == 0 ? 0 : h * h + 1; i < toolMenu.getPlayerInventoryStart(); i++) {
                list.add(i);
            }
            ci.setReturnValue(ItemArea.Companion.invoke(var2, list, false));
        }
    }
}
