package io.github.bubi274.trainersworkshop.client

import net.fabricmc.api.ClientModInitializer

/** Điểm khởi động phía client (chỉ chạy trên máy người chơi). */
object TrainersWorkshopClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientEditor.register()
    }
}
