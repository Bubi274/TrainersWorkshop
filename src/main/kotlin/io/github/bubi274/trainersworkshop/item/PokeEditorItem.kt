package io.github.bubi274.trainersworkshop.item

import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.editor.EditorMode
import io.github.bubi274.trainersworkshop.editor.EditorSessions
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.tooltip.TooltipType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

/** PokeEditor: chuột phải (vào không khí) để mở màn hình chỉnh Pokémon trong đội. */
class PokeEditorItem(settings: Item.Settings, private val mode: EditorMode) : Item(settings) {

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)
        if (world.isClient || user !is ServerPlayerEntity) return TypedActionResult.success(stack)

        if (mode == EditorMode.CREATIVE && !user.hasPermissionLevel(EditorSessions.OP_LEVEL)) {
            user.sendMessage(Text.translatable("message.${TrainersWorkshop.MOD_ID}.no_permission").formatted(Formatting.RED), true)
            return TypedActionResult.fail(stack)
        }
        EditorSessions.openPartyEditor(user, mode)
        return TypedActionResult.success(stack)
    }

    override fun appendTooltip(stack: ItemStack, context: Item.TooltipContext, tooltip: MutableList<Text>, type: TooltipType) {
        tooltip.add(Text.translatable("$translationKey.tooltip").formatted(Formatting.GRAY))
    }
}
