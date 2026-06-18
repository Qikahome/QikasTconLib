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
import com.mojang.math.Transformation;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.DyeColor;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import qikahome.tconlib.TconLib;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.common.ColorLoadable;
import slimeknights.mantle.data.loadable.common.Vector3fLoadable;
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
     * A {@link Loadable} for {@link Transformation} with extended JSON format support.
     * <p>
     * Supported fields (all optional):
     */
    public static class TransformationLoadable implements Loadable<Transformation> {
        public static final TransformationLoadable INSTANCE = new TransformationLoadable();

        private TransformationLoadable() {}

        @Override
        public Transformation convert(JsonElement element, String key, TypedMap context) {
            var obj = element.getAsJsonObject();
            Vector3f translation = new Vector3f();
            Quaternionf leftRotation = new Quaternionf();
            Quaternionf rightRotation = new Quaternionf();
            Vector3f scale = new Vector3f(1, 1, 1);
            Vector3f origin = new Vector3f();

            if (obj.has("translation"))
                translation = Vector3fLoadable.INSTANCE.convert(obj.get("translation"), key + ".translation", context);
            if (obj.has("left_rotation"))
                leftRotation = RotationLoadable.INSTANCE.convert(obj.get("left_rotation"), key + ".left_rotation", context);
            if (obj.has("rotation"))
                rightRotation = RotationLoadable.INSTANCE.convert(obj.get("rotation"), key + ".rotation", context);
            else if (obj.has("right_rotation"))
                rightRotation = RotationLoadable.INSTANCE.convert(obj.get("right_rotation"), key + ".right_rotation", context);
            if (obj.has("scale"))
                scale = Vector3fLoadable.INSTANCE.convert(obj.get("scale"), key + ".scale", context);
            if (obj.has("origin"))
                origin = Vector3fLoadable.INSTANCE.convert(obj.get("origin"), key + ".origin", context);

            var identity = new Quaternionf();
            var identityScale = new Vector3f(1, 1, 1);

            var main = new Transformation(translation, leftRotation, scale, rightRotation);

            if (origin.x() != 0 || origin.y() != 0 || origin.z() != 0) {
                var negOrigin = new Vector3f(-origin.x(), -origin.y(), -origin.z());
                var posOrigin = new Transformation(origin, identity, identityScale, identity);
                var negOriginT = new Transformation(negOrigin, identity, identityScale, identity);
                return negOriginT.compose(main).compose(posOrigin);
            }
            return main;
        }

        @Override
        public JsonElement serialize(Transformation object) {
            var matrix = object.getMatrix();
            var arr = new JsonArray();
            float[] values = new float[16];
            matrix.get(values);
            for (float v : values) {
                arr.add(v);
            }
            var obj = new JsonObject();
            obj.add("matrix", arr);
            return obj;
        }

        @Override
        public Transformation decode(FriendlyByteBuf buffer, TypedMap context) {
            float[] values = new float[16];
            for (int i = 0; i < 16; i++) {
                values[i] = buffer.readFloat();
            }
            return new Transformation(new Matrix4f().set(values));
        }

        @Override
        public void encode(FriendlyByteBuf buffer, Transformation object) {
            float[] values = new float[16];
            object.getMatrix().get(values);
            for (float v : values) {
                buffer.writeFloat(v);
            }
        }
    }

    /**
     * A {@link Loadable} for {@link Quaternionf} representing a rotation.
     * <p>
     * Supported formats:
     * <ul>
     *   <li>Single number — angle in degrees, rotation around Y axis</li>
     *   <li>Array of 4 floats — {@code [x, y, z, w]}</li>
     * </ul>
     */
    public static class RotationLoadable implements Loadable<Quaternionf> {
        public static final RotationLoadable INSTANCE = new RotationLoadable();

        private RotationLoadable() {}

        @Override
        public Quaternionf convert(JsonElement element, String key, TypedMap context) {
            // Single number: angle in degrees, rotation around Y axis
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                float halfAngle = (float) (Math.toRadians(element.getAsFloat()) * 0.5);
                return new Quaternionf(0, (float) Math.sin(halfAngle), 0, (float) Math.cos(halfAngle));
            }
            // Object: {"axis": "x"|"y"|"z", "angle": degrees}
            if (element.isJsonObject()) {
                var obj = element.getAsJsonObject();
                float halfAngle = (float) (Math.toRadians(GsonHelper.getAsDouble(obj, "angle")) * 0.5);
                float sin = (float) Math.sin(halfAngle);
                float cos = (float) Math.cos(halfAngle);
                return switch (GsonHelper.getAsString(obj, "axis")) {
                    case "x" -> new Quaternionf(sin, 0, 0, cos);
                    case "y" -> new Quaternionf(0, sin, 0, cos);
                    case "z" -> new Quaternionf(0, 0, sin, cos);
                    default -> throw new JsonSyntaxException("Invalid rotation axis at " + key);
                };
            }
            // Array of 4 floats: [x, y, z, w]
            var arr = element.getAsJsonArray();
            return new Quaternionf(
                arr.get(0).getAsFloat(),
                arr.get(1).getAsFloat(),
                arr.get(2).getAsFloat(),
                arr.get(3).getAsFloat()
            );
        }

        @Override
        public JsonElement serialize(Quaternionf object) {
            var arr = new JsonArray();
            arr.add(object.x());
            arr.add(object.y());
            arr.add(object.z());
            arr.add(object.w());
            return arr;
        }

        @Override
        public Quaternionf decode(FriendlyByteBuf buffer, TypedMap context) {
            return new Quaternionf(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, Quaternionf object) {
            buffer.writeFloat(object.x());
            buffer.writeFloat(object.y());
            buffer.writeFloat(object.z());
            buffer.writeFloat(object.w());
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
