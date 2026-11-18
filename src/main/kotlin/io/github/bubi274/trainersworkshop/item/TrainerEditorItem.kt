package io.github.bubi274.trainersworkshop.item

import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.compat.rct.RctCompat
import io.github.bubi274.trainersworkshop.editor.EditorSessions
import io.github.bubi274.trainersworkshop.editor.TrainerEditing
import io.github.bubi274.trainersworkshop.interaction.EntityTargetItem
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.tooltip.TooltipType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

/** Trainer Editor: right-click a Radical Cobblemon Trainers trainer to edit its team and loot. Creative only. */
class TrainerEditorItem(settings: Item.Settings) : Item(settings), EntityTargetItem {

    override fun canTarget(entity: Entity): Boolean = RctCompat.isTrainer(entity)

    override fun useOnEntity(player: ServerPlayerEntity, entity: Entity, stack: ItemStack): ActionResult {
        if (!player.isCreative) {
            player.sendMessage(message("no_permission").formatted(Formatting.RED), true)
            return ActionResult.FAIL
        }
        TrainerEditing.open(player, entity)
        return ActionResult.SUCCESS
    }

    /** Right-click in the air: show how to use it (or say that RCT is missing). */
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)
        if (!world.isClient) {
            val key = if (RctCompat.loaded) "trainer_editor.hint" else "trainer_editor.no_rct"
            user.sendMessage(message(key).formatted(Formatting.YELLOW), true)
        }
        return TypedActionResult.pass(stack)
    }

    override fun appendTooltip(stack: ItemStack, context: Item.TooltipContext, tooltip: MutableList<Text>, type: TooltipType) {
        tooltip.add(Text.translatable("$translationKey.tooltip").formatted(Formatting.GRAY))
    }

    private fun message(key: String) = Text.translatable("message.${TrainersWorkshop.MOD_ID}.$key")
}
