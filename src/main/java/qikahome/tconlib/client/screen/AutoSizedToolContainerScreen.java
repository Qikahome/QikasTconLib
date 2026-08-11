package qikahome.tconlib.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import qikahome.autosizedgui.api.ILayoutElement;
import qikahome.autosizedgui.screen.AutoSizedContainerScreen;
import qikahome.autosizedgui.screen.element.ItemSlot;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.tools.capability.inventory.ToolInventoryCapability;
import slimeknights.tconstruct.library.tools.capability.inventory.ToolInventoryCapability.InventoryModifierHook;
import slimeknights.tconstruct.library.tools.layout.Patterns;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.tools.menu.ReadOnlySlot;
import slimeknights.tconstruct.tools.menu.ToolContainerMenu;

/**
 * 工具容器屏幕（AutoSizedGUI 自动布局版）。
 * <p>
 * 菜单参数取 {@link ToolContainerMenu} 基类，可同时服务于本模组的
 * {@link qikahome.tconlib.placeabletool.PlacedToolContainerMenu} 与匠魂原生的
 * {@link ToolContainerMenu}（MenuScreens.register 的 M 允许取超类），
 * 由 {@code enablePlacedToolAutoScreen} 配置切换。
 */
public class AutoSizedToolContainerScreen extends AutoSizedContainerScreen<ToolContainerMenu> {

    public AutoSizedToolContainerScreen(ToolContainerMenu menu, Inventory inventory, Component title) {
        // 容器部分 = 合成区 + 工具槽（playerInventoryStart 之前的所有槽），剩余 36 格为玩家背包
        super(menu, inventory, title, menu.getPlayerInventoryStart());
    }

    /** 罐子元素（菜单带储罐时创建），点击倒/抽流体用 */
    private TankElement tank;

