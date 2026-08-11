package qikahome.tconlib.util;

import java.util.EnumMap;
import java.util.Map;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.util.typed.TypedMap;

/**
 * 一个用于 {@link VoxelShape} 的 {@link Loadable}，JSON 格式参考 JsonThings 的 shape 定义：
 * <ul>
 *   <li>单个 box 数组：{@code [x1, y1, z1, x2, y2, z2]}（像素 0~16，除以 16 转格）</li>
 *   <li>单个 box 对象：{@code {"x1":..,"y1":..,"z1":..,"x2":..,"y2":..,"z2":..}}</li>
 *   <li>组合数组：{@code [shape1, shape2, ...]}（OR 并集）</li>
 *   <li>组合对象：{@code {"op":"or","shapes":[shape1, shape2]}}，op 支持 16 种 {@link BooleanOp}</li>
 * </ul>
 * <p>
 * 序列化输出为 AABB 组合数组；网络传输使用原版 {@link VoxelShapeSerialization}。
 */
public class VoxelShapeLoadable implements Loadable<VoxelShape> {
    public static final VoxelShapeLoadable INSTANCE = new VoxelShapeLoadable();

    private static final BiMap<String, BooleanOp> BOOLEAN_OPERATORS = ImmutableBiMap.<String, BooleanOp>builder()
            .put("false", BooleanOp.FALSE)
            .put("not_or", BooleanOp.NOT_OR)
            .put("only_second", BooleanOp.ONLY_SECOND)
            .put("not_first", BooleanOp.NOT_FIRST)
            .put("only_first", BooleanOp.ONLY_FIRST)
            .put("not_second", BooleanOp.NOT_SECOND)
            .put("not_same", BooleanOp.NOT_SAME)
            .put("not_and", BooleanOp.NOT_AND)
            .put("and", BooleanOp.AND)
            .put("same", BooleanOp.SAME)
            .put("second", BooleanOp.SECOND)
            .put("causes", BooleanOp.CAUSES)
            .put("first", BooleanOp.FIRST)
            .put("caused_by", BooleanOp.CAUSED_BY)
            .put("or", BooleanOp.OR)
            .put("true", BooleanOp.TRUE)
            .build();

    private VoxelShapeLoadable() {}

    @Override
    public VoxelShape convert(JsonElement element, String key, TypedMap context) {
        // 无旋转需求时以北为基准（cuboidWithRotation(NORTH) 恒等）
        return convertDirection(element, key, context, Direction.NORTH);
    }

