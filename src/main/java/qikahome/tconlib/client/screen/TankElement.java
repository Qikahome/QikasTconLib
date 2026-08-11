package qikahome.tconlib.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.fluids.FluidStack;
import qikahome.autosizedgui.api.AttachPosition;
import qikahome.autosizedgui.api.ILayoutElement;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.screen.ElementScreen;
import slimeknights.mantle.fluid.tooltip.FluidTooltipHandler;
import slimeknights.tconstruct.library.client.GuiUtil;
import slimeknights.tconstruct.library.fluid.SimpleFluidTank;
import slimeknights.tconstruct.smeltery.client.screen.module.ClickableTankModule;
import slimeknights.tconstruct.smeltery.client.screen.module.GuiSmelteryTank;
import slimeknights.tconstruct.tools.menu.ToolContainerMenu;

/**
 * 罐子元素：横向流体条，直接使用匠魂官方 FLUID_TANK 纹理
 * （tool_inventory.png 256×256 中 (0,224) 起 176×14 区域），渲染与交互对齐官方 GuiTankModule：
 * 流体条模块 160×8 内嵌于纹理 (8,5) 偏移处，流体绘制在模块内 (1,1) 起、高 6、宽按比例。
 * <p>
 * 以 BOTTOM fixed 元素挂在玩家背包上方，不参与 flow 布局，避免打破工具槽的 uniform 网格。
 */
public class TankElement implements ILayoutElement, ClickableTankModule {
    /** 官方 FLUID_TANK 纹理尺寸 */
    public static final int WIDTH = 162;
    public static final int HEIGHT = 14;

    /** 官方 FLUID_TANK 纹理（tool_inventory.png 中 (0,224) 起） */
    private static final ResourceLocation TEXTURE = new ResourceLocation("tconstruct",
            "textures/gui/tool_inventory.png");
    private static final ElementScreen FLUID_TANK = new ElementScreen(TEXTURE, 7, 224, 162, 14, 256, 256);
    /** 官方流体条模块（GuiTankModule，160×8）相对元素左上角的偏移 */
    private static final int FLUID_X = 1;
    private static final int FLUID_Y = 5;
    /** 官方流体条模块尺寸 */
    private static final int MODULE_WIDTH = 160;
    private static final int MODULE_HEIGHT = 8;

    /** 容量为 0 时的 tooltip（沿用官方文案） */
    private static final Component NO_CAPACITY = Component
            .translatable(Mantle.makeDescriptionId("gui", "fluid.millibucket"), 0).withStyle(ChatFormatting.GRAY);

    private final SimpleFluidTank tank;
    private final AutoSizedToolContainerScreen screen;
    private int x, y;

    public TankElement(SimpleFluidTank tank, AutoSizedToolContainerScreen screen) {
        this.tank = tank;
        this.screen = screen;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
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
    public AttachPosition getAttachPosition() {
        // 固定在内容区底部、玩家背包上方，不参与 flow（保持工具槽 uniform 网格）
        return AttachPosition.BOTTOM;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        FluidStack fluid = tank.getFluid();

        // 罐子背景：官方 FLUID_TANK 纹理
        FLUID_TANK.draw(g, x, y);

        // 流体条：模块内 (1,1) 起、高 6、宽按比例（对齐官方 GuiTankModule.draw）
        int fluidWidth = getFluidWidth();
        if (getFluidWidth() > 0) {
            RenderSystem.enableBlend();
            // GuiUtil.renderTiledFluid 内部会自行加上 screen.leftPos/topPos（官方约定传相对 leftPos 的坐标），
            // 而本元素在 panel 的 translate(origin) 内用相对坐标渲染，二者叠加会双重偏移。
            // 传入时先减去 getGuiLeft()/getGuiTop()，抵消该内部偏移。
            int fluidX = x + FLUID_X - screen.getGuiLeft();
            int fluidY = y + FLUID_Y - screen.getGuiTop();
            GuiUtil.renderTiledFluid(g.pose(), screen, fluid, fluidX, fluidY, fluidWidth,
                    MODULE_HEIGHT, 100);
        }

        if (isModuleHovered(mx, my)) {
            if (isFluidHovered(mx)) {
                GuiUtil.renderHighlight(g, x + FLUID_X, y + FLUID_Y, fluidWidth, MODULE_HEIGHT);
            } else {
                GuiUtil.renderHighlight(g, x + FLUID_X + fluidWidth, y + FLUID_Y, MODULE_WIDTH - fluidWidth,
                        MODULE_HEIGHT);
            }
        }
    }

    public void renderTooltip(GuiGraphics g, int mx, int my) {
        FluidStack fluid = tank.getFluid();
        int capacity = tank.getCapacity();
        if (isModuleHovered(mx, my)) {
            // 悬停高亮：流体区/空区分别高亮（对齐官方 highlightHoveredFluid）
            final List<Component> tooltip;
            int amount = fluid.getAmount();
            if (isFluidHovered(mx)) {
                tooltip = FluidTooltipHandler.getFluidTooltip(fluid);
            } else {
                tooltip = new ArrayList<>();
                tooltip.add(GuiSmelteryTank.TOOLTIP_CAPACITY);
                if (capacity == 0) {
                    tooltip.add(NO_CAPACITY);
                } else {
                    FluidTooltipHandler.BUCKET_FORMATTER.accept(capacity, tooltip);
                    if (capacity != amount) {
                        tooltip.add(GuiSmelteryTank.TOOLTIP_AVAILABLE);
                        FluidTooltipHandler.BUCKET_FORMATTER.accept(capacity - amount, tooltip);
                    }
                }
            }
            g.renderComponentTooltip(Minecraft.getInstance().font, tooltip, mx, my);
        }
    }

    @Override
    public AbstractContainerMenu getMenu() {
        return screen.getMenu();
    }

    @Override
    public boolean isHovered(int mx, int my) {
        int rx = toRelX(mx);
        int ry = toRelY(my);
        // 交互区域：整个罐子元素矩形（四周扩 1px），保证点击纹理任意位置都能触发流体转移
        return rx >= x - 1 && ry >= y - 1 && rx < x + WIDTH + 1 && ry < y + HEIGHT + 1;
    }

    /** 视觉悬停区域：仅流体条模块（160×8，四周扩 1px），高亮/tooltip 用 */
    private boolean isModuleHovered(int mx, int my) {
        int rx = toRelX(mx);
        int ry = toRelY(my);
        int modX = x + FLUID_X;
        int modY = y + FLUID_Y;
        return rx >= modX - 1 && ry >= modY - 1 && rx < modX + MODULE_WIDTH + 1 && ry < modY + MODULE_HEIGHT + 1;
    }

    @Override
    public boolean isFluidHovered(int check) {
        return toRelX(check) - x - FLUID_X <= getFluidWidth();
    }

    /** 屏幕坐标 → 元素相对坐标（AutoSizedGUI 1.0.8+ 槽位坐标为相对 leftPos/topPos 的坐标） */
    private int toRelX(double absX) {
        return (int) absX - (screen.getGuiLeft() - 1);
    }

    private int toRelY(double absY) {
        return (int) absY - (screen.getGuiTop() - 1);
    }

    private int getFluidWidth() {
        FluidStack fluid = tank.getFluid();
        int capacity = tank.getCapacity();
        return capacity > 0 ? Math.min(MODULE_WIDTH * fluid.getAmount() / capacity, MODULE_WIDTH) : 0;
    }
}
