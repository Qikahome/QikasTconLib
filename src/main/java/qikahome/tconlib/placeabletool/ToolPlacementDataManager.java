package qikahome.tconlib.placeabletool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.math.Transformation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.registries.ForgeRegistries;
import qikahome.tconlib.TconLib;
import qikahome.tconlib.util.TransformationLoadable;
import qikahome.tconlib.util.VoxelShapeLoadable;
import slimeknights.mantle.data.listener.ISafeManagerReloadListener;
import slimeknights.mantle.data.loadable.primitive.BooleanLoadable;
import slimeknights.mantle.data.loadable.primitive.EnumLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.registry.GenericLoaderRegistry.IHaveLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 管理"放置工具方块"的放置数据：每个工具（按物品注册名）可配置放置朝向约束、三套形状
 * （视觉/交互/碰撞，自动旋转生成 6 向）以及额外的渲染变换。
 * <p>
 * 数据源为 datapack：{@code data/<ns>/tinkering/placed_tool_data.json}，服务端 reload 时加载。
 */
@SuppressWarnings({ "removal", "null" })
public class ToolPlacementDataManager implements ISafeManagerReloadListener {
  @Nonnull
  public static final ToolPlacementDataManager INSTANCE = new ToolPlacementDataManager();
  private static final String CONFIG_PATH = "tinkering/placed_tool_data.json";

  private Map<ResourceLocation, PlacementData> datas = Collections.emptyMap();
  /** tag 键（JSON 里以 # 开头）的放置数据，按加载顺序（LinkedHashMap）匹配，先命中先生效 */
  private Map<TagKey<Item>, PlacementData> tagDatas = Collections.emptyMap();

  private ToolPlacementDataManager() {
  }

  /**
   * 支撑方向：{@link #FACING} = 工具贴面方向（墙在 facing 那边），{@link #OPPOSITE_FACING} = 贴面反方向
   * （如立地工具支撑在下方），其余为固定方向；support 为 null 表示不需要支撑。
   */
  public enum SupportDirection {
    FACING, OPPOSITE_FACING, UP, DOWN, NORTH, SOUTH, EAST, WEST
  }

  /** 支撑面要求：{@link #NOT_AIR} = 非空气即可；{@link #CENTER} = 面对应位置过中心点（如火把，默认）；{@link #FULL} = 面完整 */
  public enum SupportType {
    NOT_AIR, CENTER, FULL
  }

