package qikahome.tconlib;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import qikahome.tconlib.client.BlockModifierManager;
import qikahome.tconlib.client.render.BlockToolModel;
import qikahome.tconlib.client.render.TankModifierModel;

// 这里的值应该与META-INF/mods.toml文件中的条目匹配
@Mod(TconLib.MODID)
@SuppressWarnings("removal")
public class TconLib {
    // 在一个公共位置定义mod id，以便所有内容都可以引用
    public static final String MODID = "qikas_tconlib";
    // 直接引用一个slf4j日志记录器
    public static final Logger LOGGER = LogUtils.getLogger();

    public TconLib(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // 注册mod加载的commonSetup方法
        modEventBus.addListener(this::commonSetup);

        // 为服务器和其他我们感兴趣的游戏事件注册自己
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Start TconLib common setup.");
        event.enqueueWork(() -> {

        });
    }

    // 您可以使用SubscribeEvent，让事件总线发现要调用的方法
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 当服务器启动时做一些事情
        // LOGGER.info("HELLO from server starting");
    }

    // 您可以使用EventBusSubscriber自动注册类中所有带有@SubscribeEvent注解的静态方法
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            BlockModifierManager.init(event);
        }

        @SubscribeEvent
        public static void registerModelLoaders(ModelEvent.RegisterGeometryLoaders event) {
            event.register("block_tool", BlockToolModel.LOADER);
            event.register("tank_modifier", TankModifierModel.LOADER);
        }

    }

    public static final ResourceLocation getResource(String path) {
        return new ResourceLocation(MODID, path);
    }
}
