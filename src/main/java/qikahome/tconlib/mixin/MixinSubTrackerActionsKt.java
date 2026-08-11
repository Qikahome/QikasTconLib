package qikahome.tconlib.mixin;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.anti_ad.mc.ipnext.inventory.action.SubTrackerActionsKt;
import org.anti_ad.mc.ipnext.item.ItemStack;
import org.anti_ad.mc.ipnext.item.ItemStackExtensionsKt;
import org.anti_ad.mc.ipnext.item.MutableItemStack;
import org.anti_ad.mc.ipnext.item.rule.Rule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import qikahome.tconlib.MixinConstants;
import slimeknights.tconstruct.tables.menu.TinkerChestContainerMenu;

/**
 * IPN 兼容：排序（sortItems）结果按真实槽容量修正。
 * IPN 排序按物品级 64 打包，工匠箱（TinkerChestContainerMenu）槽容量 16，
 * 排好的 64 栈会被服务端拒绝 → 挂手。在 sortItems RETURN 把超容量栈
 * 拆到列表内的空槽（同长度修正，writeTo 1:1 写回不越界）。
 * sortItems 返回值元素是 ImmutableItemStack（flatten 按类型重建），
 * 先把原列表重建为 MutableItemStack 再拆分，保证 writeTo 的 cast 通过。
 * 工匠箱槽容量 16≤64，槽内栈不可能 >64，故 sortItems 的"超容量栈插回"
 * 在工匠箱场景恒为空，RETURN 结果即排好列表，直接修正安全。
 * 仅当排序区域从 menu 槽 0 开始且空槽足够时生效，否则放弃（交给 IPN 原逻辑）。
 * IPN 未安装时整个 mixin 静默跳过。
 */

@Mixin(value = SubTrackerActionsKt.class, remap = false)
public abstract class MixinSubTrackerActionsKt {
  @Inject(method = "sortItems(Ljava/util/List;Lorg/anti_ad/mc/ipnext/item/rule/Rule;)Ljava/util/List;",
      at = @At("RETURN"), cancellable = true)
  private static void tconlib$fixSortResult(List<ItemStack> slots, Rule rule,
      CallbackInfoReturnable<List<ItemStack>> cir) {
    AbstractContainerMenu menu = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.containerMenu
        : null;
    if (!(menu instanceof TinkerChestContainerMenu tinker))
      return;
    IdentityHashMap<MutableItemStack, Integer> map = MixinConstants.IPN_SLOT_MAP.get();
    if (map == null || slots.isEmpty())
      return;
    // 仅当排序区域从 menu 槽 0 开始（工匠箱存储区），result 索引才能对齐 tinker.getSlot(i)
    Integer start = map.get(slots.get(0));
    if (start == null || start != 0)
      return;
    List<ItemStack> original = cir.getReturnValue();
    if (original == null || original.isEmpty())
      return;
    // 重建为 MutableItemStack 列表：原列表元素是不可变栈，无法 setItemType/setCount
    List<MutableItemStack> result = new ArrayList<>(original.size());
    for (ItemStack s : original) {
      MutableItemStack m = ItemStackExtensionsKt.empty(MutableItemStack.Companion);
      m.setItemType(s.getItemType());
      m.setCount(s.getCount());
      result.add(m);
    }
    // 计算拆分后总栈数：超容量栈按目标槽容量拆成多份，碎片需要占额外槽位
    int need = 0;
    for (int i = 0; i < result.size(); i++) {
      MutableItemStack s = result.get(i);
      if (s.getCount() == 0)
        continue;
      int limit = tinker.getSlot(start + i).getMaxStackSize(ItemStackExtensionsKt.getVanillaStack(s));
      need += (s.getCount() + limit - 1) / limit;             // ceil(count / limit)
    }
    if (need > result.size())
      return;                                                 // 拆分后放不下，放弃修正
    // 紧凑重建：内容连续填充在前（拆分碎片相邻），空槽统一在末尾，保持 writeTo 1:1
    List<MutableItemStack> compact = new ArrayList<>(result.size());
    for (int i = 0; i < result.size(); i++) {
      MutableItemStack s = result.get(i);
      if (s.getCount() == 0)
        continue;
      int count = s.getCount();
      while (count > 0) {
        int limit = tinker.getSlot(start + compact.size())
            .getMaxStackSize(ItemStackExtensionsKt.getVanillaStack(s));
        MutableItemStack piece = ItemStackExtensionsKt.empty(MutableItemStack.Companion);
        piece.setItemType(s.getItemType());
        piece.setCount(Math.min(count, limit));
        compact.add(piece);
        count -= piece.getCount();
      }
    }
    while (compact.size() < result.size()) {
      compact.add(ItemStackExtensionsKt.empty(MutableItemStack.Companion));
    }
    cir.setReturnValue(new ArrayList<ItemStack>(compact));
  }

}