    /**
     * 把 JSON 中的形状按 6 个方向各生成一份（键 = 物品贴面方向）。
     * <p>
     * 旋转在 box 参数层面完成（见 {@link #cuboidWithRotation}），组合形状则同方向各自旋转后合并。
     */
    public EnumMap<Direction, VoxelShape> convertRotated(JsonElement element, String key, TypedMap context) {
        EnumMap<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            result.put(dir, convertDirection(element, key, context, dir));
        }
        return result;
    }

    private VoxelShape convertDirection(JsonElement element, String key, TypedMap context, Direction facing) {
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            // 6 个数字 = 单个 box
            if (arr.size() == 6 && arr.get(0).isJsonPrimitive() && arr.get(0).getAsJsonPrimitive().isNumber()) {
                return cuboidWithRotation(facing, arr.get(0).getAsDouble(), arr.get(1).getAsDouble(),
                        arr.get(2).getAsDouble(), arr.get(3).getAsDouble(), arr.get(4).getAsDouble(),
                        arr.get(5).getAsDouble());
            }
            // 元素为 shape = OR 组合
            return combine(key, context, arr, BooleanOp.OR, facing);
        }
        JsonObject obj = element.getAsJsonObject();
        // 组合对象：{"op":..,"shapes":[..]}
        if (obj.has("op")) {
            String opName = GsonHelper.getAsString(obj, "op");
            BooleanOp op = BOOLEAN_OPERATORS.get(opName);
            if (op == null) {
                throw new JsonSyntaxException("Unknown boolean operator '" + opName + "' at " + key);
            }
            return combine(key, context, GsonHelper.getAsJsonArray(obj, "shapes"), op, facing);
        }
        // 单个 box 对象
        return cuboidWithRotation(facing, GsonHelper.getAsDouble(obj, "x1"), GsonHelper.getAsDouble(obj, "y1"),
                GsonHelper.getAsDouble(obj, "z1"), GsonHelper.getAsDouble(obj, "x2"),
                GsonHelper.getAsDouble(obj, "y2"), GsonHelper.getAsDouble(obj, "z2"));
    }

    private VoxelShape combine(String key, TypedMap context, JsonArray shapes, BooleanOp op, Direction facing) {
        VoxelShape result = Shapes.empty();
        for (JsonElement element : shapes) {
            VoxelShape part = convertDirection(element, key, context, facing);
            result = result.isEmpty() ? part : Shapes.joinUnoptimized(result, part, op);
        }
        return result.optimize();
    }

    /** 按方向旋转一个像素 box（参照 JsonThings DynamicShape.cuboidWithRotation，输入单位为像素）。
     * 语义：把"贴北墙（z=0 面）的形状"转到"贴 dir 方向的墙"。
     * 注意：JsonThings 原版的 UP/DOWN 分支是绕 z 轴旋转（且坐标顺序写反），真正的贴墙旋转应绕 x 轴（把 z 轴转到 y 轴），这里已修正。 */
    @SuppressWarnings("SuspiciousNameCombination")
    public static VoxelShape cuboidWithRotation(Direction facing, double x1, double y1, double z1, double x2,
            double y2, double z2) {
        x1 /= 16.0;
        y1 /= 16.0;
        z1 /= 16.0;
        x2 /= 16.0;
        y2 /= 16.0;
        z2 /= 16.0;
        return switch (facing) {
            case SOUTH -> Shapes.box(1 - x2, y1, 1 - z2, 1 - x1, y2, 1 - z1);
            case WEST -> Shapes.box(z1, y1, 1 - x2, z2, y2, 1 - x1);
            case EAST -> Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2);
            case UP -> Shapes.box(x1, 1 - z2, y1, x2, 1 - z1, y2);
            case DOWN -> Shapes.box(x1, z1, 1 - y2, x2, z2, 1 - y1);
            default -> Shapes.box(x1, y1, z1, x2, y2, z2);
        };
    }

    @Override
    public JsonElement serialize(VoxelShape object) {
        JsonArray arr = new JsonArray();
        for (AABB aabb : object.toAabbs()) {
            JsonArray box = new JsonArray();
            box.add(aabb.minX * 16);
            box.add(aabb.minY * 16);
            box.add(aabb.minZ * 16);
            box.add(aabb.maxX * 16);
            box.add(aabb.maxY * 16);
            box.add(aabb.maxZ * 16);
            arr.add(box);
        }
        return arr;
    }

    @Override
    public VoxelShape decode(FriendlyByteBuf buffer, TypedMap context) {
        int count = buffer.readVarInt();
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < count; i++) {
            AABB box = new AABB(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            shape = Shapes.joinUnoptimized(shape, Shapes.create(box), BooleanOp.OR);
        }
        return shape.optimize();
    }

    @Override
    public void encode(FriendlyByteBuf buffer, VoxelShape object) {
        java.util.List<AABB> boxes = object.toAabbs();
        buffer.writeVarInt(boxes.size());
        for (AABB box : boxes) {
            buffer.writeDouble(box.minX);
            buffer.writeDouble(box.minY);
            buffer.writeDouble(box.minZ);
            buffer.writeDouble(box.maxX);
            buffer.writeDouble(box.maxY);
            buffer.writeDouble(box.maxZ);
        }
    }

    /**
     * 把一个 JSON 形状解析成 6 向形状表（自动旋转）的 {@link Loadable}。
     * <p>
     * 与 {@link VoxelShapeLoadable#convertRotated} 对应，供 record 字段直接使用。
     * <p>
     * 支持两种写法：
     * <ul>
     *   <li>普通形状：同 {@link VoxelShapeLoadable}，按"贴北墙"基准自动旋转到 6 个方向（dir = 物品贴面方向，
     *       up = 贴天花板、down = 贴地板）；</li>
     *   <li>per-direction：JSON 对象 key 为方向名（north/south/east/west/up/down）时，每个方向独立指定
     *       世界坐标形状（像素，不旋转），必须写全 6 个方向。示例：
     *       {@code {"up": [4,6,4,12,16,12], "down": [4,0,4,12,10,12], ...}}</li>
     * </ul>
     */
    public static class RotatedLoadable implements Loadable<Map<Direction, VoxelShape>> {
        public static final RotatedLoadable INSTANCE = new RotatedLoadable();

        private RotatedLoadable() {}

        @Override
        public Map<Direction, VoxelShape> convert(JsonElement element, String key, TypedMap context) {
            // per-direction：JSON 对象 key 为方向名（north/south/east/west/up/down）时，
            // 每个方向独立指定形状（世界坐标像素，不旋转）；否则沿用单一形状自动旋转 6 向
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                boolean perDirection = false;
                for (String name : obj.keySet()) {
                    if (Direction.byName(name) != null) {
                        perDirection = true;
                        break;
                    }
                }
                if (perDirection) {
                    if (obj.size() != 6) {
                        throw new JsonSyntaxException(
                                "Per-direction shape at " + key + " must define all 6 directions");
                    }
                    EnumMap<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        Direction dir = Direction.byName(entry.getKey());
                        if (dir == null) {
                            throw new JsonSyntaxException("Unknown key '" + entry.getKey() + "' at " + key
                                    + " (expected a direction name)");
                        }
                        // NORTH 恒等：按世界坐标直写，不做旋转
                        result.put(dir, VoxelShapeLoadable.INSTANCE.convertDirection(entry.getValue(),
                                key + "." + entry.getKey(), context, Direction.NORTH));
                    }
                    return result;
                }
            }
            return VoxelShapeLoadable.INSTANCE.convertRotated(element, key, context);
        }

        @Override
        public JsonElement serialize(Map<Direction, VoxelShape> object) {
            // 序列化以北方为基准（不保留方向信息，够数据层使用）
            return VoxelShapeLoadable.INSTANCE.serialize(object.get(Direction.NORTH));
        }

        @Override
        public Map<Direction, VoxelShape> decode(FriendlyByteBuf buffer, TypedMap context) {
            int count = buffer.readVarInt();
            Map<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
            for (int i = 0; i < count; i++) {
                Direction dir = Direction.from3DDataValue(buffer.readByte());
                result.put(dir, VoxelShapeLoadable.INSTANCE.decode(buffer, context));
            }
            return result;
        }

        @Override
        public void encode(FriendlyByteBuf buffer, Map<Direction, VoxelShape> object) {
            buffer.writeVarInt(object.size());
            for (Map.Entry<Direction, VoxelShape> entry : object.entrySet()) {
                buffer.writeByte(entry.getKey().get3DDataValue());
                VoxelShapeLoadable.INSTANCE.encode(buffer, entry.getValue());
            }
        }
    }
}
