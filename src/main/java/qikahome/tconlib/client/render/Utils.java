package qikahome.tconlib.client.render;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.DyeColor;

import qikahome.tconlib.TconLib;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.common.ColorLoadable;
import slimeknights.mantle.util.typed.TypedMap;

@SuppressWarnings({"removal","null"})
public class Utils {
    @Nullable
    public static BlockModel parseModel(ResourceLocation rl, String prefix, JsonDeserializationContext context) {
        // 转为资源路径: models/ + path + .json
        String resourcePath = prefix + rl.getPath() + ".json";
        ResourceLocation fileLocation = new ResourceLocation(rl.getNamespace(), resourcePath);
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(fileLocation);
            if (resource.isPresent()) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8));
                JsonElement modelJson = JsonParser.parseReader(reader);
                return context.deserialize(modelJson, BlockModel.class);
            } else {
                TconLib.LOGGER.error("Model not found: {}", fileLocation);
                return null;
            }
        } catch (Exception e) {
            TconLib.LOGGER.error("Failed to load model: {}", fileLocation, e);
            return null;
        }
    }

    /**
     * Extended color loadable that supports the same formats as {@link #parseColor(JsonElement)}.
     * Falls back to {@link ColorLoadable} for standard hex string format.
     */
    public static class ExtendColorLoadable implements Loadable<Integer> {

        public static final ExtendColorLoadable ALPHA = new ExtendColorLoadable(true);
        public static final ExtendColorLoadable NO_ALPHA = new ExtendColorLoadable(false);

        private final boolean supportsAlpha;

        private ExtendColorLoadable(boolean supportsAlpha) {
            this.supportsAlpha = supportsAlpha;
        }

        @Override
        public Integer convert(JsonElement element, String key, TypedMap context) {
            // Format 1: direct int
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsInt();
            }

            // Format 2: string - hex string or named color
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String str = element.getAsString();
                if (str.isEmpty()) return -1;

                // Try hex format via ColorLoadable
                if (str.charAt(0) != '#') {
                    try {
                        if (supportsAlpha) {
                            return ColorLoadable.ALPHA.parseString(str, key, context);
                        } else {
                            return ColorLoadable.NO_ALPHA.parseString(str, key, context);
                        }
                    } catch (JsonSyntaxException e) {
                        // Not a hex string, try named color below
                    }
                } else {
                    // "#RRGGBB" or "#AARRGGBB"
                    String hex = str.substring(1);
                    try {
                        int len = hex.length();
                        if (len == 8) {
                            return (int) Long.parseLong(hex, 16);
                        }
                        if (len == 6) {
                            return 0xFF000000 | Integer.parseInt(hex, 16);
                        }
                    } catch (NumberFormatException ignored) {}
                }

                // Try named color from ChatFormatting
                ChatFormatting formatting = ChatFormatting.getByName(str);
                if (formatting != null && formatting.isColor()) {
                    Integer color = formatting.getColor();
                    if (color != null) {
                        return 0xFF000000 | color;
                    }
                }

                // Try DyeColor
                for (DyeColor dye : DyeColor.values()) {
                    if (dye.getName().equals(str)) {
                        return 0xFF000000 | dye.getTextColor();
                    }
                }

                throw new JsonSyntaxException("Invalid color '" + str + "' at " + key);
            }

            // Format 3: array [r, g, b] or [a, r, g, b]
            if (element.isJsonArray()) {
                JsonArray arr = element.getAsJsonArray();
                if (arr.size() == 3) {
                    return 0xFF000000
                            | (clamp(arr.get(0).getAsInt(), 0, 255) << 16)
                            | (clamp(arr.get(1).getAsInt(), 0, 255) << 8)
                            | clamp(arr.get(2).getAsInt(), 0, 255);
                }
                if (arr.size() == 4) {
                    return (clamp(arr.get(0).getAsInt(), 0, 255) << 24)
                            | (clamp(arr.get(1).getAsInt(), 0, 255) << 16)
                            | (clamp(arr.get(2).getAsInt(), 0, 255) << 8)
                            | clamp(arr.get(3).getAsInt(), 0, 255);
                }
                throw new JsonSyntaxException("Invalid color array, expected 3 or 4 elements at " + key);
            }

            // Format 4: object {"r":..., "g":..., "b":..., "a":...}
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                int r = GsonHelper.getAsInt(obj, "r", 0);
                int g = GsonHelper.getAsInt(obj, "g", 0);
                int b = GsonHelper.getAsInt(obj, "b", 0);
                int a = GsonHelper.getAsInt(obj, "a", 255);
                return (clamp(a, 0, 255) << 24)
                        | (clamp(r, 0, 255) << 16)
                        | (clamp(g, 0, 255) << 8)
                        | clamp(b, 0, 255);
            }

            throw new JsonSyntaxException("Invalid color format at " + key);
        }

        @Override
        public JsonElement serialize(Integer color) {
            return new JsonPrimitive(String.format("%08X", color));
        }

        @Override
        public Integer decode(FriendlyByteBuf buffer, TypedMap context) {
            return buffer.readInt();
        }

        @Override
        public void encode(FriendlyByteBuf buffer, Integer color) {
            buffer.writeInt(color);
        }

        private static int clamp(int value, int min, int max) {
            return Math.min(max, Math.max(min, value));
        }
    }

}
