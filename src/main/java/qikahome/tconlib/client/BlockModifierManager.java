package qikahome.tconlib.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import qikahome.tconlib.TconLib;
import slimeknights.mantle.data.listener.IEarlySafeManagerReloadListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.annotation.Nonnull;

/**
 * 管理三维工具模型的修饰符模型配置。
 * 使用 IEarlySafeManagerReloadListener 确保在模型烘焙之前完成加载。
 * 数据结构: Map&lt;modifier_key, JsonObject&gt; 键为修饰符 ID，值为模型引用（字符串或对象）
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings({"removal","null"})
public class BlockModifierManager implements IEarlySafeManagerReloadListener {
    @Nonnull
    public static final BlockModifierManager INSTANCE = new BlockModifierManager();
    private static final String CONFIG_PATH = "tinkering/block_modifiers.json";

    private Map<String, JsonObject> modifierModels = Collections.emptyMap();

    private BlockModifierManager() {}

    /**
     * 注册重载监听器。
     */
    public static void init(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(INSTANCE);
    }

    @Override
    public void onReloadSafe(ResourceManager manager) {
        Map<String, JsonObject> result = new HashMap<>();

        for (String namespace : manager.getNamespaces()) {
            ResourceLocation location = new ResourceLocation(namespace, CONFIG_PATH);
            try {
                manager.getResource(location).ifPresent(resource -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                        JsonElement root = JsonParser.parseReader(reader);
                        if (!root.isJsonObject()) {
                            TconLib.LOGGER.warn("Invalid {} format, expected JSON object", location);
                            return;
                        }

                        JsonObject rootObj = root.getAsJsonObject();
                        for (Map.Entry<String, JsonElement> entry : rootObj.entrySet()) {
                            String modifierKey = entry.getKey();
                            JsonElement value = entry.getValue();

                            if (!value.isJsonObject()) {
                                TconLib.LOGGER.warn("Invalid format for modifier_key '{}' in {}, expected object", modifierKey, location);
                                continue;
                            }

                            result.merge(modifierKey, value.getAsJsonObject(), (oldObj, newObj) -> {
                                JsonObject merged = new JsonObject();
                                for (Map.Entry<String, JsonElement> oldEntry : oldObj.entrySet()) {
                                    merged.add(oldEntry.getKey(), oldEntry.getValue());
                                }
                                for (Map.Entry<String, JsonElement> newEntry : newObj.entrySet()) {
                                    merged.add(newEntry.getKey(), newEntry.getValue());
                                }
                                return merged;
                            });
                        }
                    } catch (Exception e) {
                        TconLib.LOGGER.error("Failed to read {}", location, e);
                    }
                });
            } catch (Exception ignored) {
            }
        }

        this.modifierModels = result;
        TconLib.LOGGER.info("Loaded 3D modifier models for {} tool types: {}", modifierModels.size(), modifierModels.keySet());
    }

    /**
     * 获取指定 modifierKey 对应的修饰符模型 JSON 对象。
     */
    public JsonObject getModifierModels(String modifierKey) {
        JsonObject obj = modifierModels.get(modifierKey);
        return obj != null ? obj : new JsonObject();
    }
}
