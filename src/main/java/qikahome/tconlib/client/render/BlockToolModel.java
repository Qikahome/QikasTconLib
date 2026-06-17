package qikahome.tconlib.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import qikahome.tconlib.TconLib;
import qikahome.tconlib.client.BlockModifierManager;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.DynamicBakedWrapper;
import slimeknights.mantle.client.model.util.ExtraTextureContext;
import slimeknights.mantle.client.model.util.SimpleBlockModel;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.array.ArrayLoadable;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo.TintedSprite;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.client.model.ModelProperties;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.worktable.ModifierSetWorktableRecipe;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.MaterialIdNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import static qikahome.tconlib.TconLib.LOGGER;

@SuppressWarnings({"null","removal"})
public class BlockToolModel implements IUnbakedGeometry<BlockToolModel> {

    @Nonnull
    public static final IGeometryLoader<BlockToolModel> LOADER = BlockToolModel::deserialize;

    private static record ModifierModel(BlockModel model, List<IQuadTransformer> transformers) {
        BakedModelWithTransformers<BakedBlockModifierModel> bake(IGeometryBakingContext owner, ModelBaker baker,
                Function<Material, TextureAtlasSprite> spriteGetter, ModelState transform,
                ResourceLocation location) {
            return new BakedModelWithTransformers<>(
                    BakedBlockModifierModel.cast(model.bake(baker, model, spriteGetter, transform, location, true)),
                    transformers);
        }
    }

    private static record BakedModelWithTransformers<M extends BakedModel>(M model, List<IQuadTransformer> transformers) {
    }

    private static final String PATH_PREFIX = "models/";
    private static final Loadable<List<Set<String>>> PARTS = StringLoadable.DEFAULT.set(ArrayLoadable.COMPACT_OR_EMPTY)
            .list(1);

    private static void parseModifierModels(JsonElement ele, JsonDeserializationContext context,
            Consumer<ModifierModel> consumer) {
        try {
            if (ele.isJsonPrimitive()) {
                String str = ele.getAsString();
                var model = Utils.parseModel(new ResourceLocation(str), PATH_PREFIX, context);
                if (model != null) {
                    consumer.accept(new ModifierModel(model, List.of()));
                }
            } else if (ele.isJsonObject()) {
                var obj = ele.getAsJsonObject();
                var modelElement = obj.get("model");
                if (modelElement == null) {
                    TconLib.LOGGER.error("Failed to parse Modifier Model, JSON object does not contains \"model\", {}",
                            obj);
                    return;
                }
                BlockModel model;
                if (modelElement.isJsonObject()) {
                    model = context.deserialize(modelElement, BlockModel.class);
                } else {
                    model = Utils.parseModel(new ResourceLocation(modelElement.getAsString()), PATH_PREFIX, context);
                    if (model == null) {
                        return;
                    }
                }
                var transformers = new ArrayList<IQuadTransformer>();
                if (obj.has("transform")) {
                    for (var tran : Utils.parseTransforms(obj.get("transform")))
                        transformers.add(QuadTransformers.applying(tran));
                }
                if (obj.has("color")) {
                    var color = Utils.parseColor(obj.get("color"));
                    transformers.add(QuadTransformers.applyingColor(color));
                }
                if (obj.has("light"))
                    transformers.add(QuadTransformers.applyingLightmap(obj.get("light").getAsInt()));
                consumer.accept(new ModifierModel(model, transformers));
            } else if (ele.isJsonArray())
                ele.getAsJsonArray().forEach(subele -> parseModifierModels(subele, context, consumer));
            else
                TconLib.LOGGER.error(
                        "Cannot parse Modifier Model, 1.Not a String, 2.Not a JsonObject, 3.Not a JsonArray.");
        } catch (Exception e) {
            TconLib.LOGGER.error("Failed to parse modifier model: {}", ele, e);
        }
    }

