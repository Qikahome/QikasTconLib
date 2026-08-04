package qikahome.tconlib.placeabletool;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import qikahome.tconlib.TconLib;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InteractionSource;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 护甲（{@link ModifiableArmorItem}）的手持放置调度。
 * <p>
 * TCon 护甲继承原版 ArmorItem，{@code useOn} 不会调度 TCon 的 BLOCK_INTERACT 钩子，
 * 导致带 placing 模块的护甲手持右键无法放置。这里在 {@code RightClickBlock} 事件里模拟
 * {@code ModifiableItem.useOn} 的钩子调度（仅 afterBlockUse 阶段）。
 * <p>
 * 护甲始终需要潜行放置：不潜行时放行给 vanilla 的穿戴/开物品栏逻辑，避免冲突。
 */
@Mod.EventBusSubscriber(modid = TconLib.MODID)
public final class PlaceableArmorEvents {
  private PlaceableArmorEvents() {
  }

  @SubscribeEvent
  public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    InteractionHand hand = event.getHand();
    ItemStack stack = event.getItemStack();
    // 仅处理手上的 TCon 护甲
    if (stack.isEmpty() || !(stack.getItem() instanceof ModifiableArmorItem)) {
      return;
    }
    Player player = event.getEntity();
    // 护甲始终需要潜行放置；不潜行放行给 vanilla（穿戴/开物品栏）
    if (!player.isShiftKeyDown()) {
      return;
    }
    ToolStack tool = ToolStack.from(stack);
    // 模拟 ModifiableItem.shouldInteract：no_interaction / 副手 defer 时跳过
    if (tool.getVolatileData().getBoolean(IModifiable.NO_INTERACTION)) {
      return;
    }
    if (hand == InteractionHand.MAIN_HAND && tool.getVolatileData().getBoolean(IModifiable.DEFER_OFFHAND)
        && !player.getOffhandItem().isEmpty()) {
      return;
    }
    // 未带 placing 模块（无易失标记）则不处理
    if (!tool.getVolatileData().getBoolean(PlacingModule.CAN_PLACE)) {
      return;
    }
    Level level = player.level();
    UseOnContext context = new UseOnContext(level, player, hand, stack, event.getHitVec());
    // 模拟 ModifiableItem.useOn 的钩子调度
    for (ModifierEntry entry : tool.getModifierList()) {
      InteractionResult result = entry.getHook(ModifierHooks.BLOCK_INTERACT).afterBlockUse(tool, entry, context,
          InteractionSource.RIGHT_CLICK);
      if (result.consumesAction()) {
        event.setCanceled(true);
        event.setCancellationResult(result);
        return;
      }
    }
  }
}