  /**
   * 单个工具的放置数据。
   *
   * @param horizontal   四向放置约束：true 时只能贴水平方向的墙，点在顶/底面时拒绝放置
   * @param support      支撑方向；null 表示不需要支撑（自由放置）
   * @param supportType  支撑面要求（默认 {@link SupportType#CENTER}）
   * @param shape        视觉形状（6 向，键 = 物品贴面方向，即 FACING 的反方向）
   * @param interaction  交互形状（可为 null，缺省跟随视觉形状）
   * @param collision    碰撞形状（可为 null，缺省跟随视觉形状）
   * @param trans        渲染时叠加的额外变换（绕物品自身坐标）
   */
  public static record PlacementData(boolean horizontal, @Nullable SupportDirection support, SupportType supportType,
      Map<Direction, VoxelShape> shape, @Nullable Map<Direction, VoxelShape> interaction,
      @Nullable Map<Direction, VoxelShape> collision, Transformation trans) implements IHaveLoader {
    /** 未配置该工具时的回退数据：自由放置（无支撑要求）、视觉/交互为展示框样贴墙薄片（16×16×1），无碰撞、无额外变换 */
    public static final PlacementData DEFAULT = new PlacementData(false, null, SupportType.CENTER, defaultShapes(),
        defaultShapes(), emptyShapes(), Transformation.identity());

    public static final RecordLoadable<PlacementData> LOADER = RecordLoadable.create(
        BooleanLoadable.DEFAULT.defaultField("horizontal", false, PlacementData::horizontal),
        new EnumLoadable<>(SupportDirection.class).nullableField("support", PlacementData::support),
        new EnumLoadable<>(SupportType.class).defaultField("support_type", SupportType.CENTER,
            PlacementData::supportType),
        VoxelShapeLoadable.RotatedLoadable.INSTANCE.defaultField("shape", defaultShapes(), PlacementData::shape),
        VoxelShapeLoadable.RotatedLoadable.INSTANCE.nullableField("interaction_shape", PlacementData::interaction),
        VoxelShapeLoadable.RotatedLoadable.INSTANCE.defaultField("collision_shape", emptyShapes(), PlacementData::collision),
        TransformationLoadable.INSTANCE.defaultField("transform", Transformation.identity(), PlacementData::trans),
        PlacementData::new);

    /** 展示框样贴墙薄片（NORTH 基准 16×16×1），自动旋转 6 向 */
    private static Map<Direction, VoxelShape> defaultShapes() {
      EnumMap<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
      for (Direction dir : Direction.values()) {
        result.put(dir, VoxelShapeLoadable.cuboidWithRotation(dir, 2, 2, 0, 14, 14, 2));
      }
      return result;
    }

    /** 6 向全空形状（无碰撞） */
    private static Map<Direction, VoxelShape> emptyShapes() {
      EnumMap<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
      for (Direction dir : Direction.values()) {
        result.put(dir, Shapes.empty());
      }
      return result;
    }

    @Override
    public RecordLoadable<? extends IHaveLoader> getLoader() {
      return LOADER;
    }

    /**
     * 生成放置后的方块状态；返回 null 表示拒绝放置。
     */
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context, ItemStack tool) {
      Direction facing = horizontal ? context.getHorizontalDirection().getOpposite()
          : context.getClickedFace();
      // 可被水冲掉的工具：放置时不保留位置的水（waterlogged 恒 false），但放置本身不失败
      boolean waterlogged = !PlacedToolBlock.canWashAway(context.getLevel(), context.getClickedPos(),
          TconLib.PLACED_TOOL.get().defaultBlockState().setValue(BlockStateProperties.FACING, facing), tool)
          && context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER);
      BlockState state = TconLib.PLACED_TOOL.get().defaultBlockState().setValue(BlockStateProperties.FACING, facing)
          .setValue(BlockStateProperties.WATERLOGGED, waterlogged);
      // 需要支撑的工具：不满足支撑要求则拒绝放置
      if (!isSupported(context.getLevel(), context.getClickedPos(), state)) {
        return null;
      }
      return state;
    }

    /** 解析支撑方向；support 为 null 时返回 null（不需要支撑） */
    @Nullable
    public Direction getSupportDirection(BlockState state) {
      if (support == null) {
        return null;
      }
      return switch (support) {
        case FACING -> state.getValue(BlockStateProperties.FACING);
        case OPPOSITE_FACING -> state.getValue(BlockStateProperties.FACING).getOpposite();
        case UP -> Direction.UP;
        case DOWN -> Direction.DOWN;
        case NORTH -> Direction.NORTH;
        case SOUTH -> Direction.SOUTH;
        case EAST -> Direction.EAST;
        case WEST -> Direction.WEST;
      };
    }

    /** 校验支撑是否满足；support 为 null 恒成立 */
    public boolean isSupported(LevelReader level, BlockPos pos, BlockState state) {
      Direction dir = getSupportDirection(state);
      if (dir == null) {
        return true;
      }
      BlockPos supportPos = pos.relative(dir);
      Direction faceDir = dir.getOpposite();
      return switch (supportType) {
        case NOT_AIR -> !level.getBlockState(supportPos).isAir();
        case CENTER -> Block.canSupportCenter(level, supportPos, faceDir);
        case FULL -> level.getBlockState(supportPos).isFaceSturdy(level, supportPos, faceDir);
      };
    }

    /** 视觉形状，键 = 物品贴面方向 */
    public VoxelShape getShape(Direction direction) {
      return shape.get(direction);
    }

    /** 交互形状；未配置时跟随视觉形状 */
    public VoxelShape getInteractionShape(Direction direction) {
      return interaction != null ? interaction.get(direction) : getShape(direction);
    }

    /** 碰撞形状；未配置时跟随视觉形状 */
    public VoxelShape getCollisionShape(Direction direction) {
      return collision != null ? collision.get(direction) : getShape(direction);
    }
  }

  /**
   * 注册重载监听器（服务端数据包 reload 时触发）。
   */
  public static void init(AddReloadListenerEvent event) {
    event.addListener(INSTANCE);
  }

  @Override
  public void onReloadSafe(ResourceManager manager) {
    Map<ResourceLocation, PlacementData> result = new LinkedHashMap<>();
    Map<TagKey<Item>, PlacementData> tagResult = new LinkedHashMap<>();

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
              String toolKey = entry.getKey();
              JsonElement value = entry.getValue();

              PlacementData data = PlacementData.LOADER.convert(value, toolKey);
              // 以 # 开头的键表示物品 tag，统一配置一类工具
              if (toolKey.startsWith("#")) {
                tagResult.put(TagKey.create(Registries.ITEM, new ResourceLocation(toolKey.substring(1))), data);
              } else {
                result.put(new ResourceLocation(toolKey), data);
              }
            }
          } catch (Exception e) {
            TconLib.LOGGER.error("Failed to read {}", location, e);
          }
        });
      } catch (Exception ignored) {
      }
    }

    this.datas = result;
    this.tagDatas = tagResult;
    TconLib.LOGGER.info("Loaded placement data for {} item keys and {} tag keys: {}", datas.size(), tagDatas.size(),
        datas.keySet());
  }

  /** 按工具注册名查放置数据；未配置返回 null */
  @Nullable
  public PlacementData getData(ResourceLocation rl) {
    return datas.get(rl);
  }

  /**
   * 按工具物品查放置数据；先精确匹配物品注册名，未命中再匹配 tag（按 JSON 加载顺序，先命中先生效）。
   * 空物品/未配置返回 null
   */
  @Nullable
  public PlacementData getData(ItemStack stack) {
    if (stack.isEmpty()) {
      return null;
    }
    ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
    if (key != null) {
      PlacementData data = datas.get(key);
      if (data != null) {
        return data;
      }
    }
    for (Map.Entry<TagKey<Item>, PlacementData> entry : tagDatas.entrySet()) {
      if (stack.is(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }

  /** 按工具物品查放置数据；未配置时回退 {@link PlacementData#DEFAULT} */
  @Nonnull
  public PlacementData get(ItemStack stack) {
    PlacementData data = getData(stack);
    return data != null ? data : PlacementData.DEFAULT;
  }
}