    public static BlockToolModel deserialize(JsonObject json, JsonDeserializationContext context) {
        // 解析父模型
        SimpleBlockModel model = SimpleBlockModel.deserialize(json, context);
        // 解析修饰符名
        String modifierKey = GsonHelper.getAsString(json, "modifier_key", "");

        JsonObject modifierModelEntries = BlockModifierManager.INSTANCE.getModifierModels(modifierKey);
        Map<ModifierId, List<ModifierModel>> modifierModels = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : modifierModelEntries.entrySet()) {
            ModifierId modifierId = ModifierId.tryParse(entry.getKey());
            if (modifierId == null) {
                continue;
            }
            List<ModifierModel> models = new ArrayList<>();
            parseModifierModels(entry.getValue(), context, models::add);
            modifierModels.put(modifierId, models);
        }
        var parts = PARTS.getIfPresent(json, "parts");
        return new BlockToolModel(model, parts, modifierModels);
    }

    private final SimpleBlockModel model;

    private final List<Set<String>> parts;

    private final Map<ModifierId, List<ModifierModel>> modifierModels;

    private BlockToolModel(SimpleBlockModel model, List<Set<String>> parts,
            Map<ModifierId, List<ModifierModel>> modifierModels) {
        this.model = model;
        this.parts = parts;
        this.modifierModels = modifierModels;
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        model.resolveParents(modelGetter, context);
        modifierModels.values().forEach(models -> models.forEach(model -> model.model().resolveParents(modelGetter)));
    }

    @Override
    public BakedModel bake(IGeometryBakingContext owner, ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter, ModelState transform, ItemOverrides overrides,
            ResourceLocation location) {
        BakedModel baked = model.bake(owner, baker, spriteGetter, transform, overrides, location);
        Map<ModifierId, List<BakedModelWithTransformers<BakedBlockModifierModel>>> bakedMods = new HashMap<>();
        for (var entry : modifierModels.entrySet()) {
            bakedMods.put(entry.getKey(), entry.getValue().stream()
                    .map(model -> model.bake(owner, baker, spriteGetter, transform, location)).toList());
        }
        boolean particleRetextured = parts.stream().anyMatch(set -> set.contains("particle"));
        return new BakedBlockTool(baked, owner, model, transform, parts, particleRetextured, overrides, bakedMods);
    }

    private record ToolCacheKey(MaterialIdNBT materials, List<Object> modifierData) {
    }

    private static class BakedBlockTool extends DynamicBakedWrapper<BakedModel> {
        // ========== 内部类：动态覆盖器（处理 ItemStack 中的 NBT 数据） ==========
        private static class ToolOverrides extends ItemOverrides {
            private final BakedBlockTool baked;
            private final ItemOverrides nested;

            public ToolOverrides(BakedBlockTool baked, ItemOverrides nested) {
                this.baked = baked;
                this.nested = nested;
            }

            @Nullable
            @Override
            public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel world,
                    @Nullable LivingEntity entity, int seed) {
                BakedModel resolved = nested.resolve(originalModel, stack, world, entity, seed);
                if (resolved != originalModel) {
                    return resolved;
                }
                if (stack.isEmpty() || !stack.hasTag()) {
                    return originalModel;
                }
                // 从 ItemStack 中提取材料列表
                MaterialIdNBT materials = MaterialIdNBT.from(stack);
                IToolStackView tool = ToolStack.from(stack);

                // 如果没有特殊数据，渲染原始模型
                ModifierNBT modifiers = tool.getUpgrades();
                if (materials.getMaterials().isEmpty() && modifiers.isEmpty()) {
                    return originalModel;
                }
                // 构建修饰符缓存键，基于修饰符自身请求的内容
                ImmutableList.Builder<Object> cacheBuilder = ImmutableList.builder();
                Set<ModifierId> hidden = ModifierSetWorktableRecipe.getModifierSet(tool.getPersistentData(),
                        TConstruct.getResource("invisible_modifiers"));
                List<List<BakedModelWithTransformers<BakedModel>>> thisModifiers = new ArrayList<>();
                for (ModifierEntry entry : modifiers) {
                    ModifierId id = entry.getId();
                    if (!hidden.contains(id)) {
                        List<BakedModelWithTransformers<BakedBlockModifierModel>> bakedModifierModels = baked.modifierModels
                                .get(id);
                        if (bakedModifierModels == null) {
                            TconLib.LOGGER.debug("Modifier model for modifier {} not found for tool {}", id,
                                    tool.getItem().toString());
                            continue;
                        }
                        Set<Object> caches = new HashSet<>();
                        for (var model : bakedModifierModels) {
                            Object cacheKey = model.model().getCacheKey(tool, entry);
                            if (cacheKey != null) {
                                caches.add(cacheKey);
                            }
                        }
                        cacheBuilder.add(caches);
                        thisModifiers.add(baked.getCachedModifierModels(caches,
                                () -> bakedModifierModels.stream().map(baked -> new BakedModelWithTransformers<BakedModel>(
                                        baked.model().resolve(tool, entry), baked.transformers())).toList()));
                    }
                }

                // 渲染特殊模型
                try {
                    return baked.thisCache.get(new ToolCacheKey(materials, cacheBuilder.build()),
                            () -> {
                                var modelBuilder = baked.getBuilderWithMaterials(materials);
                                RandomSource rand = RandomSource.create();
                                for (var modifier : thisModifiers) {
                                    for (var modifierModel : modifier) {
                                        for (Direction dir : Direction.values()) {
                                            rand.setSeed(42L);
                                            List<BakedQuad> quads = modifierModel.model().getQuads(null, dir, rand,
                                                    ModelData.EMPTY, null);
                                            for (var transformer : modifierModel.transformers()) {
                                                quads = transformer.process(quads);
                                            }
                                            quads.forEach(quad -> modelBuilder.addCulledFace(dir, quad));

                                        }
                                        rand.setSeed(42L);
                                        List<BakedQuad> quads = modifierModel.model().getQuads(null, null, rand,
                                                ModelData.EMPTY, null);
                                        for (var transformer : modifierModel.transformers()) {
                                            quads = transformer.process(quads);
                                        }
                                        quads.forEach(modelBuilder::addUnculledFace);
                                    }
                                }
                                return modelBuilder.build(SimpleBlockModel.getRenderTypeGroup(baked.owner));
                            });
                } catch (ExecutionException e) {
                    TconLib.LOGGER.error("Failed to get tool model from cache", e);
                    return originalModel;
                }
            }
        }

        // 常量：动态烘焙用的占位位置
        private static final ResourceLocation BAKE_LOCATION = Mantle.getResource("material_tool_dynamic");

        private final IGeometryBakingContext owner;
        private final SimpleBlockModel model;
        private final ModelState transform;
        private final List<Set<String>> parts; // 每个部件对应的材质槽位集合
        private final boolean particleRetextured; // 粒子纹理是否也需要重纹理化
        private final Map<ModifierId, List<BakedModelWithTransformers<BakedBlockModifierModel>>> modifierModels; // 修饰符模型缓存
        private final ItemOverrides overrides;
        private final Cache<ToolCacheKey, BakedModel> thisCache = CacheBuilder.newBuilder()
                .maximumSize(MaterialRenderInfoLoader.INSTANCE.getAllRenderInfos().size() * 3L / 2)
                .build();
        private final Cache<Set<Object>, List<BakedModelWithTransformers<BakedModel>>> modifierCache = CacheBuilder.newBuilder()
                .build();

        public ItemOverrides getOverrides() {
            return overrides;
        }

        private SimpleBakedModel.Builder getBuilder(MaterialIdNBT materials,
                Function<Material, TextureAtlasSprite> spriteGetter, Map<String, TintedSprite> tints,
                IGeometryBakingContext retextureContext) {
            TextureAtlasSprite particle = spriteGetter.apply(owner.getMaterial("particle"));
            SimpleBakedModel.Builder builder = SimpleBlockModel.bakedBuilder(owner, originalModel.getOverrides())
                    .particle(particle);
            List<BlockElement> elements = model.getElements();
            int size = elements.size();
            IQuadTransformer quadTransformer = SimpleBlockModel.applyTransform(transform, owner.getRootTransform());
            Transformation transformation = transform.getRotation();
            boolean uvlock = transform.isUvLocked();
            for (int i = 0; i < size; i++) {
                BlockElement part = elements.get(i);
                // 判断该部件的任意面是否需要着色
                TintedSprite tint = null;
                for (BlockElementFace face : part.faces.values()) {
                    TintedSprite faceTint = tints.get(face.texture);
                    if (faceTint != null) {
                        tint = faceTint;
                        break;
                    }
                }
                if (tint != null) {
                    IQuadTransformer partTransformer = tint.color() == -1 ? quadTransformer
                            : quadTransformer.andThen(ColoredBlockModel.applyColorQuadTransformer(tint.color()));
                    ColoredBlockModel.bakePart(builder, retextureContext, part, tint.emissivity(), spriteGetter,
                            transformation, partTransformer, uvlock, BAKE_LOCATION);
                } else {
                    SimpleBlockModel.bakePart(builder, retextureContext, part, spriteGetter, transform, quadTransformer,
                            BAKE_LOCATION);
                }
            }
            return builder;
        }

        private SimpleBakedModel.Builder getBuilderWithMaterials(MaterialIdNBT materials) {
            Map<String, Material> replacements = new HashMap<>();
            Map<String, TintedSprite> tints = new HashMap<>();

            Function<Material, TextureAtlasSprite> spriteGetter = Material::sprite;
            for (int i = 0; i < parts.size(); i++) {
                fetchMaterial(materials.getMaterial(i), parts.get(i), spriteGetter, replacements, tints);
            }

            IGeometryBakingContext retextureContext = new ExtraTextureContext(owner, replacements);

            return getBuilder(materials, spriteGetter, tints, retextureContext);
        }

        /**
         * 根据材料信息重新烘焙模型（核心逻辑）
         * 
         * @param materials =材料列表
         * @return 烘焙好的模型
         */
        private BakedModel bakeWithMaterials(MaterialIdNBT materials) {
            Map<String, Material> replacements = new HashMap<>();
            Map<String, TintedSprite> tints = new HashMap<>();

            Function<Material, TextureAtlasSprite> spriteGetter = Material::sprite;
            for (int i = 0; i < parts.size(); i++) {
                fetchMaterial(materials.getMaterial(i), parts.get(i), spriteGetter, replacements, tints);
            }
            if (replacements.isEmpty()) {
                return originalModel;
            }

            IGeometryBakingContext retextureContext = new ExtraTextureContext(owner, replacements);

            if (tints.isEmpty()) {
                return model.bakeDynamic(retextureContext, transform);
            }
            var builder = getBuilder(materials, spriteGetter, tints, retextureContext);
            return builder.build(SimpleBlockModel.getRenderTypeGroup(owner));
        }

        /**
         * 从缓存中获取或烘焙模型
         * 
         * @param key 缓存键
         * @return 烘焙好的模型
         */
        private BakedModel getCachedModel(ToolCacheKey key) {
            if (key.materials().getMaterials().isEmpty() && key.modifierData().isEmpty()) {
                return originalModel;
            }
            try {
                return thisCache.get(key, () -> bakeWithMaterials(key.materials()));
            } catch (ExecutionException e) {
                TconLib.LOGGER.error("Failed to get tool model from cache", e);
                return originalModel;
            }
        }

        /**
         * 从缓存中获取或烘焙模型
         * 
         * @param key 缓存键
         * @return 烘焙好的模型
         */
        private List<BakedModelWithTransformers<BakedModel>> getCachedModifierModels(Set<Object> key,
                Callable<List<BakedModelWithTransformers<BakedModel>>> sup) {
            try {
                return modifierCache.get(key, sup);
            } catch (ExecutionException e) {
                TconLib.LOGGER.error("Failed to get tool model from cache", e);
                return List.of();
            }
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            if (particleRetextured) {
                var mat = data.get(ModelProperties.MATERIALS);
                if (mat != null) {
                    var key = new ToolCacheKey(mat, List.of());
                    return getCachedModel(key).getParticleIcon(data);
                }
            }
            return originalModel.getParticleIcon(data);
        }

        @Nonnull
        @Override
        public BakedModel applyTransform(@Nonnull ItemDisplayContext cameraTransformType, @Nonnull PoseStack poseStack,
                boolean applyLeftHandTransform) {
            super.applyTransform(cameraTransformType, poseStack, applyLeftHandTransform);
            return this;
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
            return List.of(this);
        }

        @Nonnull
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                ModelData extraData, @Nullable RenderType renderType) {
            var mat = extraData.get(ModelProperties.MATERIALS);
            if (mat != null) {
                return getCachedModel(new ToolCacheKey(mat, List.of())).getQuads(state, side, rand, extraData,
                        renderType);
            }
            return originalModel.getQuads(state, side, rand, extraData, renderType);
        }

        /**
         * 获取单个材质对应的纹理信息（包括着色和自发光）
         * 
         * @param material     材质ID
         * @param retextured   需要替换的纹理槽位名称集合（例如 {"head"}）
         * @param spriteGetter 精灵获取器
         * @param replacements 输出：槽位名 -> 替换后的材质（Material）
         * @param tints        输出：带 '#' 前缀的槽位名 -> 带着色/自发光信息的纹理精灵
         */
        private void fetchMaterial(MaterialVariantId material, Set<String> retextured,
                Function<Material, TextureAtlasSprite> spriteGetter,
                Map<String, Material> replacements, Map<String, TintedSprite> tints) {
            Optional<MaterialRenderInfo> optional = MaterialRenderInfoLoader.INSTANCE.getRenderInfo(material);
            if (optional.isPresent()) {
                MaterialRenderInfo info = optional.get();
                Map<Material, TintedSprite> seen = new HashMap<>();
                for (String name : retextured) {
                    Material texture = owner.getMaterial(name);
                    TintedSprite tinted = seen.get(texture);
                    if (tinted == null) {
                        tinted = info.getSprite(texture, spriteGetter);
                        seen.put(texture, tinted);
                    }
                    TextureAtlasSprite sprite = tinted.sprite();
                    replacements.put(name, new Material(sprite.atlasLocation(), sprite.contents().name()));
                    if (tinted.color() != -1 || tinted.emissivity() > 0) {
                        tints.put('#' + name, tinted);
                    }
                }
            }
        }

        /**
         * 构造器
         * 
         * @param original           原始模型（未替换材质前的模型）
         * @param owner              烘焙上下文
         * @param model              未烘焙的简单模型（含元素列表）
         * @param transform          模型变换（旋转/缩放等）
         * @param parts              每个部件对应的材质槽位名称集合（例如 [ ["head"], ["handle"] ]）
         * @param particleRetextured 粒子纹理是否随材质替换而变化
         * @param nestedOverrides    嵌套的覆盖器（通常来自原始模型）
         */
        private BakedBlockTool(BakedModel original, IGeometryBakingContext owner, SimpleBlockModel model,
                ModelState transform, List<Set<String>> parts, boolean particleRetextured,
                ItemOverrides nestedOverrides,
                Map<ModifierId, List<BakedModelWithTransformers<BakedBlockModifierModel>>> modifierModels) {
            super(original);
            this.owner = owner;
            this.model = model;
            this.transform = transform;
            this.parts = parts;
            this.particleRetextured = particleRetextured;
            this.modifierModels = modifierModels;
            this.overrides = new ToolOverrides(this, nestedOverrides);
        }
    }
}
