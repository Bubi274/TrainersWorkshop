package io.github.bubi274.trainersworkshop.editor

import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity

object InventoryUtil {
    /** How many items in [inventory] match [cost] (works on both sides). */
    fun count(inventory: Inventory, cost: CostItem): Int {
        var total = 0
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (cost.matches(stack)) total += stack.count
        }
        return total
    }

    /**
     * Takes items from the player's inventory. Call this after checking there are enough.
     * Returns copies of the taken stacks (with their components), so [refund] gives back exactly the same items.
     */
    fun take(player: ServerPlayerEntity, needed: Map<CostItem, Int>): List<ItemStack> {
        if (needed.isEmpty()) return emptyList()
        val inventory = player.inventory
        val taken = mutableListOf<ItemStack>()
        for ((cost, count) in needed) {
            var remaining = count
            for (slot in 0 until inventory.size()) {
                if (remaining <= 0) break
                val stack = inventory.getStack(slot)
                if (!cost.matches(stack)) continue
                val used = minOf(remaining, stack.count)
                taken += stack.copyWithCount(used)
                stack.decrement(used)
                remaining -= used
            }
        }
        inventory.markDirty()
        return taken
    }

    /** Gives back exactly the items that were taken when Apply fails. */
    fun refund(player: ServerPlayerEntity, taken: List<ItemStack>) {
        if (taken.isEmpty()) return
        val inventory = player.inventory
        taken.forEach { inventory.offerOrDrop(it.copy()) }
        inventory.markDirty()
    }
}
