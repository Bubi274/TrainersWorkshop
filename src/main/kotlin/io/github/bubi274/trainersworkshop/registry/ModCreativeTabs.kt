package io.github.bubi274.trainersworkshop.registry

import io.github.bubi274.trainersworkshop.TrainersWorkshop
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.text.Text

object ModCreativeTabs {
    val MAIN: ItemGroup = Registry.register(
        Registries.ITEM_GROUP,
        TrainersWorkshop.id("main"),
        FabricItemGroup.builder()
            .displayName(Text.translatable("itemGroup.${TrainersWorkshop.MOD_ID}"))
            .icon { ItemStack(ModItems.SHINY_BERRY) }
            .entries { _, entries -> ModItems.ALL.forEach { entries.add(it) } }
            .build()
    )

    fun register() = Unit
}
