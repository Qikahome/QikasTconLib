package qikahome.tconlib;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;
import qikahome.tconlib.client.BlockModifierManager;
import qikahome.tconlib.client.render.BlockToolModel;
import qikahome.tconlib.client.render.ModelArmorTextureSupplier;
import qikahome.tconlib.client.render.PlacedToolBlockEntityRenderer;
import qikahome.tconlib.client.render.TankModifierModel;
import qikahome.tconlib.client.screen.AutoSizedToolContainerScreen;
import qikahome.tconlib.placeabletool.PlacedToolBlock;
import qikahome.tconlib.placeabletool.PlacedToolBlock.PlacedToolBlockEntity;
import qikahome.tconlib.placeabletool.PlacedToolContainerMenu;
import qikahome.tconlib.placeabletool.PlacedToolInventoryTickModule;
import qikahome.tconlib.placeabletool.PlacingModule;
import qikahome.tconlib.placeabletool.PlacedToolLightModule;
import qikahome.tconlib.placeabletool.ProjectileToolPlacingModule;
import qikahome.tconlib.placeabletool.ToolDispenserBehavior;
import qikahome.tconlib.placeabletool.ToolPlacementDataManager;
import qikahome.tconlib.placeabletool.hook.PlacedToolInteractionModifierHook;
import qikahome.tconlib.placeabletool.hook.PlacedToolLightModifierHook;
import qikahome.tconlib.placeabletool.hook.PlacedToolTickModifierHook;
import qikahome.tconlib.placeabletool.hook.ToolPlacingModifierHook;
import slimeknights.mantle.registration.deferred.BlockEntityTypeDeferredRegister;
import slimeknights.mantle.registration.deferred.EntityTypeDeferredRegister;
import slimeknights.mantle.registration.deferred.MenuTypeDeferredRegister;
import slimeknights.tconstruct.common.registration.BlockDeferredRegisterExtension;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableLauncherItem;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.client.ToolContainerScreen;
import slimeknights.tconstruct.tools.menu.ToolContainerMenu;

// 这里的值应该与META-INF/mods.toml文件中的条目匹配
@Mod(TconLib.MODID)
@SuppressWarnings("removal")
public class TconLib {
    // 在一个公共位置定义mod id，以便所有内容都可以引用
    public static final String MODID = "qikas_tconlib";
    // 直接引用一个slf4j日志记录器
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final BlockDeferredRegisterExtension BLOCKS = new BlockDeferredRegisterExtension(MODID);
    public static final BlockEntityTypeDeferredRegister BLOCK_ENTITIES = new BlockEntityTypeDeferredRegister(MODID);
    public static final MenuTypeDeferredRegister MENUS = new MenuTypeDeferredRegister(MODID);
    public static final EntityTypeDeferredRegister ENTITY_TYPES = new EntityTypeDeferredRegister(MODID);

    public static final RegistryObject<PlacedToolBlock> PLACED_TOOL = BLOCKS.registerNoItem("placed_tool",
            () -> new PlacedToolBlock(
                    BlockBehaviour.Properties.of().strength(0.5f, 0.5f).pushReaction(PushReaction.DESTROY)));

    public static final RegistryObject<BlockEntityType<PlacedToolBlockEntity>> PLACED_TOOL_ENTITY = BLOCK_ENTITIES
            .register("placed_tool", PlacedToolBlockEntity::new, set -> set.add(PLACED_TOOL.get()));

    public static final RegistryObject<MenuType<PlacedToolContainerMenu>> PLACED_TOOL_MENU = MENUS.register(
            "placed_tool", PlacedToolContainerMenu::new);

    public void registerSerializers(RegisterEvent event) {
        if (event.getRegistryKey() == Registries.RECIPE_SERIALIZER) {
            ModifierModule.LOADER.register(getResource("placing"), PlacingModule.LOADER);
            ModifierModule.LOADER.register(getResource("placed_inventory_tick"), PlacedToolInventoryTickModule.LOADER);
            ModifierModule.LOADER.register(getResource("placed_light_tank"),
                    PlacedToolLightModule.TankLightModule.LOADER);
            ModifierModule.LOADER.register(getResource("placed_light_minimum"),
                    PlacedToolLightModule.MinimumLightModule.LOADER);
            ModifierModule.LOADER.register(getResource("projectile_placing"), ProjectileToolPlacingModule.LOADER);
        }

    }

    // region Hooks
    public static final ModuleHook<ToolPlacingModifierHook> TOOL_PLACING_MODIFIER_HOOK = ModifierHooks.register(
            getResource("tool_placing"), ToolPlacingModifierHook.class, ToolPlacingModifierHook.AllMerger::new,
            (tool, modifier, context, placeContext, source, state, toolStack) -> state);

    public static final ModuleHook<PlacedToolInteractionModifierHook> PLACED_TOOL_INTERACTION_HOOK = ModifierHooks
            .register(
                    getResource("placed_tool_interaction"), PlacedToolInteractionModifierHook.class,
                    PlacedToolInteractionModifierHook.AllMerger::new, new PlacedToolInteractionModifierHook() {
                    });

