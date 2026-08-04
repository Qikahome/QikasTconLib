package qikahome.tconlib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import qikahome.tconlib.TconLib;
import qikahome.tconlib.placeabletool.PlacedToolBlock;
import qikahome.tconlib.placeabletool.PlacedToolBlock.PlacedToolBlockEntity;

/**
 * 放置工具方块的渲染器。
 * <p>
 * 与物品展示框（ItemFrameRenderer）几乎一致的渲染方式：
 * 工具按 FACING 贴墙平放，尺寸走物品模型自身的 FIXED 变换。
 */
public class PlacedToolBlockEntityRenderer implements BlockEntityRenderer<PlacedToolBlockEntity> {

    /** 放置形态标记（NBT 键）：注册的 qikas_tconlib:placed 属性返回放置分类（1=墙面 2=天花板 3=地板），未放置为 -1 */
    public static final String PLACED_MODEL_TAG = TconLib.getResource("placed").toString();

    /** 展示框内物品的缩放（展示框原版为 0.5，需要更大可调这里） */
    private static final float ITEM_SCALE = 0.5F;

    public PlacedToolBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PlacedToolBlockEntity be, float partialTicks, PoseStack matrices, MultiBufferSource buffer,
            int light, int overlay) {
        ItemStack stack = be.getStack();
        if (stack.isEmpty()) {
            return;
        }
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(PlacedToolBlock.FACING);

        matrices.pushPose();
        matrices.translate(0.5D, 0.5D, 0.5D);
        matrices.mulPose(facing.getRotation());
        matrices.mulPose(Axis.XN.rotationDegrees(90));
        matrices.mulPose(Axis.YP.rotationDegrees(180));
        matrices.translate(0, 0, 0.4375D); // 与展示框 ItemFrameRenderer 的物品深度一致

        matrices.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        // 工具要求的额外渲染变换（绕物品自身坐标，最后应用）
        // 走 BE 的放置数据：联机客户端本地查不到 datapack，靠服务端更新包同步（transient）
        Transformation renderTransform = be.getPlacementData().trans();
        if (!renderTransform.isIdentity()) {
            matrices.mulPoseMatrix(renderTransform.getMatrix());
        }
        // 放置形态模型切换：复制一份并写入放置分类（1=墙面 2=天花板 3=地板），资源包可在工具模型 JSON 的 overrides 里
        // 按 qikas_tconlib:placed=<值> 用不同模型
        // 仅作用于这次渲染的副本：不打到 BE 存储/服务器，掉落物、容器界面都不受影响（同护甲 armor_model 的做法）
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(PLACED_MODEL_TAG)) {
            stack = stack.copy();
            int value = switch (PlacedToolBlock.getItemFacing(state)) {
                case UP -> 3;    // 物品朝上 → 平放在方块顶面/地板
                case DOWN -> 2;  // 物品朝下 → 倒挂贴天花板
                default -> 1;    // 水平四向 → 贴墙
            };
            stack.getOrCreateTag().putInt(PLACED_MODEL_TAG, value);
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, matrices, buffer, be.getLevel(), 0);
        matrices.popPose();
    }
}
