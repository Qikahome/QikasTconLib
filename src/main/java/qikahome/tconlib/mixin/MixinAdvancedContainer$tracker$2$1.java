package qikahome.tconlib.mixin;

import java.util.IdentityHashMap;
import java.util.List;
import org.anti_ad.mc.ipnext.inventory.data.MutableItemTracker;
import org.anti_ad.mc.ipnext.item.MutableItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import qikahome.tconlib.MixinConstants;

/**
 * IPN 兼容辅助：在整理 lambda 执行期间（AdvancedContainer$tracker$2$1.invoke）
 * 构建"沙盒栈对象 → 槽索引"的 IdentityHashMap，供 transferTo O(1) 定位真实槽。
 * IPN 未安装时整个 mixin 静默跳过。
 */
@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.inventory.AdvancedContainer$tracker$2$1", remap = false)
public abstract class MixinAdvancedContainer$tracker$2$1 {

  @Inject(method = "invoke", at = @At("HEAD"))
  private void tconlib$enterTracker(MutableItemTracker f, CallbackInfo cb) {
    List<?> slots = f.getSlots();
    IdentityHashMap<MutableItemStack, Integer> map = new IdentityHashMap<>(slots.size());
    for (int i = 0; i < slots.size(); i++) {
      map.put((MutableItemStack) slots.get(i), i);
    }
    MixinConstants.IPN_SLOT_MAP.set(map);
  }

  @Inject(method = "invoke", at = @At("RETURN"))
  private void tconlib$exitTracker(MutableItemTracker f, CallbackInfo cb) {
    MixinConstants.IPN_SLOT_MAP.remove();
  }
}