    public static final ModuleHook<PlacedToolTickModifierHook> PLACED_TOOL_TICK_HOOK = ModifierHooks.register(
            getResource("placed_tool_tick"), PlacedToolTickModifierHook.class,
            PlacedToolTickModifierHook.AllMerger::new,
            (tool, modifier, state, level, pos, ptbe) -> {
            });

    public static final ModuleHook<PlacedToolLightModifierHook> PLACED_TOOL_LIGHT_HOOK = ModifierHooks.register(
            getResource("placed_tool_light"), PlacedToolLightModifierHook.class,
            PlacedToolLightModifierHook.AllMerger::new,
            (tool, modifier, state, level, pos, ptbe, light) -> light);
    // endregion

    public TconLib(FMLJavaModLoadingContext context) {
        // 注册配置文件（此前只 build 了 SPEC 没有注册，Forge 永不加载它，读取时抛 IllegalStateException）。
        // 屏幕切换是纯客户端选项，用 CLIENT 类型避免服务端也加载
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        IEventBus modEventBus = context.getModEventBus();

        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        // 注册mod加载的commonSetup方法
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerSerializers);
        // 为服务器和其他我们感兴趣的游戏事件注册自己
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Start TconLib common setup.");
        event.enqueueWork(() -> {
            // 发射器行为：所有可修改工具（工具/远程/盔甲）统一注册，行为内按 CAN_PLACE 决定放置/弹出
            // 注意：ModifiableLauncherItem（弓/弩）不继承 ModifiableItem，需单独覆盖
            for (Item item : ForgeRegistries.ITEMS) {
                if (item instanceof ModifiableItem || item instanceof ModifiableLauncherItem
                        || item instanceof ModifiableArmorItem) {
                    DispenserBlock.registerBehavior(item, ToolDispenserBehavior.INSTANCE);
                }
            }
        });
    }

    // 您可以使用SubscribeEvent，让事件总线发现要调用的方法
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 当服务器启动时做一些事情
        // LOGGER.info("HELLO from server starting");
    }

    /** 服务端数据包 reload 时加载放置数据（datapack） */
    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        ToolPlacementDataManager.init(event);
    }

    // 您可以使用EventBusSubscriber自动注册类中所有带有@SubscribeEvent注解的静态方法
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        public static boolean cancelToolContainerScreenRegister = true;
        private static final MenuScreens.ScreenConstructor<ToolContainerMenu, AbstractContainerScreen<ToolContainerMenu>> provider = (
                a, b, c) -> Config.ENABLE_AUTO_SIZED_TOOL_SCREEN.get() ? new AutoSizedToolContainerScreen(a, b, c)
                        : new ToolContainerScreen(a, b, c);

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            BlockModifierManager.init(event);
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(TconLib.PLACED_TOOL_ENTITY.get(), PlacedToolBlockEntityRenderer::new);
        }

        @SubscribeEvent
        public static void registerModelLoaders(ModelEvent.RegisterGeometryLoaders event) {
            event.register("block_tool", BlockToolModel.LOADER);
            event.register("tank_modifier", TankModifierModel.LOADER);
        }

        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            ArmorTextureSupplier.LOADER.register(getResource("item_model"), ModelArmorTextureSupplier.LOADER);

            event.enqueueWork(() -> {
                // 新 AutoSizedGUI 屏幕与官方屏幕可配置切换（默认新屏幕）
                cancelToolContainerScreenRegister = false;
                MenuScreens.register(TconLib.PLACED_TOOL_MENU.get(), provider);
                MenuScreens.register(TinkerTools.toolContainer.get(), provider);
                cancelToolContainerScreenRegister = true;
                ForgeRegistries.ITEMS.forEach(item -> {
                    if (item instanceof ModifiableArmorItem) {
                        ItemProperties.register(item, getResource("armor_model"),
                                (stack, level, entity, seed) -> {
                                    var tag = stack.getTag();
                                    return tag != null ? tag.getInt(ModelArmorTextureSupplier.ARMOR_MODEL_TAG) : 0;
                                });
                    }
                    // 放置形态：放下的工具可换模型。渲染器会给渲染副本写入放置分类（1=墙面 2=天花板 3=地板），
                    // 资源包在工具模型 JSON 的 overrides 里按 qikas_tconlib:placed=<值> 切换（同护甲 armor_model
                    // 机制）；未放置默认 -1
                    if (item instanceof IModifiable) {
                        ItemProperties.register(item, getResource("placed"), (stack, level, entity, seed) -> {
                            var tag = stack.getTag();
                            return tag != null && tag.contains(PlacedToolBlockEntityRenderer.PLACED_MODEL_TAG)
                                    ? tag.getInt(PlacedToolBlockEntityRenderer.PLACED_MODEL_TAG)
                                    : -1;
                        });
                    }
                });
            });
        }
    }

    @Nonnull
    public static final ResourceLocation getResource(String path) {
        return new ResourceLocation(MODID, path);
    }
}
