package qikahome.tconlib.predicate;

import javax.annotation.Nullable;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import slimeknights.mantle.data.loadable.primitive.EnumLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.entity.LivingEntityPredicate;

/**
 * 判断实体是否有任何状态效果
 */
public enum HasAnyMobEffectPredicate implements LivingEntityPredicate {
    ANY(null),
    BENEFICIAL(MobEffectCategory.BENEFICIAL),
    HARMFUL(MobEffectCategory.HARMFUL),
    NEUTRAL(MobEffectCategory.NEUTRAL);

    public static final RecordLoadable<HasAnyMobEffectPredicate> LOADER = RecordLoadable.create(
            EnumLoadable.of(values()).defaultField("category", ANY, e -> e),
            e -> e);

    @Nullable
    public final MobEffectCategory category;

    HasAnyMobEffectPredicate(MobEffectCategory category) {
        this.category = category;
    }

    @Override
    public boolean matches(LivingEntity input) {
        return (this == ANY && !input.getActiveEffects().isEmpty())
                || input.getActiveEffects().stream().anyMatch(e -> e.getEffect().getCategory() == category);
    }

    @Override
    public RecordLoadable<HasAnyMobEffectPredicate> getLoader() {
        return LOADER;
    }
}
