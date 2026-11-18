package io.github.bubi274.trainersworkshop.client

import net.fabricmc.api.ClientModInitializer

/** Client entry point (only runs on the player's computer). */
object TrainersWorkshopClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientEditor.register()
    }
}
