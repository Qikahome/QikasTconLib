package qikahome.tconlib.plugin.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import qikahome.tconlib.TconLib;

/** QikasTconLib 的 JEI 集成：为放置工具菜单注册合成转移 */
@JeiPlugin
public class JEIPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return TconLib.getResource("jei_plugin");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new PlacedToolTransferInfo(registration.getTransferHelper()), RecipeTypes.CRAFTING);
    }
}
