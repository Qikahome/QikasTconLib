package qikahome.tconlib.predicate;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.data.predicate.entity.LivingEntityPredicate;
import slimeknights.tconstruct.library.json.IntRange;
import slimeknights.tconstruct.library.json.TinkerLoadables;

/**
 * 判断实体所处位置的光照等级（0-15，闭区间）是否在指定范围内。
 * 光照计算：light_layer 缺省为 null 时取天空光与方块光两者最大值（同 vanilla getMaxLocalRawBrightness，
 * 天空光已减去天气昏暗）；指定 "sky"/"block" 时单独取对应光源。
 */
public record LightLevelPredicate(IntRange light, @Nullable LightLayer lightLayer) implements LivingEntityPredicate {

    /** JSON 缺省 light 时默认 0-15（任意亮度），light_layer 缺省为 null（取两者最大值） */
    public static final RecordLoadable<LightLevelPredicate> LOADER = RecordLoadable.create(
            new IntRange(0, 15).defaultField("light", LightLevelPredicate::light),
            TinkerLoadables.LIGHT_LAYER.nullableField("light_layer", LightLevelPredicate::lightLayer),
            LightLevelPredicate::new);

    /** 天空光减去天气昏暗（雨/雷），最低 0 */
    private static int getSkyLight(Level level, BlockPos pos) {
        return Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
    }

    /** 按光源层取亮度；lightLayer 为 null 时取两者最大值 */
    private static int getLightLevel(Level level, @Nullable LightLayer lightLayer, BlockPos pos) {
        if (lightLayer == LightLayer.SKY) {
            return getSkyLight(level, pos);
        }
        if (lightLayer == LightLayer.BLOCK) {
            return level.getBrightness(LightLayer.BLOCK, pos);
        }
        return Math.max(getSkyLight(level, pos), level.getBrightness(LightLayer.BLOCK, pos));
    }

    @Override
    public boolean matches(LivingEntity input) {
        return light.test(getLightLevel(input.level(), lightLayer, input.blockPosition()));
    }

    @Override
    public RecordLoadable<LightLevelPredicate> getLoader() {
        return LOADER;
    }
}
