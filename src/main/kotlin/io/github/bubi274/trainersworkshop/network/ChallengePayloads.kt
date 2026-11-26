package io.github.bubi274.trainersworkshop.network

import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.challenge.ChallengePaste
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload

/**
 * Client -> server: a code pasted from the clipboard. Sent as a custom payload, not through chat, so the 256-character
 * chat limit doesn't apply. Limited to [ChallengePaste.MAX_LENGTH] characters (the client checks before sending).
 */
data class ChallengePastePayload(val code: String) : CustomPayload {
    override fun getId(): CustomPayload.Id<ChallengePastePayload> = ID

    companion object {
        val ID = CustomPayload.Id<ChallengePastePayload>(TrainersWorkshop.id("challenge_paste"))
        val CODEC: PacketCodec<ByteBuf, ChallengePastePayload> =
            PacketCodecs.string(ChallengePaste.MAX_LENGTH).xmap({ ChallengePastePayload(it) }, { it.code })
    }
}

/** Server -> client: text to put on the clipboard (the TWR1 reroll code, for PokéRoll). */
data class ChallengeClipboardPayload(val text: String) : CustomPayload {
    override fun getId(): CustomPayload.Id<ChallengeClipboardPayload> = ID

    companion object {
        val ID = CustomPayload.Id<ChallengeClipboardPayload>(TrainersWorkshop.id("challenge_clipboard"))
        val CODEC: PacketCodec<ByteBuf, ChallengeClipboardPayload> =
            PacketCodecs.string(ChallengePaste.MAX_LENGTH).xmap({ ChallengeClipboardPayload(it) }, { it.text })
    }
}

object ChallengePayloads {
    fun register() {
        PayloadTypeRegistry.playC2S().register(ChallengePastePayload.ID, ChallengePastePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ChallengeClipboardPayload.ID, ChallengeClipboardPayload.CODEC)
    }
}
