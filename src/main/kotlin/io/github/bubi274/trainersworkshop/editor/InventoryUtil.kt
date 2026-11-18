package io.github.bubi274.trainersworkshop.editor

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity

object InventoryUtil {
    /** Takes items from the player's inventory. Call this after checking there are enough. */
    fun take(player: ServerPlayerEntity, needed: Map<Item, Int>) {
        if (needed.isEmpty()) return
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

    /** Gives back exactly the items that were taken when Apply fails. */
    fun refund(player: ServerPlayerEntity, taken: Map<Item, Int>) {
        if (taken.isEmpty()) return
        val inventory = player.inventory
        for ((item, count) in taken) {
            var remaining = count
            while (remaining > 0) {
                val batch = minOf(remaining, item.maxCount)
                inventory.offerOrDrop(ItemStack(item, batch))
                remaining -= batch
            }
        }
        inventory.markDirty()
    }
}
