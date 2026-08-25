package qikahome.tconlib.placeabletool;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.EmptyHandler;
import net.minecraftforge.network.NetworkHooks;
import qikahome.tconlib.TconLib;
import qikahome.tconlib.placeabletool.ToolPlacementDataManager.PlacementData;
import slimeknights.mantle.fluid.FluidTransferHelper;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolTankHelper;
import slimeknights.tconstruct.library.tools.capability.inventory.ToolInventoryCapability;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity.ITankBlock;
import slimeknights.tconstruct.tools.network.ToolContainerFluidUpdatePacket;

public class PlacedToolBlock extends BaseEntityBlock implements SimpleWaterloggedBlock, LiquidBlockContainer {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** 发光等级（0-15）：由工具的 PLACED_TOOL_LIGHT_HOOK 计算并写入，供光照引擎查询 */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);

    public PlacedToolBlock(Properties p_49224_) {
        super(p_49224_);
    }

    /**
     * 物品贴面的方向（物品所在的那一侧），统一为 FACING 的反方向：
     * facing=west 贴东墙（物品在东侧）；facing=up 贴地板（物品在方块下方）。
     */
    public static Direction getItemFacing(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    /**
     * 挖掘进度按工具放置数据的 hardness 计算（0 = 瞬间挖掉，如火把；默认 0.5）。
     * Block.hardness 是方块级固定值，无法按放置的工具区分，故覆写此方法。
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        float hardness = 0.5F;
        if (level.getBlockEntity(pos) instanceof IToolBlockEntity ptbe) {
            hardness = ToolPlacementDataManager.INSTANCE.get(ptbe.getStack()).hardness();
        }
        if (hardness <= 0.0F) {
            // 0 硬度 = 第一 tick 直接破坏（原版 TNT 等瞬时方块同理）
            return 1.0F;
        }
        int i = ForgeHooks.isCorrectToolForDrops(state, player) ? 30 : 100;
        return player.getDigSpeed(state, pos) / hardness / (float) i;
    }

    /** 按工具查放置数据；BE 缺失（如客户端尚未同步）时回退默认 */
    private PlacementData getPlacementData(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PlacedToolBlockEntity ptbe) {
            return ptbe.getPlacementData();
        }
        return PlacementData.DEFAULT;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 形状键是物品朝向
        return getPlacementData(level, pos).getShape(getItemFacing(state));
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getPlacementData(level, pos).getInteractionShape(getItemFacing(state));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getPlacementData(level, pos).getCollisionShape(getItemFacing(state));
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        // 不遮挡任何相邻方块的面：始终返回空形状，相邻面永不剔除
        return Shapes.empty();
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos p_153215_, BlockState p_153216_) {
        return new PlacedToolBlockEntity(TconLib.PLACED_TOOL_ENTITY.get(), p_153215_, p_153216_);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        // 仅服务端需要：节流窗口结束后补发流体同步
        return level.isClientSide ? null
                : createTickerHelper(type, TconLib.PLACED_TOOL_ENTITY.get(),
                        PlacedToolBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof IToolBlockEntity ptbe) {
            ToolStack tool = ToolStack.from(ptbe.getStack());
            // 内建逻辑（流体交互/打开物品栏）之前：模块可短路自定义交互
            for (ModifierEntry entry : tool.getModifierList()) {
                InteractionResult result = entry.getHook(TconLib.PLACED_TOOL_INTERACTION_HOOK).before(tool, entry,
                        state, level, pos, player, hand, hit, ptbe);
                if (result.consumesAction()) {
                    return result;
                }
            }
            if (FluidTransferHelper.interactWithTank(level, pos, player, hand, hit)) {
                return InteractionResult.SUCCESS;
            }
            if (!player.isShiftKeyDown() && (ptbe.getHandler().getSlots() > 0
                    || ModifierUtil.checkVolatileFlag(ptbe.getStack(), ToolInventoryCapability.CRAFTING_TABLE)
                    || ModifierUtil.checkVolatileFlag(ptbe.getStack(),
                            ToolInventoryCapability.INVENTORY_CRAFTING))) {
                if (player instanceof ServerPlayer serverPlayer && ptbe instanceof MenuProvider menuProvider)
                    NetworkHooks.openScreen(serverPlayer, menuProvider, buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeItemStack(ptbe.getStack(), false);
                    });
                return InteractionResult.CONSUME;
            }
            // 内建逻辑未处理（流体交互失败且未打开容器）时：模块可兜底处理
            for (ModifierEntry entry : tool.getModifierList()) {
                InteractionResult hookResult = entry.getHook(TconLib.PLACED_TOOL_INTERACTION_HOOK).after(tool, entry,
                        state, level, pos, player, hand, hit, ptbe);
                if (hookResult.consumesAction()) {
                    return hookResult;
                }
            }
            return InteractionResult.PASS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState,
            boolean isMoving) {
        // 含水状态变化（同一方块类型的属性变化）也走 onRemove：通知 modifier
        if (!level.isClientSide && oldState.getBlock() == newState.getBlock()
                && oldState.getValue(WATERLOGGED) != newState.getValue(WATERLOGGED)
                && level.getBlockEntity(pos) instanceof IToolBlockEntity ptbe) {
            ToolStack tool = ToolStack.from(ptbe.getStack());
            boolean waterlogged = newState.getValue(WATERLOGGED);
            for (ModifierEntry entry : tool.getModifierList()) {
                entry.getHook(TconLib.PLACED_TOOL_FLUID_STATE_CHANGE_HOOK)
                        .onWaterloggedChanged(tool, entry, newState, level, pos, waterlogged);
            }
        }
        if (!oldState.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IToolBlockEntity ptbe && !ptbe.getStack().isEmpty()) {
                ItemEntity itementity = new ItemEntity(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                        ptbe.getStack().copy());
                itementity.setDeltaMovement(level.random.triangle(0.0D, 0.11485000171139836D),
                        level.random.triangle(0.2D, 0.11485000171139836D),
                        level.random.triangle(0.0D, 0.11485000171139836D));
                level.addFreshEntity(itementity);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(oldState, level, pos, newState, isMoving);
        }
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 放置逻辑由工具的 PlacementData 决定（如四向/六向/拒绝放置）；
        // 未定义该工具时按 DEFAULT 放置；返回 null 时 PlacingModule 会拒绝放置
        ItemStack tool = context.getItemInHand();
        PlacementData data = ToolPlacementDataManager.INSTANCE.getData(tool);
        return (data != null ? data : PlacementData.DEFAULT).getStateForPlacement(context, tool);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        // 活塞推动时销毁并掉落工具（走 onRemove），避免 BE 跟随移动导致工具栈丢失
        return PushReaction.DESTROY;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        // 水浸状态正确但没渲染水，是因为没告诉渲染器这里的水：返回非空 FluidState 才会渲染水面/水流
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        // 可被水冲掉的工具：拒绝任何液体放入（桶倒水/含水）；自然流动由 MixinFlowingFluid 放行并破坏
        if (level instanceof Level lv && level.getBlockEntity(pos) instanceof IToolBlockEntity ptbe
                && canWashAway(lv, pos, state, ptbe.getStack())) {
            return false;
        }
        return SimpleWaterloggedBlock.super.canPlaceLiquid(level, pos, state, fluid);
    }

    /** 查询工具是否允许被水冲掉（PLACED_TOOL_FLUID_STATE_CHANGE_HOOK.canBeWashedAway 任一 modifier 返回 true） */
    public static boolean canWashAway(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        ToolStack toolStack = ToolStack.from(tool);
        for (ModifierEntry entry : toolStack.getModifierList()) {
            if (entry.getHook(TconLib.PLACED_TOOL_FLUID_STATE_CHANGE_HOOK)
                    .canBeWashedAway(toolStack, entry, state, level, pos, level.getFluidState(pos))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        // 相邻方块变化时给水调度计划刻，水才能正常流动/排出（含水方块的标配，同蜡烛等）
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        // 需要支撑的工具：支撑方向被破坏时计划销毁（走 onRemove 掉落工具）
        if (level.getBlockEntity(pos) instanceof IToolBlockEntity ptbe) {
            PlacementData data = ToolPlacementDataManager.INSTANCE.get(ptbe.getStack());
            if (data.getSupportDirection(state) == direction && !data.isSupported(level, pos, state)) {
                level.scheduleTick(pos, this, 1);
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!super.canSurvive(state, level, pos)) {
            return false;
        }
        // 需要支撑的工具：按放置数据校验支撑（如贴墙工具在墙被拆掉后失效）
        if (level.getBlockEntity(pos) instanceof IToolBlockEntity ptbe) {
            return ToolPlacementDataManager.INSTANCE.get(ptbe.getStack()).isSupported(level, pos, state);
        }
        return true;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // updateShape 计划销毁后在此执行：支撑仍缺失则销毁并掉落工具
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, LIGHT);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LIGHT);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PlacedToolBlockEntity ptbe) {
            return AbstractContainerMenu.getRedstoneSignalFromContainer(ptbe);
        }
        return 0;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof IToolBlockEntity ptbe) {
            return ptbe.getStack().copy();
        }
        return ItemStack.EMPTY;
    }

    public static class PlacedToolBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, Nameable, IToolBlockEntity {
        public PlacedToolBlockEntity(BlockEntityType<?> p_155228_, BlockPos p_155229_, BlockState p_155230_) {
            super(p_155228_, p_155229_, p_155230_);
        }

        public PlacedToolBlockEntity(BlockPos p_155229_, BlockState p_155230_) {
            this(TconLib.PLACED_TOOL_ENTITY.get(), p_155229_, p_155230_);
        }

        public PlacedToolBlockEntity(BlockPos p_155229_, BlockState p_155230_, ItemStack stackIn) {
            this(TconLib.PLACED_TOOL_ENTITY.get(), p_155229_, p_155230_);
            stack = stackIn;
        }

        private net.minecraftforge.common.util.LazyOptional<IFluidHandlerItem> fluidHandler;
        private net.minecraftforge.common.util.LazyOptional<IFluidHandlerItem> syncingFluidHandler;
        /** 打开着本方块菜单的玩家，外部写入流体时通知他们刷新 tank 显示 */
        private final Set<ServerPlayer> viewers = new HashSet<>();
        /** 非满/非空状态下，流体同步的最小间隔（tick） */
        private static final int FLUID_SYNC_INTERVAL = 5;
        private long lastSyncTick = Long.MIN_VALUE;
        /** 节流期内又有新的流体变化时置位，由 serverTick 在窗口结束后补发最后一次 */
        private boolean pendingSync = false;

        @Override
        public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable Direction side) {
            if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER) {
                if (this.fluidHandler == null) {
                    this.fluidHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
                    // 包装代理：外部（管道等）写入/抽取流体后，主动同步给已打开菜单的客户端
                    this.syncingFluidHandler = this.fluidHandler.isPresent()
                            ? net.minecraftforge.common.util.LazyOptional.of(() -> new SyncingFluidHandler(
                                    this.fluidHandler.orElseThrow(
                                            () -> new IllegalStateException("missing tool fluid handler"))))
                            : net.minecraftforge.common.util.LazyOptional.empty();
                }
                return this.syncingFluidHandler.cast();
            }
            return stack.getCapability(cap, side);
        }

        public void addViewer(ServerPlayer player) {
            viewers.add(player);
        }

        public void removeViewer(ServerPlayer player) {
            viewers.remove(player);
        }

        /** 外部写入流体后，把最新 tank 状态推送给所有打开菜单的玩家，并同步工具 stack 到客户端 BE */
        private void syncFluid() {
            if (level == null || level.isClientSide) {
                return;
            }
            long tick = level.getGameTime();
            // 容器满/空属于状态突变，立即同步；其余情况按间隔节流
            if (isFullOrEmpty() || tick - lastSyncTick >= FLUID_SYNC_INTERVAL) {
                flushSync(tick);
            } else {
                // 节流期内：记一笔，由 serverTick 在窗口结束后补发（保证最后一次变化不丢）
                pendingSync = true;
            }
        }

        private void flushSync(long tick) {
            lastSyncTick = tick;
            pendingSync = false;
            // 流体变化可能影响发光（TankLightModule），先重算 LIGHT 属性
            updateLight();
            // 流体存在工具 NBT 中，先让 BE 数据包携带最新 stack 到客户端
            BlockState state = this.level.getBlockState(this.worldPosition);
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
            // 已打开菜单的玩家：额外推送 TCon 的 tank 更新包（实时刷新屏幕上的 tank）
            FluidStack fluid = ToolTankHelper.TANK_HELPER.getFluid(getTool());
            for (ServerPlayer player : viewers) {
                TinkerNetwork.getInstance().sendTo(new ToolContainerFluidUpdatePacket(fluid), player);
            }
        }

        /** 所有 tank 全空或全满时为 true，需要立即同步（状态突变） */
        private boolean isFullOrEmpty() {
            if (this.fluidHandler == null || !this.fluidHandler.isPresent()) {
                return false;
            }
            IFluidHandlerItem handler = this.fluidHandler.orElseThrow(IllegalStateException::new);
            int tanks = handler.getTanks();
            if (tanks == 0) {
                return false;
            }
            boolean allEmpty = true;
            boolean allFull = true;
            for (int i = 0; i < tanks; i++) {
                int amount = handler.getFluidInTank(i).getAmount();
                if (amount > 0) {
                    allEmpty = false;
                }
                if (amount < handler.getTankCapacity(i)) {
                    allFull = false;
                }
            }
            return allEmpty || allFull;
        }

        /** 服务端每 tick：若节流期内有变化，窗口结束后补发一次；并驱动工具 tick 钩子 */
        public static void serverTick(Level level, BlockPos pos, BlockState state, PlacedToolBlockEntity be) {
            if (be.pendingSync) {
                be.flushSync(level.getGameTime());
            }
            // tick 钩子：模块可在方块存在期间每 tick 工作
            ToolStack tool = ToolStack.from(be.getStack());
            for (ModifierEntry entry : tool.getModifierList()) {
                entry.getHook(TconLib.PLACED_TOOL_TICK_HOOK).onTick(tool, entry, state, level, pos, be);
            }
        }

        /** 转发工具的流体能力，fill/drain 执行成功后触发一次同步 */
        private class SyncingFluidHandler implements IFluidHandlerItem {
            private final IFluidHandlerItem delegate;

            private SyncingFluidHandler(IFluidHandlerItem delegate) {
                this.delegate = delegate;
            }

            @Override
            public int getTanks() {
                return delegate.getTanks();
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return delegate.getFluidInTank(tank);
            }

            @Override
            public int getTankCapacity(int tank) {
                return delegate.getTankCapacity(tank);
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return delegate.isFluidValid(tank, stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                int filled = delegate.fill(resource, action);
                if (action.execute() && filled > 0) {
                    syncFluid();
                }
                return filled;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                FluidStack drained = delegate.drain(resource, action);
                if (action.execute() && !drained.isEmpty()) {
                    syncFluid();
                }
                return drained;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                FluidStack drained = delegate.drain(maxDrain, action);
                if (action.execute() && !drained.isEmpty()) {
                    syncFluid();
                }
                return drained;
            }

            @Override
            public ItemStack getContainer() {
                return delegate.getContainer();
            }
        }

        public void setStack(ItemStack stack) {
            this.stack = stack;
            this.cachedTool = null;
            this.cachedHandler = null;
            this.cachedPlacementData = null;
            this.fluidHandler = null;
            this.syncingFluidHandler = null;
            this.pendingSync = false;
            this.lastSyncTick = Long.MIN_VALUE;
            this.setChanged();
            // 服务端变更后主动推送数据包到客户端（否则客户端 BE 一直是初始空数据）
            if (this.level != null && !this.level.isClientSide) {
                // 工具更换可能改变发光等级（modifier 携带的发光模块），重算 LIGHT 属性
                updateLight();
                BlockState state = this.level.getBlockState(this.worldPosition);
                this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
            }
        }

        @Override
        public Component getName() {
            return Component.translatable("block.qikas_tconlib.placed_tool", stack.getHoverName());
        }

        @Override
        public Component getDisplayName() {
            return getName();
        }

        @Override
        public Component getCustomName() {
            return getName();
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
            return new PlacedToolContainerMenu(id, inv, worldPosition);
        }

        // ---- 客户端同步 ----

        /** 更新包里放置数据的键（transient：只随网络包下发，不落盘） */
        private static final String PLACEMENT_TAG = "PlacementData";

        /**
         * 客户端区块加载/重新进入世界时，服务端用这个 NBT 初始化客户端 BE。
         * <p>
         * 除工具 stack 外，附带序列化后的放置数据——联机客户端本地查不到 datapack，
         * 形状/渲染必须依赖这份网络数据（放在普通 NBT 键里，但 saveAdditional 不写，因此不落盘）。
         */
        @Override
        public CompoundTag getUpdateTag() {
            CompoundTag tag = saveWithoutMetadata();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            PlacementData.LOADER.encode(buf, getPlacementData());
            tag.putByteArray(PLACEMENT_TAG, ByteBufUtil.getBytes(buf));
            buf.release();
            return tag;
        }

        @Override
        public void handleUpdateTag(CompoundTag tag) {
            load(tag);
            if (tag.contains(PLACEMENT_TAG)) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(tag.getByteArray(PLACEMENT_TAG)));
                this.syncedPlacementData = PlacementData.LOADER.decode(buf, TypedMap.EMPTY);
            } else {
                this.syncedPlacementData = null;
            }
        }

        /**
         * 运行期间（setStack 触发的 sendBlockUpdated）发送给客户端的更新包
         */
        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        /**
         * 客户端收到更新包后应用数据
         */
        @Override
        public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
            handleUpdateTag(pkt.getTag());
        }

        private ItemStack stack = ItemStack.EMPTY;
        private ToolStack cachedTool = null;
        private IItemHandlerModifiable cachedHandler = null;
        /** 懒缓存的放置数据（随 setStack/load 失效），避免每次形状查询都走 registry 查 key */
        private PlacementData cachedPlacementData = null;
        /** 客户端从更新包解出的放置数据（transient，不落盘）；null = 未同步 */
        private PlacementData syncedPlacementData = null;

        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            CompoundTag itemTag = new CompoundTag();
            stack.save(itemTag);
            tag.put("tool", itemTag);
        }

        public void load(CompoundTag tag) {
            super.load(tag);
            stack = ItemStack.of(tag.getCompound("tool"));
            cachedTool = null;
            cachedHandler = null;
            cachedPlacementData = null;
            fluidHandler = null;
            syncingFluidHandler = null;
            pendingSync = false;
            lastSyncTick = Long.MIN_VALUE;
        }

        private ToolStack getTool() {
            if (cachedTool == null)
                cachedTool = ToolStack.from(stack);
            return cachedTool;
        }

        /** 计算工具光等级：按工具 modifier 顺序折叠 PLACED_TOOL_LIGHT_HOOK，夹取到 0-15 */
        public int getLightLevel() {
            int light = 0;
            ToolStack tool = getTool();
            BlockState state = level == null ? null : level.getBlockState(worldPosition);
            for (ModifierEntry entry : tool.getModifierList()) {
                light = entry.getHook(TconLib.PLACED_TOOL_LIGHT_HOOK).getLightLevel(tool, entry, state, level,
                        worldPosition, this, light);
            }
            return Math.max(0, Math.min(15, light));
        }

        /** 服务端：重算光等级并写入 LIGHT 属性；变化时才 setBlock，避免多余的光照更新 */
        private void updateLight() {
            if (level == null || level.isClientSide) {
                return;
            }
            int light = getLightLevel();
            BlockState state = level.getBlockState(worldPosition);
            if (state.getValue(PlacedToolBlock.LIGHT) != light) {
                level.setBlock(worldPosition, state.setValue(PlacedToolBlock.LIGHT, light), Block.UPDATE_CLIENTS);
            }
        }

        /** 供菜单等外部访问工具数据 */
        public ToolStack getToolStack() {
            return getTool();
        }

        /** 工具物品（菜单打开时随额外数据发送给客户端） */
        public ItemStack getStack() {
            return stack;
        }

        /** 按工具查放置数据（懒缓存，setStack/load 时失效）；未配置时回退默认 */
        public PlacementData getPlacementData() {
            // 客户端优先用网络同步的数据：联机时本地查不到 datapack，只能靠服务端更新包下发
            if (this.level != null && this.level.isClientSide && this.syncedPlacementData != null) {
                return this.syncedPlacementData;
            }
            if (cachedPlacementData == null) {
                PlacementData data = ToolPlacementDataManager.INSTANCE.getData(stack);
                cachedPlacementData = data != null ? data : PlacementData.DEFAULT;
            }
            return cachedPlacementData;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return getHandler().isItemValid(slot, stack);
        }

        public IItemHandlerModifiable getHandler() {
            if (cachedHandler == null)
                cachedHandler = stack.getCapability(ForgeCapabilities.ITEM_HANDLER)
                        .filter(cap -> cap instanceof IItemHandlerModifiable)
                        .map(cap -> (IItemHandlerModifiable) cap)
                        .orElse(new EmptyHandler());
            return cachedHandler;
        }

        @Override
        public int getContainerSize() {
            return getHandler().getSlots();
        }

        @Override
        public boolean isEmpty() {
            IItemHandler handler = getHandler();
            for (int i = 0; i < handler.getSlots(); i++) {
                if (!ItemStack.EMPTY.equals(handler.extractItem(i, 1, true)))
                    return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return getHandler().getStackInSlot(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack result = getHandler().extractItem(slot, amount, false);
            this.setChanged();
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return getHandler().extractItem(slot, 64, false);
        }

        @Override
        public void setItem(int slot, ItemStack item) {
            getHandler().setStackInSlot(slot, item);
            this.setChanged();
        }

        @Override
        public boolean stillValid(Player p_18946_) {
            return true;
        }

        @Override
        public void clearContent() {
            IItemHandler handler = getHandler();
            for (int i = 0; i < handler.getSlots(); i++) {
                handler.extractItem(i, 64, false);
            }
            this.setChanged();
        }

        @Override
        public int[] getSlotsForFace(Direction face) {
            return IntStream.range(0, getContainerSize()).toArray();
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack item, @Nullable Direction face) {
            return getHandler().isItemValid(slot, item);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack item, Direction face) {
            return !getHandler().extractItem(slot, 64, true).isEmpty();
        }

    }
    /**
     * 放置工具方块实体接口：实现者须为方块实体（{@link BlockEntity}）。
     * 接口本身不能继承类（Java 语法限制），需要同时使用 BE 方法与接口方法时，
     * 用泛型交集 {@code <T extends BlockEntity & IToolBlockEntity>}（见 PlacedToolContainerMenu）。
     */
    public static interface IToolBlockEntity{
        void setStack(ItemStack stack);
        ToolStack getToolStack();
        ItemStack getStack();
        IItemHandlerModifiable getHandler();
        void addViewer(ServerPlayer player);
        void removeViewer(ServerPlayer player);
    }
}
