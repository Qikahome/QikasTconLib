package qikahome.tconlib.client.render;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraftforge.client.model.BakedModelWrapper;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

public class BakedBlockModifierModel extends BakedModelWrapper<BakedModel> {

    public BakedBlockModifierModel(BakedModel originalModel) {
        super(originalModel);
    }

    public BakedModel resolve(IToolStackView tool, ModifierEntry entry)
    {
        return this;
    }
    /**
     * Gets the key to use for caching results from this modifier. Should uniquely
     * represent this tool state for the given modifier
     * For most models, this can be just the modifier itself
     * 
     * @param tool     Tool
     * @param modifier Modifier instance
     * @return Cache key for the given data, or null to not cache anything
     */
    @Nullable
    public Object getCacheKey(IToolStackView tool, ModifierEntry modifier) {
        return modifier == ModifierEntry.EMPTY ? null : modifier.getId();
    }

    @Nonnull
    public static BakedBlockModifierModel cast(BakedModel model) {
        if (model instanceof BakedBlockModifierModel blockModel) {
            return blockModel;
        }
        return new BakedBlockModifierModel(model);
    }
}
