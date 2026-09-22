package qikahome.tconlib.modules;

import com.google.gson.JsonElement;
import net.minecraft.network.FriendlyByteBuf;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.tconstruct.library.modifiers.ModifierId;

import java.util.List;

/**
 * 一条 modifier module 注入：目标修饰符 ID 列表 + 追加到其定义 JSON 的 modules 数组末尾的模块。
 * <p>
 * JSON 位于数据包 {@code data/<ns>/tinkering/modifier_modules/}：
 *
 * <pre>{@code
 * {
 *   "modifiers": ["tconstruct:fiery"],   // 目标修饰符 ID，可写多个
 *   "modules": [                         // 与修饰符定义 JSON 的 modules 语法完全一致
 *     { "type": "qikas_tconlib:conditional_hit_module", ... }
 *   ],
 *   "conditions": [ ... ]                // 可选，原版 Forge 条件，不满足则整个文件跳过
 * }
 * }</pre>
 * <p>
 * 模块 JSON 原样保留、不做反序列化往返，最终交给匠魂自身的 {@code ModifierModule} 加载器解析，
 * 因此语法与直接写在修饰符 JSON 里完全一致（含可选的 "hooks" 字段）。
 */
public record ModifierModuleInjection(List<ModifierId> modifiers, List<JsonElement> modules) {
  /**
   * 原样透传 JSON 的 loadable：解析时深拷贝一份，避免与注入器 JSON 的原对象共享节点。
   * 注入只在数据包加载阶段发生，不参与网络编解码。
   */
  public static final Loadable<JsonElement> RAW_JSON = new Loadable<>() {
    @Override
    public JsonElement convert(JsonElement element, String key, TypedMap context) {
      return element.deepCopy();
    }

    @Override
    public JsonElement serialize(JsonElement object) {
      return object.deepCopy();
    }

    @Override
    public JsonElement decode(FriendlyByteBuf buffer, TypedMap context) {
      throw new UnsupportedOperationException("Modifier module injection is not network-serializable");
    }

    @Override
    public void encode(FriendlyByteBuf buffer, JsonElement value) {
      throw new UnsupportedOperationException("Modifier module injection is not network-serializable");
    }
  };

  public static final RecordLoadable<ModifierModuleInjection> LOADER = RecordLoadable.create(
      ModifierId.PARSER.list(1).requiredField("modifiers", ModifierModuleInjection::modifiers),
      RAW_JSON.list(1).requiredField("modules", ModifierModuleInjection::modules),
      ModifierModuleInjection::new);
}
