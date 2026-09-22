package qikahome.tconlib.mixin;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qikahome.tconlib.modules.ModifierModuleInjector;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;

import java.util.Map;

/**
 * 在匠魂解析修饰符 JSON 之前，把注入的 modifier module 追加进它的 modules 数组，
 * 使得加载出的 ComposableModifier 天然包含这些模块——无需重建修饰符、也无需访问私有字段。
 * <p>
 * {@code ModifierManager#loadModifier} 是数据包加载与客户端数据包重载的唯一入口
 * （网络同步走 {@code ComposableModifier.LOADER.decode}，不经此处），因此注入只需挂在这一处，
 * 之后 {@code UpdateModifiersPacket} 会把注入后的模块一并同步给客户端。
 */
@Mixin(value = ModifierManager.class, remap = false)
public class MixinModifierManager {

    @Inject(method = "loadModifier", at = @At("HEAD"), remap = false)
    private void tconlib$injectModules(ResourceLocation key, JsonElement element,
            Map<ModifierId, ModifierId> redirects, CallbackInfoReturnable<Modifier> cir) {
        ModifierModuleInjector.inject(key, element);
    }
}
