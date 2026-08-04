package qikahome.tconlib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import qikahome.tconlib.placeabletool.PlacedToolBlock;
import qikahome.tconlib.placeabletool.PlacedToolBlock.PlacedToolBlockEntity;

/**
 * 让"可被水冲掉的放置工具"被自然流动的水破坏。自然流动链路共三道闸门，全部需要放行：
 * <ul>
 *   <li>{@link FlowingFluid#canPassThrough}（getSpread/getSlopeDistance 筛选候选方向）：
 *       目标位置是可被冲掉的放置工具时放行。这是最前的一道闸——不过这里，
 *       canSpreadTo 根本不会被以工具方块为目标调用；</li>
 *   <li>{@link FlowingFluid#canSpreadTo}：放行扩散
 *       （canPlaceLiquid 返回 false，原版对它会因 canHoldFluid 而拒绝扩散）；</li>
 *   <li>{@link FlowingFluid#spreadTo}：把方块替换为流体，
 *       setBlock 触发 PlacedToolBlock.onRemove 掉落工具，并取消原含水逻辑。</li>
 * </ul>
 * 桶直接倒水不经过以上方法，由 {@link PlacedToolBlock#canPlaceLiquid} 拒绝。
 */
@Mixin(FlowingFluid.class)
public abstract class MixinFlowingFluid {

    @Inject(method = "canPassThrough", at = @At("RETURN"), cancellable = true)
    private void tconlib$allowFlowThrough(BlockGetter level, Fluid fluid, BlockPos pos, BlockState state,
            Direction dir, BlockPos neighborPos, BlockState neighborState, FluidState neighborFluid,
            CallbackInfoReturnable<Boolean> cir) {
        // 目标方块是可被水冲掉的放置工具：让水流把该方向列为候选扩散方向
        if (!cir.getReturnValueZ() && neighborState.getBlock() instanceof PlacedToolBlock
                && level instanceof Level lv && level.getBlockEntity(neighborPos) instanceof PlacedToolBlockEntity ptbe
                && PlacedToolBlock.canWashAway(lv, neighborPos, neighborState, ptbe.getStack())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "canSpreadTo", at = @At("RETURN"), cancellable = true)
    private void tconlib$allowSpreadTo(BlockGetter level, BlockPos pos, BlockState state, Direction dir,
            BlockPos neighborPos, BlockState neighborState, FluidState fluid, Fluid fluidType,
            CallbackInfoReturnable<Boolean> cir) {
        // 目标方块是可被水冲掉的放置工具：放行扩散（spreadTo 中会破坏该方块）
        if (!cir.getReturnValueZ() && neighborState.getBlock() instanceof PlacedToolBlock
                && level instanceof Level lv && level.getBlockEntity(neighborPos) instanceof PlacedToolBlockEntity ptbe
                && PlacedToolBlock.canWashAway(lv, neighborPos, neighborState, ptbe.getStack())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void tconlib$washAway(LevelAccessor level, BlockPos pos, BlockState state, Direction dir,
            FluidState fluid, CallbackInfo ci) {
        // 水流动过可被冲掉的放置工具：替换成水（setBlock 触发 onRemove 掉落工具），并取消原逻辑
        if (state.getBlock() instanceof PlacedToolBlock && level instanceof Level lv
                && level.getBlockEntity(pos) instanceof PlacedToolBlockEntity ptbe
                && PlacedToolBlock.canWashAway(lv, pos, state, ptbe.getStack())) {
            level.setBlock(pos, fluid.createLegacyBlock(), 3);
            ci.cancel();
        }
    }
}
