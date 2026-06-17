package qikahome.tconlib.client.render;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.ExtraTextureContext;
import slimeknights.mantle.client.model.util.SimpleBlockModel;
import slimeknights.tconstruct.library.client.model.block.IncrementalFluidCuboid;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolTankHelper;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

/**
 * Unbaked geometry for a tank modifier model.
 * <p>
 * JSON format:
 * <pre>
 * {
 *   "loader": "qikas_tconlib:tank_modifier",
 *   "textures": { ... },
 *   "elements": [ ... ],
 *   "fluid": {
 *     "from": [x, y, z],
 *     "to": [x, y, z],
 *     "increments": N
 *   }
 * }
 * </pre>
 */
public class TankModifierModel implements IUnbakedGeometry<TankModifierModel> {
    public static final IGeometryLoader<TankModifierModel> LOADER = TankModifierModel::deserialize;

    private final SimpleBlockModel model;
    private final IncrementalFluidCuboid fluid;

    public TankModifierModel(SimpleBlockModel model, IncrementalFluidCuboid fluid) {
        this.model = model;
        this.fluid = fluid;
    }

    public static TankModifierModel deserialize(JsonObject json, JsonDeserializationContext context) {
        SimpleBlockModel model = SimpleBlockModel.deserialize(json, context);
        IncrementalFluidCuboid fluid = IncrementalFluidCuboid.fromJson(GsonHelper.getAsJsonObject(json, "fluid"));
        return new TankModifierModel(model, fluid);
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        model.resolveParents(modelGetter, context);
    }

    @Override
    public BakedModel bake(IGeometryBakingContext owner, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter, ModelState transform,
                           ItemOverrides overrides, ResourceLocation location) {
        BakedModel baked = model.bake(owner, baker, spriteGetter, transform, overrides, location);
        return new Baked(baked, owner, transform, model, fluid);
    }

    /**
     * Baked model for a modifier that renders a fluid tank inside the tool model.
     * Extends {@link BakedBlockModifierModel} to integrate with the modifier model caching system.
     * <p>
     * In {@link #resolve(IToolStackView, ModifierEntry)}, the fluid is read from the tool's
     * modifier persistent data (stored under the modifier's ID) and a new instance
     * with the fluid rendered is returned.
     */
    public static class Baked extends BakedBlockModifierModel {
        private static final ResourceLocation BAKE_LOCATION = new ResourceLocation("qikas_tconlib", "tank_modifier_baking");

        private final IGeometryBakingContext owner;
        private final ModelState transform;
        private final SimpleBlockModel model;
        private final IncrementalFluidCuboid fluid;

        public Baked(BakedModel original, IGeometryBakingContext owner, ModelState transform,
                     SimpleBlockModel model, IncrementalFluidCuboid fluid) {
            super(original);
            this.owner = owner;
            this.transform = transform;
            this.model = model;
            this.fluid = fluid;
        }

        @Override
        public BakedModel resolve(IToolStackView tool, ModifierEntry entry) {
            var fluidStack = ToolTankHelper.TANK_HELPER.getFluid(tool);
            if (fluidStack.isEmpty()) {
                return originalModel;
            }
            return bakeWithFluid(tool, fluidStack);
        }

        private BakedModel bakeWithFluid(IToolStackView tool, FluidStack fluidStack) {
            Function<Material, TextureAtlasSprite> spriteGetter = Material::sprite;
            TextureAtlasSprite particle = spriteGetter.apply(owner.getMaterial("particle"));
            SimpleBakedModel.Builder builder = SimpleBlockModel.bakedBuilder(owner, ItemOverrides.EMPTY)
                    .particle(particle);
            IQuadTransformer quadTransformer = SimpleBlockModel.applyTransform(transform, owner.getRootTransform());

            for (BlockElement element : model.getElements()) {
                SimpleBlockModel.bakePart(builder, owner, element, spriteGetter, transform, quadTransformer, BAKE_LOCATION);
            }

            IClientFluidTypeExtensions attributes = IClientFluidTypeExtensions.of(fluidStack.getFluid());
            FluidType type = fluidStack.getFluid().getFluidType();
            int color = attributes.getTintColor(fluidStack);
            int luminosity = type.getLightLevel(fluidStack);

            int increments = fluid.getIncrements();
            int capacity = ToolTankHelper.TANK_HELPER.getCapacity(tool);
            int amount = Mth.clamp(fluidStack.getAmount() * increments / Math.max(capacity, 1), 1, increments);
            BlockElement fluidPart = fluid.getPart(amount, type.isLighterThanAir());

            ImmutableMap<String, Material> textures = ImmutableMap.of(
                    "fluid", new Material(InventoryMenu.BLOCK_ATLAS, attributes.getStillTexture(fluidStack)),
                    "flowing_fluid", new Material(InventoryMenu.BLOCK_ATLAS, attributes.getFlowingTexture(fluidStack)));
            var texturedContext = new ExtraTextureContext(owner, textures);

            IQuadTransformer fluidTransformer = color == -1 ? quadTransformer
                    : quadTransformer.andThen(ColoredBlockModel.applyColorQuadTransformer(color));
            ColoredBlockModel.bakePart(builder, texturedContext, fluidPart, luminosity, spriteGetter,
                    transform.getRotation(), fluidTransformer, transform.isUvLocked(), BAKE_LOCATION);

            return builder.build(SimpleBlockModel.getRenderTypeGroup(owner));
        }

        @Nullable
        @Override
        public Object getCacheKey(IToolStackView tool, ModifierEntry modifier) {
            var fuildStack = ToolTankHelper.TANK_HELPER.getFluid(tool);
            if (fuildStack.isEmpty()) {
                return modifier.getId();
            }
            int increments = fluid.getIncrements();
            int capacity = ToolTankHelper.TANK_HELPER.getCapacity(tool);
            int amountScaled = Mth.clamp(fuildStack.getAmount() * increments / Math.max(capacity, 1), 1, increments);
            return new TankCacheKey(modifier.getId(), fuildStack.getFluid(), amountScaled);
        }

        private record TankCacheKey(Object modifierId, Object fluid, int fillLevel) {}

        @Nonnull
        @Override
        public BakedModel applyTransform(@Nonnull ItemDisplayContext cameraTransformType,
                                          @Nonnull PoseStack poseStack, boolean applyLeftHandTransform) {
            super.applyTransform(cameraTransformType, poseStack, applyLeftHandTransform);
            return this;
        }
    }
}
