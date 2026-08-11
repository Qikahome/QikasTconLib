package qikahome.tconlib.modules;

import net.minecraft.world.entity.LivingEntity;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.data.predicate.entity.LivingEntityPredicate;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MonsterMeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileLaunchModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition.ConditionalModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import javax.annotation.Nullable;

import java.util.List;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileShootModifierHook;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;

public record ConditionalHitModifierModuleModule(IJsonPredicate<LivingEntity> target,
        IJsonPredicate<LivingEntity> attacker, ModifierModule subModule,
        ModifierCondition<IToolStackView> condition)
        implements ModifierModule, ProjectileLaunchModifierHook.NoShooter, ProjectileHitModifierHook,
        MeleeHitModifierHook, MonsterMeleeHitModifierHook, ConditionalModule<IToolStackView> {

    private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.defaultHooks(ModifierHooks.MELEE_HIT,
            ModifierHooks.MONSTER_MELEE_HIT, ModifierHooks.PROJECTILE_LAUNCH, ModifierHooks.PROJECTILE_SHOT,
            ModifierHooks.PROJECTILE_THROWN, ModifierHooks.PROJECTILE_HIT);
    public static final RecordLoadable<ConditionalHitModifierModuleModule> LOADER = RecordLoadable.create(
            LivingEntityPredicate.LOADER.nullableField("target", ConditionalHitModifierModuleModule::target),
            LivingEntityPredicate.LOADER.nullableField("attacker", ConditionalHitModifierModuleModule::attacker),
            ModifierModule.LOADER.requiredField("module", ConditionalHitModifierModuleModule::subModule),
            ModifierCondition.TOOL_FIELD,
            ConditionalHitModifierModuleModule::new);

    @Override
    public RecordLoadable<? extends ModifierModule> getLoader() {
        return LOADER;
    }

    @Override
    public List<ModuleHook<?>> getDefaultHooks() {
        return DEFAULT_HOOKS;
    }

    @Override
    public void onProjectileShoot(IToolStackView tool, ModifierEntry modifier, LivingEntity shooter, ItemStack ammo,
            Projectile projectile, AbstractArrow arrow, ModDataNBT persistentData, boolean primary) {
        if (subModule instanceof ProjectileShootModifierHook shoot
                && condition().matches(tool, modifier)
                && (this.attacker == null || shooter != null && this.attacker.matches(shooter))) {
            shoot.onProjectileShoot(tool, modifier, shooter, ammo, projectile, arrow, persistentData, primary);
        }
    }

    @Override
    public boolean onProjectileHitEntity(ModifierNBT modifiers, ModDataNBT persistentData, ModifierEntry modifier,
            Projectile projectile, EntityHitResult hit, @Nullable LivingEntity attacker, @Nullable LivingEntity target,
            boolean notBlocked) {
        return subModule instanceof ProjectileHitModifierHook hook
                && (this.attacker == null || attacker != null && this.attacker.matches(attacker))
                && (this.target == null || target != null && this.target.matches(target))
                && hook.onProjectileHitEntity(modifiers, persistentData, modifier,
                        projectile, hit, attacker, target, notBlocked);
    }

    @Override
    public boolean onProjectileHitsBlock(ModifierNBT modifiers, ModDataNBT persistentData, ModifierEntry modifier,
            Projectile projectile, BlockHitResult hit, @Nullable LivingEntity owner) {
        return subModule instanceof ProjectileHitModifierHook hook
                && (this.attacker == null || owner != null && this.attacker.matches(owner))
                && hook.onProjectileHitsBlock(modifiers, persistentData, modifier, projectile, hit, owner);
    }

    @Override
    public float beforeMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damage,
            float baseKnockback, float knockback) {
        return (subModule instanceof MeleeHitModifierHook hook && matches(tool, modifier, context))
                ? hook.beforeMeleeHit(tool, modifier, context, damage, baseKnockback, knockback)
                : knockback;
    }

    @Override
    public void afterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damageDealt) {
        if (subModule instanceof MeleeHitModifierHook hook && matches(tool, modifier, context)) {
            hook.afterMeleeHit(tool, modifier, context, damageDealt);
        }
    }

    @Override
    public void failedMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context,
            float damageAttempted) {
        if (subModule instanceof MeleeHitModifierHook hook && matches(tool, modifier, context)) {
            hook.failedMeleeHit(tool, modifier, context, damageAttempted);
        }
    }

    @Override
    public void onMonsterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damage) {
        if (subModule instanceof MonsterMeleeHitModifierHook hook && matches(tool, modifier, context)) {
            hook.onMonsterMeleeHit(tool, modifier, context, damage);
        }
    }

    private boolean matches(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context) {
        return condition().matches(tool, modifier)
                && (this.attacker == null || this.attacker.matches(context.getAttacker()))
                && (context.getTarget() instanceof LivingEntity living && this.target != null
                        ? this.target.matches(living)
                        : this.target == null);
    }

}
