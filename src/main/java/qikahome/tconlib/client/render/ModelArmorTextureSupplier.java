package qikahome.tconlib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier;
import net.minecraft.world.item.ItemDisplayContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.Function;

@SuppressWarnings({ "null" })
public class ModelArmorTextureSupplier implements ArmorTextureSupplier, ArmorTextureSupplier.ArmorTexture {
    private static final int MAX_LIGHT = LightTexture.pack(15, 15);

    @Nonnull
    public static final RecordLoadable<ModelArmorTextureSupplier> LOADER = RecordLoadable.create(
            Utils.TransformationLoadable.INSTANCE.nullableField("transform", s -> s.transform),
            StringLoadable.DEFAULT.nullableField("attach", s -> s.attachName),
            IntLoadable.range(0, 15).defaultField("luminosity", 0, false, s -> s.luminosity),
            IntLoadable.range(0, Integer.MAX_VALUE).defaultField("armor_model_override", 0, false, s -> s.armorModelOverride),
            Loadables.RESOURCE_LOCATION.nullableField("item", s -> s.itemId),
            ModelArmorTextureSupplier::new);

    @Nullable
    private final Transformation transform;
    @Nullable
    private final String attachName;
    private final int luminosity;
    private final int armorModelOverride;
    @Nullable
    private final ResourceLocation itemId;

    public static final String ARMOR_MODEL_TAG = "qikas_tconlib:armor_model";

    @Nullable
    private AttachPoint cachedAttach;
    @Nullable
    private ItemStack currentStack;

    public ModelArmorTextureSupplier(@Nullable Transformation transform,
            @Nullable String attachName, int luminosity, int armorModelOverride,
            @Nullable ResourceLocation itemId) {
        this.transform = transform;
        this.attachName = attachName;
        this.luminosity = luminosity;
        this.armorModelOverride = armorModelOverride;
        this.itemId = itemId;
    }

    @Override
    public ArmorTexture getArmorTexture(ItemStack stack, TextureType textureType, RegistryAccess access) {
        if (itemId != null && !ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(itemId)) {
            return ArmorTexture.EMPTY;
        }
        currentStack = stack;
        if (cachedAttach == null && attachName != null) {
            cachedAttach = AttachPoint.fromString(attachName);
        }
        return this;
    }

    @Override
    public void renderTexture(Model armorModel, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int packedOverlay, float red, float green, float blue,
            float alpha, boolean hasGlint) {
        if (luminosity > 0) {
            packedLight = applyLuminosity(packedLight, luminosity);
        }
        poseStack.pushPose();
        if (cachedAttach != null && armorModel instanceof HumanoidModel<?> humanoid) {
            cachedAttach.getPart(humanoid).translateAndRotate(poseStack);
        }
        if (transform != null) {
            poseStack.last().pose().mul(transform.getMatrix());
        }

        ItemStack renderStack = currentStack;
        if (armorModelOverride != 0) {
            renderStack = currentStack.copy();
            renderStack.getOrCreateTag().putInt(ARMOR_MODEL_TAG, armorModelOverride);
        }

        Minecraft.getInstance().getItemRenderer().renderStatic(renderStack, ItemDisplayContext.NONE,
                packedLight, packedOverlay, poseStack, bufferSource, Minecraft.getInstance().level, 0);

        poseStack.popPose();
    }

    private static int applyLuminosity(int packedLight, int luminosity) {
        if (luminosity >= 15) {
            return MAX_LIGHT;
        }
        return Math.max(luminosity, (packedLight & 0xFFFF) >> 4) << 4
                | Math.max(luminosity, packedLight >> 20 & 0xFFFF) << 20;
    }

    @Override
    public RecordLoadable<? extends ArmorTextureSupplier> getLoader() {
        return LOADER;
    }

    /** Body part to attach the 3D model to. */
    public enum AttachPoint {
        BODY(m -> m.body),
        HEAD(m -> m.head),
        LEFT_ARM(m -> m.leftArm),
        RIGHT_ARM(m -> m.rightArm),
        LEFT_LEG(m -> m.leftLeg),
        RIGHT_LEG(m -> m.rightLeg);

        private final Function<HumanoidModel<?>, ModelPart> getter;

        AttachPoint(Function<HumanoidModel<?>, ModelPart> getter) {
            this.getter = getter;
        }

        public ModelPart getPart(HumanoidModel<?> humanoid) {
            return getter.apply(humanoid);
        }

        @Nullable
        public static AttachPoint fromString(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
