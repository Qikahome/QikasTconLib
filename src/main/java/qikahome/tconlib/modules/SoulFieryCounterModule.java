package qikahome.tconlib.modules;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus.Internal;

import it.crystalnest.soul_fire_d.api.FireManager;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.library.json.LevelingValue;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.tools.modules.armor.CounterModule;

/** Module implementing the counterattack side of fiery */
public record SoulFieryCounterModule(LevelingValue chance, LevelingValue constant, LevelingValue random,
    int durabilityUsage, IJsonPredicate<LivingEntity> defender, IJsonPredicate<LivingEntity> attacker,
    ModifierCondition<IToolStackView> condition) implements CounterModule {
  public static final RecordLoadable<SoulFieryCounterModule> LOADER = CounterModule.makeLoader("seconds",
      SoulFieryCounterModule::new);

  @Override
  public RecordLoadable<SoulFieryCounterModule> getLoader() {
    return LOADER;
  }

  /** @apiNote use {@link #builder()} */
  @Internal
  public SoulFieryCounterModule {
  }

  /** Creates a new builder instance */
  public static CounterModule.Builder<SoulFieryCounterModule> builder() {
    return new CounterModule.Builder<>(SoulFieryCounterModule::new);
  }

  @Override
  public boolean canApply(Entity target) {
    return !target.fireImmune();
  }

  @Override
  public void applyEffect(IToolStackView tool, ModifierEntry modifier, float value, EquipmentContext context,
      Entity attacker, DamageSource source, float damageDealt) {
    FireManager.setOnFire(attacker, Math.round(value), FireManager.SOUL_FIRE_TYPE);
  }
}
