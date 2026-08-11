package qikahome.tconlib.modules;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.data.predicate.entity.LivingEntityPredicate;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InventoryTickModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition.ConditionalModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import java.util.List;

public record ConditionalInventoryTickModifierModule(IJsonPredicate<LivingEntity> entity,
        ModifierModule subModule, ModifierCondition<IToolStackView> condition)
        implements ModifierModule, InventoryTickModifierHook, ConditionalModule<IToolStackView> {

    private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.defaultHooks(ModifierHooks.INVENTORY_TICK);

    public static final RecordLoadable<ConditionalInventoryTickModifierModule> LOADER = RecordLoadable.create(
            LivingEntityPredicate.LOADER.nullableField("entity", ConditionalInventoryTickModifierModule::entity),
            ModifierModule.LOADER.requiredField("module", ConditionalInventoryTickModifierModule::subModule),
            ModifierCondition.TOOL_FIELD,
            ConditionalInventoryTickModifierModule::new);

    @Override
    public RecordLoadable<? extends ModifierModule> getLoader() {
        return LOADER;
    }

    @Override
    public List<ModuleHook<?>> getDefaultHooks() {
        return DEFAULT_HOOKS;
    }

    @Override
    public void onInventoryTick(IToolStackView tool, ModifierEntry modifier, Level level, LivingEntity entity,
            int slot, boolean selected, boolean held, ItemStack stack) {
        if (subModule instanceof InventoryTickModifierHook hook
                && condition().matches(tool, modifier)
                && (this.entity == null || this.entity.matches(entity))) {
            hook.onInventoryTick(tool, modifier, level, entity, slot, selected, held, stack);
        }
    }
}
