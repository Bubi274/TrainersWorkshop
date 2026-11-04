package io.github.bubi274.trainersworkshop.interaction

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult

/**
 * Item dùng bằng cách chuột phải vào một Pokémon ngoài thế giới.
 * Hàm này luôn chạy ở phía server.
 */
interface PokemonTargetItem {
    fun useOnPokemon(player: ServerPlayerEntity, entity: PokemonEntity, stack: ItemStack): ActionResult
}
