package qikahome.tconlib;

import java.util.IdentityHashMap;
import net.minecraft.resources.ResourceLocation;

import org.anti_ad.mc.ipnext.item.MutableItemStack;

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

    /**
     * IPN 兼容：当前整理沙盒的"栈对象 → 槽索引"映射（IdentityHashMap 按引用比较），
     * 由 {@code MixinItemPlanner} 在 tracker 入口构建、出口清除，
     * 供 {@code MixinItemStackExtensionsKt.transferTo} O(1) 定位真实槽。
     * 泛型仅在编译期，字节码擦除后不引用 IPN 类，IPN 未安装时不影响类加载。
     */
    public static final ThreadLocal<IdentityHashMap<MutableItemStack, Integer>> IPN_SLOT_MAP = new ThreadLocal<>();
}
