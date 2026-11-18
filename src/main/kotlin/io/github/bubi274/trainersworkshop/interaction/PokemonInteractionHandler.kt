package io.github.bubi274.trainersworkshop.interaction

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import java.util.UUID

/**
 * Catches right-clicks on an entity while holding a [PokemonTargetItem] (on a Pokémon)
 * or an [EntityTargetItem] (on another entity, for example a trainer).
 * Runs before the default Cobblemon/RCT interaction, so their menus don't "steal" the click.
 *
 * How Fabric calls this event: the client calls it only once; if we return something other than PASS, Fabric sends
 * an "interact at" packet to the server, swings the hand and skips the default handling. The server calls the event
 * for every packet it receives, so we block repeated handling within the same tick.
 */
object PokemonInteractionHandler {
    private val lastUse = HashMap<UUID, Pair<Int, Long>>()

    fun register() {
        UseEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            val stack = player.getStackInHand(hand)
            val item = stack.item
            val handled = (item is PokemonTargetItem && entity is PokemonEntity) ||
                (item is EntityTargetItem && item.canTarget(entity))
            if (!handled || player.isSpectator) {
                return@register ActionResult.PASS
            }
            // The client only swings the hand and sends the packet; every check runs on the server.
            if (world.isClient || player !is ServerPlayerEntity) {
                return@register ActionResult.SUCCESS
            }
            if (isRepeat(player, entity, world.time)) {
                return@register ActionResult.SUCCESS
            }
            if (item is PokemonTargetItem && entity is PokemonEntity) {
                item.useOnPokemon(player, entity, stack)
            } else {
                (item as EntityTargetItem).useOnEntity(player, entity, stack)
            }
        }
    }

    /** Same player, same entity, same tick: already handled. */
    private fun isRepeat(player: ServerPlayerEntity, entity: Entity, time: Long): Boolean {
        val key = Pair(entity.id, time)
        if (lastUse[player.uuid] == key) return true
        lastUse[player.uuid] = key
        return false
    }
}
