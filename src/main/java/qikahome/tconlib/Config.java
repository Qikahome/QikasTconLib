package qikahome.tconlib;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 一个配置类示例。这不是必需的，但为了保持配置的组织性，有一个配置类是个好主意。
// 演示如何使用Forge的配置API
@Mod.EventBusSubscriber(modid = TconLib.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

//     private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
//             .comment("是否在通用设置中记录泥土方块")
//             .define("logDirtBlock", true);

//     private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
//             .comment("一个魔法数字")
//             .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

//     public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
//             .comment("你希望魔法数字的介绍信息是什么")
//             .define("magicNumberIntroduction", "The magic number is... ");

//     // 一系列字符串，被视为物品的资源位置
//     private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
//             .comment("在通用设置中记录的物品列表。")
//             .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    static final ForgeConfigSpec SPEC = BUILDER.build();

//     public static boolean logDirtBlock;
//     public static int magicNumber;
//     public static String magicNumberIntroduction;
//     public static Set<Item> items;

    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        // logDirtBlock = LOG_DIRT_BLOCK.get();
        // magicNumber = MAGIC_NUMBER.get();
        // magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        //     // 将字符串列表转换为物品集合
        //     items = ITEM_STRINGS.get().stream()
        //         .map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName)))
        //         .collect(Collectors.toSet());
    }
}
