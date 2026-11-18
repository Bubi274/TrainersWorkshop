package io.github.bubi274.trainersworkshop.interaction

import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult

/**
 * An item used by right-clicking any kind of entity (for example an RCT trainer).
 * [canTarget] runs on both sides; [useOnEntity] always runs on the server.
 */
interface EntityTargetItem {
    fun canTarget(entity: Entity): Boolean
    fun useOnEntity(player: ServerPlayerEntity, entity: Entity, stack: ItemStack): ActionResult
}
