package io.github.bubi274.trainersworkshop.item

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.interaction.PokemonTargetItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.tooltip.TooltipType
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting

/** Shiny Berry: chuột phải vào Pokémon của mình đang được thả ra để biến nó thành shiny. */
class ShinyBerryItem(settings: Item.Settings) : Item(settings), PokemonTargetItem {

    override fun useOnPokemon(player: ServerPlayerEntity, entity: PokemonEntity, stack: ItemStack): ActionResult {
        val pokemon = entity.pokemon

        val refusal = when {
            pokemon.isWild() -> "wild"
            pokemon.getOwnerUUID() != player.uuid -> "not_owner"
            entity.isBattling -> "in_battle"
            else -> null
        }
        if (refusal != null) {
            player.sendMessage(message(refusal).formatted(Formatting.RED), true)
            return ActionResult.FAIL
        }

        if (pokemon.shiny) {
            player.sendMessage(message("already_shiny", pokemon.getDisplayName(false)).formatted(Formatting.YELLOW), true)
            return ActionResult.FAIL
        }

        pokemon.shiny = true
        if (!player.abilities.creativeMode) {
            stack.decrement(1)
        }
        playEffects(player, entity)
        player.sendMessage(message("success", pokemon.getDisplayName(false)).formatted(Formatting.GOLD), false)
        return ActionResult.SUCCESS
    }

    private fun playEffects(player: ServerPlayerEntity, entity: PokemonEntity) {
        val world = player.serverWorld
        val x = entity.x
        val y = entity.y + entity.height / 2
        val z = entity.z
        val spread = entity.width / 2.0

        world.playSound(null, x, y, z, CobblemonSounds.POKE_BALL_SHINY_SEND_OUT, SoundCategory.NEUTRAL, 1.0f, 1.0f)
        world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 30, spread, entity.height / 2.0, spread, 0.05)
        world.spawnParticles(ParticleTypes.FIREWORK, x, y, z, 15, spread, entity.height / 2.0, spread, 0.1)
    }

    override fun appendTooltip(stack: ItemStack, context: Item.TooltipContext, tooltip: MutableList<Text>, type: TooltipType) {
        tooltip.add(Text.translatable("$translationKey.tooltip").formatted(Formatting.GRAY))
    }

    private fun message(key: String, vararg args: Any): MutableText =
        Text.translatable("message.${TrainersWorkshop.MOD_ID}.shiny_berry.$key", *args)
}
