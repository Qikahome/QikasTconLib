package qikahome.tconlib.client.screen;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import qikahome.autosizedgui.api.AttachPosition;
import qikahome.autosizedgui.api.ILayoutElement;
import slimeknights.mantle.client.screen.ElementScreen;
import slimeknights.tconstruct.TConstruct;

/**
 * 3x3 合成区域元素：把 9 个槽位排成 3x3 并渲染背景与合成箭头。
 * <p>
 * 需要先在菜单中注册对应数量的合成槽，再把这些槽的 ItemSlot 引用传入。
 */
public class CraftingElement implements ILayoutElement {
    private static final int[] ROWS = { -3, 15, 33 };
    private static final int SLOT_SIZE = 18;
    /** The ResourceLocation containing the chest GUI texture. */
    private static final ResourceLocation TEXTURE = TConstruct.getResource("textures/gui/tool_inventory.png");
    /** Slot background for 3x3 crafting grid */
    private static final ElementScreen CRAFTING_SLOTS = new ElementScreen(TEXTURE, 176, 74, 54, 54, 256, 256);
    /** Result slot for 3x3 crafting grid */
    private static final ElementScreen CRAFTING_RESULT = CRAFTING_SLOTS.move(176, 20, 62, 54);
    /** Full 2x2 crafting grid */
    private static final ElementScreen INVENTORY_CRAFTING = CRAFTING_SLOTS.move(176, 128, 74, 36);
    private final boolean inv;
    private final List<List<ILayoutElement>> inputs;//column lists
    private final ILayoutElement output;
    private int x, y;

    public CraftingElement(boolean inv, List<List<ILayoutElement>> inputs, ILayoutElement output) {
        this.inv = inv;
        this.inputs = inputs;
        this.output = output;
    }

    @Override
    public int getWidth() {
        return inv ? 74 : 54 + 62;
    }

    @Override
    public int getHeight() {
        return inv ? 36 : 54;
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
        int grid = inv ? 2 : 3;
        for (int i = 0; i < grid; i++)
            for (int j = 0; j < grid; j++)
                inputs.get(j).get(i).setPosition(x + i * SLOT_SIZE, y + ROWS[j]);
        if (inv)
            output.setPosition(x + 56, y + 7);
        else
            output.setPosition(x + 54 + 40, y + 15);
    }

    @Override
    public AttachPosition getAttachPosition() {
        return AttachPosition.TOP;
    }

    public int getPriority() {
        return Integer.MAX_VALUE / 2 + 100;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (inv) {
            INVENTORY_CRAFTING.draw(g, x, y - 3);
        } else {
            CRAFTING_SLOTS.draw(g, x, y - 3);
            CRAFTING_RESULT.draw(g, x + 54, y - 3);
        }
    }
}
