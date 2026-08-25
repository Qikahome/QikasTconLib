package qikahome.tconlib.placeabletool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import qikahome.tconlib.TconLib;
import qikahome.tconlib.placeabletool.PlacedToolBlock.IToolBlockEntity;
import qikahome.tconlib.placeabletool.hook.PlacedToolTickModifierHook;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.loadable.record.SingletonLoader;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

@Mod.EventBusSubscriber(modid = TconLib.MODID)
public enum PlacedToolInventoryTickModule implements ModifierModule, PlacedToolTickModifierHook {
    INSTANCE;

    private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.defaultHooks(TconLib.PLACED_TOOL_TICK_HOOK);
    public static final RecordLoadable<PlacedToolInventoryTickModule> LOADER = new SingletonLoader<>(INSTANCE);

    public List<ModuleHook<?>> getDefaultHooks() {
        return DEFAULT_HOOKS;
    }

    private static Map<LevelAccessor, FakeLiving> map = new HashMap<>();

    private static FakeLiving getFakeForLevel(Level level) {
        if (!map.containsKey(level))
            map.put(level, new FakeLiving(level));
        return map.get(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        map.remove(event.getLevel());
    }

    @Override
    public void onTick(IToolStackView tool, ModifierEntry modifier, BlockState state, Level level, BlockPos pos,
            IToolBlockEntity ptbe) {
        var stack = ptbe.getStack();
        var fakeLiving = getFakeForLevel(level);
        fakeLiving.setPos(((BlockEntity) ptbe).getBlockPos().getCenter());
        fakeLiving.stack = stack;
        fakeLiving.isInWater = state.getValue(BlockStateProperties.WATERLOGGED);
        fakeLiving.tickCount = (int) level.getGameTime();
        ;
        for (var entry : tool.getModifierList()) {
            entry.getHook(ModifierHooks.INVENTORY_TICK).onInventoryTick(tool, entry, level, fakeLiving, 0, false, false,
                    stack);
        }
        fakeLiving.stack = ItemStack.EMPTY;
        fakeLiving.isInWater = false;
    }

    @Override
    public RecordLoadable<? extends ModifierModule> getLoader() {
        return LOADER;
    }

    public static final RegistryObject<EntityType<FakeLiving>> FAKE_LIVING_TYPE = TconLib.ENTITY_TYPES.register(
            "fake_living",
            () -> EntityType.Builder.<FakeLiving>createNothing(MobCategory.MISC).noSave().noSummon());

    private static final class FakeLiving extends LivingEntity {
        private ItemStack stack = ItemStack.EMPTY;
        private boolean isInWater = false;

        protected FakeLiving(Level level) {
            super(FAKE_LIVING_TYPE.get(), level);
        }

        @Override
        public Iterable<ItemStack> getArmorSlots() {
            return List.of();
        }

        @Override
        public ItemStack getItemBySlot(EquipmentSlot slot) {
            return slot == EquipmentSlot.MAINHAND ? stack : ItemStack.EMPTY;
        }

        @Override
        public void setItemSlot(EquipmentSlot slot, ItemStack newStack) {
            if (slot == EquipmentSlot.MAINHAND) {
                stack = newStack;
            }
        }

        @Override
        public HumanoidArm getMainArm() {
            return HumanoidArm.RIGHT;
        }

        @Override
        public boolean isInWater() {
            return isInWater;
        }

        @Override
        public boolean isUnderWater() {
            return isInWater;
        }
    }

    /** MOD 总线：注册 FakeLiving 实体类型的默认属性表（LivingEntity 构造时读 getMaxHealth，缺了会 NPE） */
    @Mod.EventBusSubscriber(modid = TconLib.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class AttributeEvents {
        @SubscribeEvent
        public static void onAttributeCreation(EntityAttributeCreationEvent event) {
            event.put(FAKE_LIVING_TYPE.get(), LivingEntity.createLivingAttributes().build());
        }
    }

}
