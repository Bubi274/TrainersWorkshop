package io.github.bubi274.trainersworkshop.interaction

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import java.util.UUID

/**
 * Bắt sự kiện chuột phải vào entity khi đang cầm một [PokemonTargetItem] (vào Pokémon)
 * hoặc [EntityTargetItem] (vào entity khác, ví dụ trainer).
 * Chạy trước tương tác mặc định của Cobblemon/RCT nên không bị menu của chúng "giành" mất.
 *
 * Cách Fabric gọi sự kiện này: phía client chỉ gọi một lần; nếu ta trả về khác PASS, Fabric gửi
 * một gói tin "interact at" lên server, vẫy tay và bỏ qua xử lý mặc định. Phía server gọi sự kiện
 * cho mỗi gói tin nhận được, nên ta chặn xử lý lặp trong cùng một tick.
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
            // Phía client chỉ vẫy tay và gửi gói tin; mọi kiểm tra làm ở server.
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

    /** Cùng người chơi, cùng entity, cùng tick: đã xử lý rồi. */
    private fun isRepeat(player: ServerPlayerEntity, entity: Entity, time: Long): Boolean {
        val key = Pair(entity.id, time)
        if (lastUse[player.uuid] == key) return true
        lastUse[player.uuid] = key
        return false
    }
}
