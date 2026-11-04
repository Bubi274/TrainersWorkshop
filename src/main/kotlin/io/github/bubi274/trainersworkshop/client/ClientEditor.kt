package io.github.bubi274.trainersworkshop.client

import io.github.bubi274.trainersworkshop.client.gui.EditorScreen
import io.github.bubi274.trainersworkshop.editor.EditorState
import io.github.bubi274.trainersworkshop.network.EditorActionPayload
import io.github.bubi274.trainersworkshop.network.EditorStatePayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.nbt.NbtCompound

/** Nhận trạng thái từ server và gửi hành động lên server. */
object ClientEditor {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(EditorStatePayload.ID) { payload, context ->
            onState(context.client(), payload.nbt)
        }
    }

    private fun onState(client: MinecraftClient, nbt: NbtCompound) {
        val state = EditorState.fromNbt(nbt)
        val status = if (nbt.contains("status")) nbt.getCompound("status") else null
        val screen = client.currentScreen
        if (screen is EditorScreen && screen.mode == state.mode) {
            screen.update(state, status)
        } else {
            client.setScreen(EditorScreen(state).also { it.update(state, status) })
        }
    }

    fun send(action: String, fill: NbtCompound.() -> Unit) {
        val nbt = NbtCompound()
        nbt.putString("action", action)
        nbt.fill()
        ClientPlayNetworking.send(EditorActionPayload(nbt))
    }
}
