package io.github.bubi274.trainersworkshop.interaction

import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult

/**
 * Item dùng bằng cách chuột phải vào một loại entity bất kỳ (ví dụ trainer của RCT).
 * [canTarget] chạy ở cả hai phía; [useOnEntity] luôn chạy ở phía server.
 */
interface EntityTargetItem {
    fun canTarget(entity: Entity): Boolean
    fun useOnEntity(player: ServerPlayerEntity, entity: Entity, stack: ItemStack): ActionResult
}
