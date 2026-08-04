package qikahome.tconlib.placeabletool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.EmptyHandler;
import qikahome.tconlib.TconLib;
import qikahome.tconlib.placeabletool.PlacedToolBlock.PlacedToolBlockEntity;
import slimeknights.tconstruct.tools.menu.ToolContainerMenu;

/**
 * 放置工具的物品栏菜单。
 * <p>
 * 继承 TCon 的 ToolContainerMenu，复用其 tank 实时同步
 * （{@link slimeknights.tconstruct.tools.network.ToolContainerFluidUpdatePacket}）、
 * 罐子交互（clickMenuButton）与物品转移（quickMoveStack）逻辑。
 * 与 TCon 的差异：工具来自方块实体，而非玩家背包。
 */
public class PlacedToolContainerMenu extends ToolContainerMenu {
    private final BlockPos pos;
    private final PlacedToolBlockEntity be;

    /**
     * 服务端构造：工具与物品栏直接来自方块实体。
     */
    public PlacedToolContainerMenu(int id, Inventory playerInventory, BlockPos pos) {
        this(id, playerInventory, pos, getBlockEntity(playerInventory.player, pos));
    }

    private PlacedToolContainerMenu(int id, Inventory playerInventory, BlockPos pos, PlacedToolBlockEntity be) {
        super(TconLib.PLACED_TOOL_MENU.get(), id, playerInventory,
                be != null ? be.getStack() : ItemStack.EMPTY,
                be != null ? be.getHandler() : EmptyHandler.INSTANCE,
                -1); // slotIndex 无效值（工具在方块实体中，不在玩家背包，stillValid 已重写）
        this.pos = pos;
        this.be = be;
        // 服务端：登记为查看者，外部写入流体时同步到本玩家
        if (be != null && playerInventory.player instanceof ServerPlayer serverPlayer) {
            be.addViewer(serverPlayer);
        }
    }

    /**
     * 客户端构造：额外数据为 BlockPos + 完整工具 stack。
     */
    public PlacedToolContainerMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), buf.readItem());
    }

    private PlacedToolContainerMenu(int id, Inventory playerInventory, BlockPos pos, ItemStack stack) {
        super(TconLib.PLACED_TOOL_MENU.get(), id, playerInventory, stack,
                stack.getCapability(ForgeCapabilities.ITEM_HANDLER)
                        .filter(cap -> cap instanceof IItemHandlerModifiable)
                        .orElse(EmptyHandler.INSTANCE),
                -1);
        this.pos = pos;
        this.be = getBlockEntity(playerInventory.player, pos);
    }

    private static PlacedToolBlockEntity getBlockEntity(Player player, BlockPos pos) {
        if (player == null || player.level() == null)
            return null;
        if (player.level().getBlockEntity(pos) instanceof PlacedToolBlockEntity ptbe)
            return ptbe;
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        // 方块还在原位且玩家在 8 格内才允许继续打开
        return be != null && player.level().getBlockEntity(pos) == be
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64.0;
    }

    @Override
    public int getSlotIndex() {
        // 工具在方块实体中而非玩家背包，返回无效值使 ToolContainerScreen 的
        // 选中槽高亮逻辑（slotIndex < 9 / < INVENTORY_SIZE / == SLOT_OFFHAND）全部短路
        return Integer.MAX_VALUE;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // 工具 NBT 可能在交互中被修改，关闭菜单时确保方块实体持久化
        if (be != null) {
            be.setChanged();
            // 取消登记，停止外部流体同步
            if (player instanceof ServerPlayer serverPlayer) {
                be.removeViewer(serverPlayer);
            }
        }
    }
}
