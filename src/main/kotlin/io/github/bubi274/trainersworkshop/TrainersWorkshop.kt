package io.github.bubi274.trainersworkshop

import io.github.bubi274.trainersworkshop.compat.RaidDensCompat
import io.github.bubi274.trainersworkshop.config.WorkshopConfig
import io.github.bubi274.trainersworkshop.interaction.PokemonInteractionHandler
import io.github.bubi274.trainersworkshop.registry.ModCreativeTabs
import io.github.bubi274.trainersworkshop.registry.ModItems
import net.fabricmc.api.ModInitializer
import net.minecraft.util.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Điểm khởi động của mod: gọi lần lượt từng phần cần đăng ký. */
object TrainersWorkshop : ModInitializer {
    const val MOD_ID = "trainers_workshop"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    fun id(path: String): Identifier = Identifier.of(MOD_ID, path)

    override fun onInitialize() {
        WorkshopConfig.load()
        ModItems.register()
        ModCreativeTabs.register()
        PokemonInteractionHandler.register()
        RaidDensCompat.register()
    }
}
