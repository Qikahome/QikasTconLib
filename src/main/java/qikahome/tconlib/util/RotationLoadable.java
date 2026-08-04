package qikahome.tconlib.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import org.joml.Quaternionf;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.util.typed.TypedMap;

/**
 * A {@link Loadable} for {@link Quaternionf} representing a rotation.
 * <p>
 * Supported formats:
 * <ul>
 *   <li>Single number — angle in degrees, rotation around Y axis</li>
 *   <li>Object — {@code {"axis": "x"|"y"|"z", "angle": degrees}}</li>
 *   <li>Array of 4 floats — {@code [x, y, z, w]}</li>
 * </ul>
 */
public class RotationLoadable implements Loadable<Quaternionf> {
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
