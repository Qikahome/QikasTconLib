package qikahome.tconlib;

import net.minecraft.resources.ResourceLocation;

/**
 * Mixin 相关共享常量。
 * <p>
 * 注意：
 * <ul>
 *   <li>必须放在 mixin 包之外——mixins.json 声明的 mixin 包内类被目标类直接引用会抛
 *       {@code IllegalClassLoadError}；</li>
 *   <li>不要声明为 Mixin 类的静态字段——mixin 注入的静态字段赋值追加在目标类 {@code <clinit>} 末尾，
 *       若其他模组首次触发目标类初始化时恰好调用被注入的方法，字段还是 null 会直接 NPE。</li>
 * </ul>
 */
public final class MixinConstants {
    private MixinConstants() {
    }

    /** TCon 工具容器菜单类型的注册名（tconstruct:tool_container） */
    public static final ResourceLocation TOOL_CONTAINER_KEY = new ResourceLocation("tconstruct", "tool_container");

    /**
     * 屏幕注册拦截开关：为 true 时 {@code MixinToolContainerScreen} 会 cancel TCon 原生
     * toolContainer 的屏幕注册；本模组/TCon 在 clientSetup 里注册自己的屏幕时临时置 false
     * （MenuScreens 后写覆盖先写），随后恢复 true。
     */
    public static boolean cancelToolContainerScreenRegister = true;
}
