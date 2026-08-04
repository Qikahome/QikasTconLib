package qikahome.tconlib.plugin.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import qikahome.tconlib.TconLib;
import qikahome.tconlib.placeabletool.PlacedToolContainerMenu;
import slimeknights.tconstruct.tools.menu.ReadOnlySlot;

/**
 * 放置工具菜单的 JEI 合成转移。
 * <p>
 * 官方 {@code ToolInventoryTransferInfo} 通过 {@code ToolContainerMenu.class} + 官方 MenuType 匹配，
 * 而 JEI 按 {@code container.getClass()} 精确查表且要求 MenuType 相等，因此对使用自定义
 * {@code PLACED_TOOL_MENU} 的 {@link PlacedToolContainerMenu} 不生效。这里复制官方逻辑，改绑本模组菜单。
 */
public class PlacedToolTransferInfo
        implements IRecipeTransferHandler<PlacedToolContainerMenu, CraftingRecipe>,
        IRecipeTransferInfo<PlacedToolContainerMenu, CraftingRecipe> {
    /** 2x2 合成网格能承接的配方输入索引（截掉右边和下边后） */
    private static final IntSet PLAYER_INV_INDEXES = IntArraySet.of(0, 1, 3, 4);

    private final IRecipeTransferHandlerHelper handlerHelper;
    private final IRecipeTransferHandler<PlacedToolContainerMenu, CraftingRecipe> handler;

    public PlacedToolTransferInfo(IRecipeTransferHandlerHelper handlerHelper) {
        this.handlerHelper = handlerHelper;
        this.handler = handlerHelper.createUnregisteredRecipeTransferHandler(this);
    }

    @Override
    public Class<? extends PlacedToolContainerMenu> getContainerClass() {
        return PlacedToolContainerMenu.class;
    }

    @Override
    public Optional<MenuType<PlacedToolContainerMenu>> getMenuType() {
        return Optional.of(TconLib.PLACED_TOOL_MENU.get());
    }

    @Override
    public RecipeType<CraftingRecipe> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(PlacedToolContainerMenu container, CraftingRecipe recipe,
            IRecipeSlotsView recipeSlotsView, Player player, boolean maxTransfer, boolean doTransfer) {
        // 没有合成槽时无事可做
        int slots = container.getToolInventoryStart() - 1;
        if (slots <= 0) {
            return handlerHelper.createInternalError();
        }
        // 需要服务端配合
        if (!handlerHelper.recipeTransferHasServerSupport()) {
            Component tooltipMessage = Component.translatable("jei.tooltip.error.recipe.transfer.no.server");
            return handlerHelper.createUserErrorWithTooltip(tooltipMessage);
        }
        // 完整 3x3 网格直接走默认转移
        if (slots >= 9) {
            return handler.transferRecipe(container, recipe, recipeSlotsView, player, maxTransfer, doTransfer);
        }
        // 小网格：配方必须能放进 2x2
        List<IRecipeSlotView> slotViews = recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT);
        if (!validateIngredientsOutsidePlayerGridAreEmpty(slotViews)) {
            Component tooltipMessage = Component
                    .translatable("jei.tooltip.error.recipe.transfer.too.large.player.inventory");
            return this.handlerHelper.createUserErrorWithTooltip(tooltipMessage);
        }
        // 过滤出 2x2 网格对应的配方输入槽
        List<IRecipeSlotView> filteredSlotViews = filterSlots(slotViews);
        IRecipeSlotsView filteredRecipeSlots = this.handlerHelper.createRecipeSlotsView(filteredSlotViews);
        return this.handler.transferRecipe(container, recipe, filteredRecipeSlots, player, maxTransfer, doTransfer);
    }

    @Override
    public boolean canHandle(PlacedToolContainerMenu container, CraftingRecipe recipe) {
        return true;
    }

    @Override
    public List<Slot> getInventorySlots(PlacedToolContainerMenu container, CraftingRecipe recipe) {
        List<Slot> slots = new ArrayList<>();
        for (int i = container.getToolInventoryStart(); i < container.slots.size(); i++) {
            Slot slot = container.getSlot(i);
            if (!(slot instanceof ReadOnlySlot)) {
                slots.add(container.getSlot(i));
            }
        }
        return slots;
    }

    @Override
    public List<Slot> getRecipeSlots(PlacedToolContainerMenu container, CraftingRecipe recipe) {
        // 开合成区时 index 0 是输出槽，1 起是 2x2/3x3 网格
        List<Slot> slots = new ArrayList<>();
        for (int i = 1; i < container.getToolInventoryStart(); i++) {
            slots.add(container.getSlot(i));
        }
        return slots;
    }

    /** 确保配方输入都能放进 2x2 网格 */
    private static boolean validateIngredientsOutsidePlayerGridAreEmpty(List<IRecipeSlotView> slotViews) {
        int bound = slotViews.size();
        for (int i = 0; i < bound; i++) {
            if (!PLAYER_INV_INDEXES.contains(i)) {
                IRecipeSlotView slotView = slotViews.get(i);
                if (!slotView.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 只保留 2x2 网格槽位 */
    private static List<IRecipeSlotView> filterSlots(List<IRecipeSlotView> slotViews) {
        return PLAYER_INV_INDEXES.intStream().mapToObj(slotViews::get).toList();
    }
}
