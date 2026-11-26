package io.github.bubi274.trainersworkshop.client

import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.challenge.ChallengePaste
import io.github.bubi274.trainersworkshop.network.ChallengeClipboardPayload
import io.github.bubi274.trainersworkshop.network.ChallengePastePayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.lwjgl.glfw.GLFW

/**
 * Client side of challenge mode: pasting a code from the clipboard and copying the reroll code to it.
 *
 * "/challenge paste" is caught here before it is sent (ClientSendMessageEvents.ALLOW_COMMAND), so every other
 * /challenge command still goes to the server unchanged. The code then travels as a payload, not as chat.
 */
object ClientChallenge {
    private const val KEY = "message.${TrainersWorkshop.MOD_ID}.challenge"

    /** "Paste challenge code" in Controls, unbound by default. */
    private val pasteKey = KeyBinding(
        "key.${TrainersWorkshop.MOD_ID}.paste_challenge_code",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        "key.categories.${TrainersWorkshop.MOD_ID}",
    )

    fun register() {
        KeyBindingHelper.registerKeyBinding(pasteKey)
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (pasteKey.wasPressed()) paste(client)
        }

        ClientSendMessageEvents.ALLOW_COMMAND.register { command ->
            if (command.trim().split(Regex("\\s+")) == listOf("challenge", "paste")) {
                paste(MinecraftClient.getInstance())
                false
            } else {
                true
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ChallengeClipboardPayload.ID) { payload, context ->
            context.client().keyboard.clipboard = payload.text
            context.player()?.sendMessage(Text.translatable("$KEY.reroll_copied").formatted(Formatting.GREEN), false)
        }
    }

    /** Reads the clipboard, checks it and sends it to the server. Errors go to chat without the clipboard text. */
    fun paste(client: MinecraftClient) {
        val player = client.player ?: return
        val text = try {
            client.keyboard.clipboard
        } catch (e: Exception) {
            null
        }
        val problem = ChallengePaste.problem(ChallengePaste.classify(text))
        if (problem != null) {
            player.sendMessage(Text.literal(problem).formatted(Formatting.RED), false)
            return
        }
        if (!ClientPlayNetworking.canSend(ChallengePastePayload.ID)) {
            player.sendMessage(Text.translatable("$KEY.paste_no_server").formatted(Formatting.RED), false)
            return
        }
        ClientPlayNetworking.send(ChallengePastePayload(text!!.trim()))
    }
}
