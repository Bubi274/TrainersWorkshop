package io.github.bubi274.trainersworkshop.interaction

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult

/**
 * An item used by right-clicking a Pokémon in the world.
 * This function always runs on the server.
 */
interface PokemonTargetItem {
    fun useOnPokemon(player: ServerPlayerEntity, entity: PokemonEntity, stack: ItemStack): ActionResult
}
