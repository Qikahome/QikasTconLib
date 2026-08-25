package qikahome.tconlib.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.common.Vector3fLoadable;
import slimeknights.mantle.util.typed.TypedMap;

/**
 * A {@link Loadable} for {@link Transformation} with extended JSON format support.
 * <p>
 * Supported fields (all optional):
 * <ul>
 *   <li>{@code translation} — {@code [x, y, z]}</li>
 *   <li>{@code rotation} / {@code right_rotation} — see {@link RotationLoadable}</li>
 *   <li>{@code left_rotation} — see {@link RotationLoadable}</li>
 *   <li>{@code scale} — {@code [x, y, z]}</li>
 *   <li>{@code origin} — {@code [x, y, z]}, rotates around this pivot point</li>
 * </ul>
 */
public class TransformationLoadable implements Loadable<Transformation> {
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
        // 与 convert 对称：输出结构化字段（translation/rotation/scale），避免 matrix 无法读回
        var obj = new JsonObject();
        obj.add("translation", Vector3fLoadable.INSTANCE.serialize(object.getTranslation()));
        obj.add("scale", Vector3fLoadable.INSTANCE.serialize(object.getScale()));
        Quaternionf left = object.getLeftRotation();
        if (left != null) {
            obj.add("left_rotation", RotationLoadable.INSTANCE.serialize(left));
        }
        Quaternionf right = object.getRightRotation();
        if (right != null) {
            obj.add("right_rotation", RotationLoadable.INSTANCE.serialize(right));
        }
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
