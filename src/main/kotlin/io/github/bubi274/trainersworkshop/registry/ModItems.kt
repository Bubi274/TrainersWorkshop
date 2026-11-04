package io.github.bubi274.trainersworkshop.registry

import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.item.ShinyBerryItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry

/** Danh sách item của mod. Món mới thêm vào đây. */
object ModItems {
    val SHINY_BERRY: Item = register("shiny_berry", ShinyBerryItem(Item.Settings()))

    /** Thứ tự hiển thị trong tab creative. */
    val ALL: List<Item> = listOf(SHINY_BERRY)

    private fun register(name: String, item: Item): Item =
        Registry.register(Registries.ITEM, TrainersWorkshop.id(name), item)

    /** Gọi để chắc chắn object này được nạp (các item đăng ký ở trên). */
    fun register() = Unit
}
