package io.github.bubi274.trainersworkshop.network

import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload

/** Server -> client: the editor screen state (see EditorState). */
data class EditorStatePayload(val nbt: NbtCompound) : CustomPayload {
    override fun getId(): CustomPayload.Id<EditorStatePayload> = ID

    companion object {
        val ID = CustomPayload.Id<EditorStatePayload>(TrainersWorkshop.id("editor_state"))
        // TODO(build): not compiled yet; check that PacketCodecs.UNLIMITED_NBT_COMPOUND is a PacketCodec<ByteBuf, NbtCompound>.
        val CODEC: PacketCodec<ByteBuf, EditorStatePayload> =
            PacketCodecs.UNLIMITED_NBT_COMPOUND.xmap<EditorStatePayload>({ EditorStatePayload(it) }, { it.nbt })
    }
}

/** Client -> server: one action on the screen (apply, close...). The server checks everything again. */
data class EditorActionPayload(val nbt: NbtCompound) : CustomPayload {
    override fun getId(): CustomPayload.Id<EditorActionPayload> = ID

    companion object {
        val ID = CustomPayload.Id<EditorActionPayload>(TrainersWorkshop.id("editor_action"))
        // Size-limited, so a client can't send a packet that is too large.
        val CODEC: PacketCodec<ByteBuf, EditorActionPayload> =
            PacketCodecs.NBT_COMPOUND.xmap<EditorActionPayload>({ EditorActionPayload(it) }, { it.nbt })
    }
}

object EditorPayloads {
    fun register() {
        PayloadTypeRegistry.playS2C().register(EditorStatePayload.ID, EditorStatePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(EditorActionPayload.ID, EditorActionPayload.CODEC)
    }
}
