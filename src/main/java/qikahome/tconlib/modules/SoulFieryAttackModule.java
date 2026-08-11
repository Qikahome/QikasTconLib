package qikahome.tconlib.modules;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.json.LevelingValue;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MonsterMeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileLaunchModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;

import javax.annotation.Nullable;

import it.crystalnest.soul_fire_d.api.FireManager;

import java.util.List;

/** Module for lighting the target on fire after a melee or ranged attack */
public record SoulFieryAttackModule(LevelingValue time)
    implements ModifierModule, ProjectileLaunchModifierHook.NoShooter, ProjectileHitModifierHook, MeleeHitModifierHook,
    MonsterMeleeHitModifierHook.RedirectAfter {
  private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.defaultHooks(ModifierHooks.MELEE_HIT,
      ModifierHooks.MONSTER_MELEE_HIT, ModifierHooks.PROJECTILE_LAUNCH, ModifierHooks.PROJECTILE_SHOT,
      ModifierHooks.PROJECTILE_THROWN, ModifierHooks.PROJECTILE_HIT);
  public static final RecordLoadable<SoulFieryAttackModule> LOADER = RecordLoadable.create(
      LevelingValue.LOADABLE.requiredField("seconds", SoulFieryAttackModule::time),
      SoulFieryAttackModule::new);

  @Override
  public RecordLoadable<SoulFieryAttackModule> getLoader() {
    return LOADER;
  }

  @Override
  public List<ModuleHook<?>> getDefaultHooks() {
    return DEFAULT_HOOKS;
  }

  /** Sets the target on fire */
  private void setFire(ModifierEntry modifier, Entity target) {
    FireManager.setOnFire(target, Math.round(time.compute(modifier.getEffectiveLevel())), FireManager.SOUL_FIRE_TYPE);
  }

  @Override
  public float beforeMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damage,
      float baseKnockback, float knockback) {
    // vanilla hack: apply fire so the entity drops the proper items on instant kill
    LivingEntity target = context.getLivingTarget();
    if (target != null && !target.isOnFire()) {
      target.setRemainingFireTicks(1);
    }
    return knockback;
  }

  @Override
  public void failedMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context,
      float damageAttempted) {
    // conclusion of vanilla hack: we don't want the target on fire if we did not
    // hit them
    LivingEntity target = context.getLivingTarget();
    if (target != null && target.getRemainingFireTicks() == 1) {
      target.clearFire();
    }
  }

  @Override
  public void afterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damageDealt) {
    setFire(modifier, context.getTarget());
  }

  @Override
  public void onProjectileShoot(IToolStackView tool, ModifierEntry modifier, @Nullable LivingEntity shooter,
      ItemStack ammo, Projectile projectile, @Nullable AbstractArrow arrow, ModDataNBT persistentData,
      boolean primary) {
    // this is mostly cosmetic as we handle hit time below
    FireManager.setOnFire(projectile, 100, FireManager.SOUL_FIRE_TYPE);
  }

  @Override
  public boolean onProjectileHitEntity(ModifierNBT modifiers, ModDataNBT persistentData, ModifierEntry modifier,
      Projectile projectile, EntityHitResult hit, @Nullable LivingEntity attacker, @Nullable LivingEntity target) {
    setFire(modifier, hit.getEntity());
    return false;
  }
}
