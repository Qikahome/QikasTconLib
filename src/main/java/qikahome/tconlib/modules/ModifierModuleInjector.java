package qikahome.tconlib.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition.IContext;
import net.minecraftforge.event.AddReloadListenerEvent;
import qikahome.tconlib.TconLib;
import slimeknights.mantle.data.listener.IEarlyReloadListener;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.library.modifiers.ModifierId;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 修饰符模块注入器（服务端 + 客户端数据包）。
 * <p>
 * 通过数据包 JSON（{@code data/<ns>/tinkering/modifier_modules/}）给已有的 ComposableModifier
 * 追加额外的 modifier module，而无需覆盖该修饰符自己的定义 JSON。
 * <p>
 * 加载时机是资源重载的 prepare 阶段（{@link IEarlyReloadListener}），早于任何 listener 的 apply，
 * 因此数据一定在 {@code ModifierManager} 解析修饰符 JSON 之前就绪；实际追加由
 * {@code MixinModifierManager} 在 {@code ModifierManager#loadModifier} 的 HEAD 处完成。
 * <p>
 * 匠魂加载完修饰符后会用 {@code UpdateModifiersPacket} 把模块序列化同步给客户端，
 * 所以只在服务端注入即可，客户端会自动收到注入后的定义，无需额外处理。
 */
public enum ModifierModuleInjector implements IEarlyReloadListener {
  INSTANCE;

  /** 数据包文件夹：data/&lt;ns&gt;/tinkering/modifier_modules/ */
  public static final String FOLDER = "tinkering/modifier_modules";

  /** 条件上下文，用于注入 JSON 的 conditions 字段 */
  private IContext context = IContext.EMPTY;
  /** 目标修饰符 → 要追加的模块 JSON（保持原样，交给匠魂自己的加载器解析） */
  private volatile Map<ModifierId, List<JsonElement>> injections = Collections.emptyMap();

  /** 注册数据包重载监听器 */
  public static void init(AddReloadListenerEvent event) {
    event.addListener(INSTANCE);
    INSTANCE.context = event.getConditionContext();
  }

  @Override
  public void onResourceManagerReload(ResourceManager manager) {
    long time = System.nanoTime();
    Map<ModifierId, List<JsonElement>> result = new HashMap<>();
    int loaded = 0;
    for (Entry<ResourceLocation, Resource> entry : manager
        .listResources(FOLDER, loc -> loc.getPath().endsWith(".json")).entrySet()) {
      try (Reader reader = entry.getValue().openAsReader()) {
        JsonObject json = GsonHelper.fromJson(JsonHelper.DEFAULT_GSON, reader, JsonObject.class);
        // 空文件视为删除，方便数据包禁用；条件不满足则跳过
        if (json == null || json.keySet().isEmpty()
            || !CraftingHelper.processConditions(json, "conditions", context)) {
          continue;
        }
        ModifierModuleInjection injection = ModifierModuleInjection.LOADER.deserialize(json);
        // 多个文件可注入同一修饰符，按加载顺序合并
        for (ModifierId modifier : injection.modifiers()) {
          result.computeIfAbsent(modifier, m -> new ArrayList<>()).addAll(injection.modules());
        }
        loaded++;
      } catch (IllegalArgumentException | IOException | JsonParseException ex) {
        TconLib.LOGGER.error("Couldn't parse modifier module injection from {}", entry.getKey(), ex);
      }
    }
    injections = result;
    TconLib.LOGGER.info("Loaded {} modifier module injectors targeting {} modifiers in {} ms",
        loaded, injections.size(), (System.nanoTime() - time) / 1000000f);
  }

  /**
   * 把注入的模块追加到修饰符定义 JSON 的 modules 数组末尾；没有对应注入时不改动。
   * <p>
   * 由 {@code MixinModifierManager} 在解析前调用，参数 {@code key} 即修饰符 ID。追加在末尾意味着
   * 优先级低于原模块（{@code ModuleHookMap} 按列表顺序合并 hook）。
   */
  public static void inject(ResourceLocation key, JsonElement element) {
    List<JsonElement> modules = INSTANCE.injections.get(new ModifierId(key));
    if (modules == null || !element.isJsonObject()) {
      return;
    }
    JsonObject json = element.getAsJsonObject();
    JsonArray array = json.has("modules") ? GsonHelper.getAsJsonArray(json, "modules") : new JsonArray();
    for (JsonElement module : modules) {
      // 深拷贝：同一注入可能命中多个修饰符，避免共享同一 JSON 节点
      array.add(module.deepCopy());
    }
    json.add("modules", array);
  }
}