    @Override
    protected void populatePanel() {
        addBackground();
        int index = 0;
        int craftingHeight = menu.getCraftingHeight();
        if (craftingHeight != 0) {
            ILayoutElement result = getSlotWrapper(menu.getSlot(0));
            List<List<ILayoutElement>> inputs = new ArrayList();
            index = 1;
            for (int i = 0; i < craftingHeight; i++) {
                List<ILayoutElement> column = new ArrayList();
                for (int j = 0; j < craftingHeight; j++) {
                    column.add(getSlotWrapper(menu.getSlot(index)));
                    index++;
                }
                inputs.add(column);
            }
            panel.addElement(new CraftingElement(craftingHeight == 2, inputs, result));
        }
        addTitle();
        for (int i = index; i < this.containerSize; ++i) {
            panel.addElement((ILayoutElement) this.menu.slots.get(i));
        }
        if (menu.getTank().getCapacity() > 0) {
            tank = new TankElement(menu.getTank(), this);
            panel.addElement(tank);
        }
        addPlayerInventory();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        assert minecraft != null && minecraft.player != null && minecraft.gameMode != null;
        if (tank != null && (button == 0 || button == 1) && !menu.getCarried().isEmpty()
                && !minecraft.player.isSpectator()) {
            if (tank.tryClick((int) mouseX, (int) mouseY, button, 0)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mx, int my) {
        super.renderTooltip(g, mx, my);
        if (tank != null)
            tank.renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(guiGraphics, partialTick, mouseX, mouseY);
        // AutoSizedGUI 1.0.8+ 槽位坐标为相对 (leftPos, topPos) 的坐标，
        // 与物品图标一致需在此手动平移，否则 pattern/高亮会错位
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 0F);
        // 玩家背包区的只读槽（工具所在槽位）由 PlayerInventory 包裹，其 render 只画整块背景、
        // 不调用槽位元素渲染，需在此单独绘制选中高亮纹理（对齐官方 ToolContainerScreen 的 SELECTED_X 高亮）
        for (int i = menu.getPlayerInventoryStart(); i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot instanceof ReadOnlyItemSlot readOnly) {
                readOnly.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
        // 工具槽 pattern 图标（空槽时由 modifier 提供提示图标，对齐官方 ToolContainerScreen.renderBg）
        assert minecraft != null;
        Function<ResourceLocation, TextureAtlasSprite> spriteGetter = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        int start = menu.getToolInventoryStart();
        int maxSlots = menu.slots.size();
        IToolStackView tool = menu.getTool();
        List<ModifierEntry> modifiers = tool.getModifierList();
        modifiers:
        for (int modIndex = modifiers.size() - 1; modIndex >= 0; modIndex--) {
            ModifierEntry entry = modifiers.get(modIndex);
            InventoryModifierHook inventory = entry.getHook(ToolInventoryCapability.HOOK);
            int size = inventory.getSlots(tool, entry);
            for (int i = 0; i < size; i++) {
                if (start + i >= maxSlots) {
                    break modifiers;
                }
                Slot slot = menu.getSlot(start + i);
                Pattern pattern = inventory.getPattern(tool, entry, i, slot.hasItem());
                if (pattern != null) {
                    TextureAtlasSprite sprite = spriteGetter.apply(pattern.getTexture());
                    guiGraphics.blit(slot.x + 1, slot.y + 1, 100, 16, 16, sprite);
                }
            }
            start += size;
        }
        // 副手槽空槽盾牌图标（对齐官方 ToolContainerScreen.renderBg）
        if (menu.isShowOffhand()) {
            Slot slot = menu.getSlot(menu.getPlayerInventoryStart() - 1);
            if (!slot.hasItem()) {
                TextureAtlasSprite sprite = spriteGetter.apply(Patterns.SHIELD.getTexture());
                guiGraphics.blit(slot.x, slot.y, 100, 16, 16, sprite);
            }
        }
        guiGraphics.pose().popPose();
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int index, ClickType type) {
        // 禁止数字键快捷交换到持有工具的槽位（对齐官方 ToolContainerScreen.slotClicked；
        // 放置场景 getSlotIndex() 返回 MAX_VALUE 自动短路）
        if (type == ClickType.SWAP && slot.container == menu.getPlayer().getInventory()
                && slot.getSlotIndex() == menu.getSlotIndex()) {
            return;
        }
        super.slotClicked(slot, slotId, index, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <S extends Slot & ILayoutElement> S getSlotWrapper(Slot slot) {
        // 只读槽（副手/背包工具槽）必须保留 ReadOnlySlot 类型：
        // 官方 JEI 转移靠 instanceof ReadOnlySlot 排除这些槽，包装成普通 ItemSlot 会被误纳入转移槽
        if (slot instanceof ReadOnlySlot) {
            return (S) new ReadOnlyItemSlot(slot);
        }
        return (S) (slot instanceof ItemSlot is ? is : new DelegatingItemSlot(slot));
    }

    /**
     * 只读槽布局包装：保持 {@link ReadOnlySlot} 身份（JEI 转移排除 + 只读语义），
     * 同时作为 AutoSizedGUI 布局元素渲染。
     */
    private static final class ReadOnlyItemSlot extends ReadOnlySlot implements ILayoutElement {
        private static final ResourceLocation TEXTURE = TConstruct.getResource("textures/gui/tool_inventory.png");

        private ReadOnlyItemSlot(Slot delegate) {
            super(delegate.container, delegate.getContainerSlot(), delegate.x, delegate.y);
            // 保持菜单槽位索引（替换 menu.slots 时不经过 addSlot 重设）
            this.index = delegate.index;
        }

        @Override
        public int getWidth() {
            return 18;
        }

        @Override
        public int getHeight() {
            return 18;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public void setPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            // 官方选中高亮纹理（只读槽背景）
            g.blit(TEXTURE, x - 2, y - 2, 176, 0, 20, 20);
        }
    }

    /** 转发所有行为到原始槽的 ItemSlot 包装 */
    private static class DelegatingItemSlot extends ItemSlot {
        private final Slot delegate;

        private DelegatingItemSlot(Slot delegate) {
            super(delegate.container, delegate.getContainerSlot(), delegate.index);
            this.delegate = delegate;
        }

        @Override
        public ItemStack getItem() {
            return delegate.getItem();
        }

        @Override
        public boolean hasItem() {
            return delegate.hasItem();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return delegate.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return delegate.mayPickup(player);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            delegate.onTake(player, stack);
        }

        @Override
        public void onQuickCraft(ItemStack oldStack, ItemStack newStack) {
            delegate.onQuickCraft(oldStack, newStack);
        }

        @Override
        public void set(ItemStack stack) {
            delegate.set(stack);
        }

        @Override
        public void setChanged() {
            delegate.setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return delegate.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return delegate.getMaxStackSize(stack);
        }
    }
}
