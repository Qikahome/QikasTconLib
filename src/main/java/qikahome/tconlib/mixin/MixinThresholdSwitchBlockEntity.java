package qikahome.tconlib.mixin;

import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import qikahome.tconlib.placeabletool.PlacedToolBlock;

/**
 * 修复 Create 存量转信器对无 BlockItem 方块（如放置工具方块）的"未附加到方块"显示。
 * <p>
 * getDisplayItemForScreen 原实现为 {@code new ItemStack(block)}，依赖 {@code asItem()}；
 * 放置工具方块用 registerNoItem 注册（无 BlockItem），asItem() 返回空气，导致屏幕显示"未附加到方块"。
 * 这里仅在目标方块为本模组的放置工具方块时，改为取 getCloneItemStack 的结果
 * （放置工具方块已重写该方法返回工具物品），不影响其他模组方块的显示。
 */
@Mixin(value = ThresholdSwitchBlockEntity.class, remap = false)
public abstract class MixinThresholdSwitchBlockEntity {

    @Shadow
    private BlockPos getTargetPos() {
        throw new AssertionError();
    }

    @SuppressWarnings("deprecation") // Block.getCloneItemStack(BlockGetter, BlockPos, BlockState) 为 Forge 标记废弃版本
    @Inject(method = "getDisplayItemForScreen", at = @At("RETURN"), cancellable = true, remap = false)
    private void tconlib$useCloneItemStack(CallbackInfoReturnable<ItemStack> cir) {
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (level == null)
            return;
        BlockPos targetPos = getTargetPos();
        BlockState state = level.getBlockState(targetPos);
        // 仅限本模组的放置工具方块，防止影响其他模组的存量转信器显示
        if (state.getBlock() instanceof PlacedToolBlock) {
            ItemStack stack = state.getBlock().getCloneItemStack(level, targetPos, state);
            if (!stack.isEmpty()) {
                cir.setReturnValue(stack);
            }
        }
    }
}
