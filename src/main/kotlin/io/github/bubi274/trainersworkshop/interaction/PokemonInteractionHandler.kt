package io.github.bubi274.trainersworkshop.interaction

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult

/**
 * Bắt sự kiện chuột phải vào Pokémon khi đang cầm một [PokemonTargetItem].
 * Chạy trước tương tác mặc định của Cobblemon nên không bị menu của Cobblemon "giành" mất.
 */
object PokemonInteractionHandler {
    fun register() {
        UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            val stack = player.getStackInHand(hand)
            val item = stack.item
            if (item !is PokemonTargetItem || entity !is PokemonEntity || player.isSpectator) {
                return@register ActionResult.PASS
            }
            // Mỗi cú chuột phải gọi sự kiện hai lần (có và không có hitResult); chỉ xử lý một lần.
            if (hitResult != null) {
                return@register ActionResult.PASS
            }
            // Phía client chỉ vẫy tay và gửi gói tin; mọi kiểm tra làm ở server.
            if (world.isClient || player !is ServerPlayerEntity) {
                return@register ActionResult.SUCCESS
            }
            item.useOnPokemon(player, entity, stack)
        }
    }
}
