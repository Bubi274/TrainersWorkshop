package io.github.bubi274.trainersworkshop

import io.github.bubi274.trainersworkshop.compat.RaidDensCompat
import io.github.bubi274.trainersworkshop.compat.rct.RctCompat
import io.github.bubi274.trainersworkshop.config.WorkshopConfig
import io.github.bubi274.trainersworkshop.editor.EditorSessions
import io.github.bubi274.trainersworkshop.editor.TrainerOverrides
import io.github.bubi274.trainersworkshop.interaction.PokemonInteractionHandler
import io.github.bubi274.trainersworkshop.network.EditorPayloads
import io.github.bubi274.trainersworkshop.registry.ModCreativeTabs
import io.github.bubi274.trainersworkshop.registry.ModItems
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.util.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Entry point of the mod: calls each part that needs to register, in order. */
object TrainersWorkshop : ModInitializer {
    const val MOD_ID = "trainers_workshop"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    fun id(path: String): Identifier = Identifier.of(MOD_ID, path)

    override fun onInitialize() {
        WorkshopConfig.load()
        ModItems.register()
        ModCreativeTabs.register()
        PokemonInteractionHandler.register()
        EditorPayloads.register()
        EditorSessions.register()
        RaidDensCompat.register()
        RctCompat.init()

        // The trainer was killed/removed for good: also drop its saved edits.
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, world ->
            if (entity.removalReason?.shouldDestroy() == true && RctCompat.isTrainer(entity)) {
                TrainerOverrides.get(world.server).remove(entity.uuid)
                RctCompat.forget(entity.uuid)
            }
        }
    }
}
