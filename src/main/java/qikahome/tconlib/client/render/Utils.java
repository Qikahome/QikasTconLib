package qikahome.tconlib.client.render;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.math.Transformation;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import qikahome.tconlib.TconLib;

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
     * Parse color from a JsonElement.
     * Supported formats:
     * <ul>
     *   <li>int — direct ARGB integer</li>
     *   <li>"#RRGGBB" or "#AARRGGBB" — hex color string</li>
     *   <li>[I:r,g,b] — int array with RGB (alpha = 255)</li>
     *   <li>[I:a,r,g,b] — int array with ARGB</li>
     *   <li>{"r":int,"g":int,"b":int[,"a":int]} — JSON object with RGB/A</li>
     * </ul>
     */
    public static int parseColor(@Nullable JsonElement json) {
        if (json == null) return 0xFF000000;

        // Format 1: direct int
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return json.getAsInt();
        }

        // Format 2: hex string "#RRGGBB" / "#AARRGGBB" or named color
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String str = json.getAsString().trim();
            if (str.isEmpty()) return 0xFF000000;
            // named color?
            if (!str.startsWith("#") && !str.startsWith("0x")) {
                ChatFormatting fmt = ChatFormatting.getByName(str);
                if (fmt != null && fmt.getColor() != null) {
                    return 0xFF000000 | fmt.getColor();
                }
                TconLib.LOGGER.warn("Unknown color name: {}, falling back to white", str);
                return 0xFFFFFFFF;
            }
            // strip optional # or 0x prefix
            if (str.charAt(0) == '#') str = str.substring(1);
            else if (str.toLowerCase().startsWith("0x")) str = str.substring(2);
            if (str.length() <= 0 || str.length() > 8) return 0xFF000000;
            // if RGB (6 digits), prefix FF for alpha
            if (str.length() == 6) str = "FF" + str;
            try {
                return (int) (Long.parseLong(str, 16) & 0xFFFFFFFFL);
            } catch (NumberFormatException e) {
                TconLib.LOGGER.warn("Invalid hex color string: {}", json);
                return 0xFF000000;
            }
        }

        // Format 3 & 4: [I:r,g,b] or [I:a,r,g,b]
        if (json.isJsonArray()) {
            var arr = json.getAsJsonArray();
            int r = 0, g = 0, b = 0, a = 255;
            if (arr.size() == 3) {
                r = arr.get(0).getAsInt();
                g = arr.get(1).getAsInt();
                b = arr.get(2).getAsInt();
            } else if (arr.size() == 4) {
                a = arr.get(0).getAsInt();
                r = arr.get(1).getAsInt();
                g = arr.get(2).getAsInt();
                b = arr.get(3).getAsInt();
            }
            return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        }

        // Format 5: {"r":int,"g":int,"b":int[,"a":int]}
        if (json.isJsonObject()) {
            JsonObject obj = json.getAsJsonObject();
            int r = obj.get("r").getAsInt();
            int g = obj.get("g").getAsInt();
            int b = obj.get("b").getAsInt();
            int a = obj.has("a") ? obj.get("a").getAsInt() : 255;
            return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        }

        TconLib.LOGGER.warn("Unknown color format: {}", json);
        return 0xFF000000;
    }

    /**
     * Parse a transform object from JSON.
     * <p>
     * Supported fields (all optional):
     * <ul>
     *   <li>{@code "translation": [x, y, z]} — default [0, 0, 0]</li>
     *   <li>{@code "rotation": [x, y, z, w]} — right-rotation quaternion (alias for {@code "right_rotation"})</li>
     *   <li>{@code "left_rotation": [x, y, z, w]} — left-rotation quaternion, default identity</li>
     *   <li>{@code "right_rotation": [x, y, z, w]} — right-rotation quaternion, default identity</li>
     *   <li>{@code "scale": [x, y, z]} — default [1, 1, 1]</li>
     *   <li>{@code "origin": [x, y, z]} — transform center, default [0, 0, 0]</li>
     * </ul>
     *
     * @param ele the JSON element to parse (must be an object)
     * @return list of {@link Transformation} to apply in sequence
     */
    public static List<Transformation> parseTransforms(JsonElement ele) {
        var obj = ele.getAsJsonObject();
        Vector3f translation = new Vector3f();
        Quaternionf leftRotation = new Quaternionf();
        Quaternionf rightRotation = new Quaternionf();
        Vector3f scale = new Vector3f(1, 1, 1);
        Vector3f origin = new Vector3f();

        if (obj.has("translation"))
            translation = parseVector3f(obj.get("translation"));
        if (obj.has("left_rotation"))
            leftRotation = parseQuaternionf(obj.get("left_rotation"));
        if (obj.has("rotation"))
            rightRotation = parseQuaternionf(obj.get("rotation"));
        else if (obj.has("right_rotation"))
            rightRotation = parseQuaternionf(obj.get("right_rotation"));
        if (obj.has("scale"))
            scale = parseVector3f(obj.get("scale"));
        if (obj.has("origin"))
            origin = parseVector3f(obj.get("origin"));

        var main = new Transformation(translation, leftRotation, scale, rightRotation);

        if (origin.x() != 0 || origin.y() != 0 || origin.z() != 0) {
            var negOrigin = new Vector3f(-origin.x(), -origin.y(), -origin.z());
            var idQ = new Quaternionf();
            var idS = new Vector3f(1, 1, 1);
            return List.of(
                    new Transformation(negOrigin, idQ, idS, idQ),
                    main,
                    new Transformation(origin, idQ, idS, idQ));
        }
        return List.of(main);
    }

    private static Vector3f parseVector3f(JsonElement ele) {
        if (ele.isJsonPrimitive() && ele.getAsJsonPrimitive().isNumber()) {
            float v = ele.getAsFloat();
            return new Vector3f(v, v, v);
        }
        var arr = ele.getAsJsonArray();
        return new Vector3f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat());
    }

    private static Quaternionf parseQuaternionf(JsonElement ele) {
        // single number: angle in degrees, rotation around Y axis
        if (ele.isJsonPrimitive() && ele.getAsJsonPrimitive().isNumber()) {
            float halfAngle = (float) (Math.toRadians(ele.getAsFloat()) * 0.5);
            return new Quaternionf(0, (float) Math.sin(halfAngle), 0, (float) Math.cos(halfAngle));
        }
        var arr = ele.getAsJsonArray();
        return new Quaternionf(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat(), arr.get(3).getAsFloat());
    }

}
