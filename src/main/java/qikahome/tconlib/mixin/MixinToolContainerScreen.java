package qikahome.tconlib.mixin;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import qikahome.tconlib.Config;
import qikahome.tconlib.MixinConstants;
import qikahome.tconlib.TconLib;
import qikahome.tconlib.client.screen.AutoSizedToolContainerScreen;
import slimeknights.tconstruct.tools.ToolClientEvents;

/**
 * 用本模组的 AutoSizedGUI 屏幕替换匠魂原生的工具容器屏幕（ToolContainerScreen）。
 * <p>
 * TCon 在 {@link ToolClientEvents#clientSetupEvent} 里通过
 * {@code event.enqueueWork(() -> MenuScreens.register(toolContainer, ToolContainerScreen::new))}
 * 注册屏幕，
 * 该注册在 lambda 内异步执行。这里 @Redirect 拦截 {@code enqueueWork} 调用：
 * 先执行原 lambda（保留 TCon 的其他注册），再按 {@code enablePlacedToolAutoScreen} 配置覆盖
 * toolContainer 的屏幕注册（MenuScreens 后写覆盖先写）。配置关闭时行为与官方完全一致。
 */
@Mixin(MenuScreens.class)
public abstract class MixinToolContainerScreen {

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void tconlib$register(
            MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, U> constructor, CallbackInfo cb) {
        // 用注册名比较而非直接访问 TinkerTools.toolContainer（后者会强制加载 TCon 类，可能触发类初始化崩溃）。
        // 常量放主包的 MixinConstants（不能在 mixin 包内，也不能作为 mixin 静态字段，原因见该类注释）。
        if (MixinConstants.cancelToolContainerScreenRegister
                && MixinConstants.TOOL_CONTAINER_KEY.equals(ForgeRegistries.MENU_TYPES.getKey(type))) {
            cb.cancel();
        }
    }
}
