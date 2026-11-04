package io.github.bubi274.trainersworkshop.editor

import net.minecraft.item.Item
import net.minecraft.server.network.ServerPlayerEntity

object InventoryUtil {
    /** Trừ vật phẩm khỏi túi người chơi. Gọi sau khi đã kiểm tra đủ số lượng. */
    fun take(player: ServerPlayerEntity, needed: Map<Item, Int>) {
        val inventory = player.inventory
        for ((item, count) in needed) {
            var remaining = count
            for (slot in 0 until inventory.size()) {
                if (remaining <= 0) break
                val stack = inventory.getStack(slot)
                if (stack.isEmpty || stack.item != item) continue
                val used = minOf(remaining, stack.count)
                stack.decrement(used)
                remaining -= used
            }
        }
        inventory.markDirty()
    }
}
