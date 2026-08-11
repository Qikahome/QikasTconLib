package qikahome.tconlib.mixin;

import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.anti_ad.mc.ipnext.item.ItemStackExtensionsKt;
import org.anti_ad.mc.ipnext.item.MutableItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qikahome.tconlib.MixinConstants;

/**
 * IPN 兼容：模拟转移（transferTo）时尊重真实槽的 mayPlace 与容量。
 * IPN 沙盒是纯物品级（无槽引用），会把工具规划进 TCon 工具槽（mayPlace 拒绝）
 * 或超过工匠箱槽容量（16），导致真实点击被服务端拒绝、物品挂手。
 * MixinItemPlanner 在整理期间登记了"栈对象 → 槽索引"的 IdentityHashMap，
 * 这里 O(1) 映射到真实槽后做判定。IPN 未安装时整个 mixin 静默跳过。
 */
@Pseudo
@Mixin(value = ItemStackExtensionsKt.class, remap = false)
public abstract class MixinItemStackExtensionsKt {
  @Inject(method = "transferNTo(Lorg/anti_ad/mc/ipnext/item/MutableItemStack;Lorg/anti_ad/mc/ipnext/item/MutableItemStack;I)V", at = @At("HEAD"), cancellable = true)
  private static void tconlib$respectMayPlace(MutableItemStack source, MutableItemStack target, int n,
      CallbackInfo cb) {
    // 非整理沙盒内（或 IPN 未登记）零开销短路
    IdentityHashMap<MutableItemStack, Integer> map = MixinConstants.IPN_SLOT_MAP.get();
    if (map == null)
      return;
    Integer idx = map.get(target);
    if (idx == null)
      return;
    AbstractContainerMenu menu = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.containerMenu
        : null;
    if (menu == null || idx >= menu.slots.size())
      return;
    Slot slot = menu.slots.get(idx);
    ItemStack vanilla = ItemStackExtensionsKt.getVanillaStack(source);
    // 尊重真实槽的 mayPlace（工具嵌套等）与容量（工匠箱 16）
    int limit = slot.getMaxStackSize(vanilla);
    if (!slot.mayPlace(vanilla) || target.getCount() + n > limit)
      cb.cancel();
    if (target.getCount() + n > limit && target.getCount() < limit)
      ItemStackExtensionsKt.transferNTo(source, target, limit - target.getCount());
  }
}
