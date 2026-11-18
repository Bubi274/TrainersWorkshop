package io.github.bubi274.trainersworkshop.editor

import com.cobblemon.mod.common.item.components.TMMoveComponent
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

/**
 * What one survival cost is paid with: an [item], or, when [tmMove] is set, a Cobblemon TM
 * (cobblemon:technical_machine) whose cobblemon:tm_move component holds that move.
 * All Cobblemon TMs are the same item, so the move is needed to count and take the right ones.
 */
data class CostItem(val item: Item, val tmMove: String? = null) {
    fun matches(stack: ItemStack): Boolean {
        if (stack.isEmpty || stack.item != item) return false
        if (tmMove == null) return true
        // Same lookup as Cobblemon's TechnicalMachineItem (TMMoveComponent.getTMMove).
        return TMMoveComponent.getTMMove(stack)?.name.equals(tmMove, ignoreCase = true)
    }
}
