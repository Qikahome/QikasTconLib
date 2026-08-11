package qikahome.tconlib.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.typed.TypedMap;

import java.util.List;

/**
 * 一条模型 override 注入：目标模型 ID + 要追加的 overrides 列表。
 * <p>
 * JSON 位于资源包 {@code assets/<ns>/model_overrides/}，格式与 LootTableInjector 类似：
 * <pre>{@code
 * {
 *   "model": "ns:path",            // 目标模型 ID（如 tconstruct:item/tools/pickaxe）
 *   "overrides": [                 // 与原版模型 JSON 的 overrides 语法一致
 *     {"predicate": {...}, "model": "..."}
 *   ]
 * }
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public record ModelOverrideInjection(ResourceLocation model, List<ItemOverride> overrides) {
  /**
   * 复用原版 {@link ItemOverride.Deserializer} 解析 override 条目，
   * 保证与原版模型 JSON 的 overrides 语法、数值解析完全一致。
   * 仅用于 JSON 加载，不参与网络编解码。
   */
  public static final Loadable<ItemOverride> OVERRIDE_LOADABLE = new Loadable<>() {
    @Override
    public ItemOverride convert(JsonElement element, String key, TypedMap context) {
      return new ItemOverride.Deserializer().deserialize(element, ItemOverride.class, null);
    }

    @Override
    public JsonElement serialize(ItemOverride object) {
      // 仅用于加载，不参与序列化
      return JsonNull.INSTANCE;
    }

    @Override
    public ItemOverride decode(FriendlyByteBuf buffer, TypedMap context) {
      throw new UnsupportedOperationException("Model override injection is not network-serializable");
    }

    @Override
    public void encode(FriendlyByteBuf buffer, ItemOverride value) {
      throw new UnsupportedOperationException("Model override injection is not network-serializable");
    }
  };

  public static final RecordLoadable<ModelOverrideInjection> LOADABLE = RecordLoadable.create(
    Loadables.RESOURCE_LOCATION.requiredField("model", ModelOverrideInjection::model),
    OVERRIDE_LOADABLE.list(1).requiredField("overrides", ModelOverrideInjection::overrides),
    ModelOverrideInjection::new);
}
