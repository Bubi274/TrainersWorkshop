package io.github.bubi274.trainersworkshop.registry

import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.editor.EditorMode
import io.github.bubi274.trainersworkshop.item.PokeEditorItem
import io.github.bubi274.trainersworkshop.item.ShinyBerryItem
import io.github.bubi274.trainersworkshop.item.TrainerEditorItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry

/** The mod's items. Add new items here. */
object ModItems {
    val SHINY_BERRY: Item = register("shiny_berry", ShinyBerryItem(Item.Settings()))
    val POKEEDITOR: Item = register("pokeeditor", PokeEditorItem(Item.Settings().maxCount(1), EditorMode.SURVIVAL))
    val POKEEDITOR_CREATIVE: Item = register("pokeeditor_creative", PokeEditorItem(Item.Settings().maxCount(1), EditorMode.CREATIVE))
    val TRAINER_EDITOR: Item = register("trainer_editor", TrainerEditorItem(Item.Settings().maxCount(1)))

    /** Display order in the creative tab. */
    val ALL: List<Item> = listOf(SHINY_BERRY, POKEEDITOR, POKEEDITOR_CREATIVE, TRAINER_EDITOR)

    private fun register(name: String, item: Item): Item =
        Registry.register(Registries.ITEM, TrainersWorkshop.id(name), item)

    /** Call this to make sure the object is loaded (the items above get registered). */
    fun register() = Unit
}
