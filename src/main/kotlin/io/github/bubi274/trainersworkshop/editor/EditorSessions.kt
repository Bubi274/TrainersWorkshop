package io.github.bubi274.trainersworkshop.editor

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleRegistry
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.network.EditorActionPayload
import io.github.bubi274.trainersworkshop.network.EditorStatePayload
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.UUID

/** Một màn hình đang mở của một người chơi. Server chỉ chấp nhận hành động khớp với phiên này. */
open class EditorSession(val mode: EditorMode)

/** Kết quả hiển thị ở cuối màn hình. */
data class EditorStatus(val key: String, val args: List<String> = emptyList(), val error: Boolean = false) {
    fun toNbt(): NbtCompound = NbtCompound().apply {
        putString("key", "message.${TrainersWorkshop.MOD_ID}.editor.$key")
        put("args", stringList(args))
        putBoolean("error", error)
    }
}

object EditorSessions {
    private val sessions = HashMap<UUID, EditorSession>()

    fun register() {
        ServerPlayNetworking.registerGlobalReceiver(EditorActionPayload.ID) { payload, context ->
            handle(context.player(), payload.nbt)
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> sessions.remove(handler.player.uuid) }
    }

    fun put(player: ServerPlayerEntity, session: EditorSession) {
        sessions[player.uuid] = session
    }

    fun openPartyEditor(player: ServerPlayerEntity, mode: EditorMode) {
        if (mode == EditorMode.CREATIVE && !player.isCreative) {
            player.sendMessage(Text.translatable("message.${TrainersWorkshop.MOD_ID}.no_permission").formatted(Formatting.RED), true)
            return
        }
        sessions[player.uuid] = EditorSession(mode)
        sendPartyState(player, mode, null)
    }

    fun sendState(player: ServerPlayerEntity, state: EditorState, status: EditorStatus?) {
        val nbt = state.toNbt()
        status?.let { nbt.put("status", it.toNbt()) }
        ServerPlayNetworking.send(player, EditorStatePayload(nbt))
    }

    private fun sendPartyState(player: ServerPlayerEntity, mode: EditorMode, status: EditorStatus?) {
        val party = Cobblemon.storage.getParty(player)
        val slots = (0 until 6).mapNotNull { index -> party.get(index)?.let { PokemonEditing.slot(index, it) } }
        sendState(player, EditorState(mode, slots, PokemonEditing.maxLevel(), PokemonEditing.allBalls()), status)
    }

    private fun handle(player: ServerPlayerEntity, nbt: NbtCompound) {
        val session = sessions[player.uuid] ?: return
        when (nbt.getString("action")) {
            EditorActions.CLOSE -> sessions.remove(player.uuid)
            EditorActions.APPLY -> if (session.mode != EditorMode.TRAINER) applyToParty(player, session, nbt)
            else -> handleExtra(player, session, nbt)
        }
    }

    /** Hành động riêng của Trainer Editor. */
    private fun handleExtra(player: ServerPlayerEntity, session: EditorSession, nbt: NbtCompound) {
        if (session is TrainerEditorSession) TrainerEditing.handle(player, session, nbt)
    }

    private fun applyToParty(player: ServerPlayerEntity, session: EditorSession, nbt: NbtCompound) {
        val status = tryApplyToParty(player, session, nbt)
        sendPartyState(player, session.mode, status)
    }

    private fun tryApplyToParty(player: ServerPlayerEntity, session: EditorSession, nbt: NbtCompound): EditorStatus {
        if (session.mode == EditorMode.CREATIVE && !player.isCreative) {
            player.sendMessage(Text.translatable("message.${TrainersWorkshop.MOD_ID}.no_permission").formatted(Formatting.RED), true)
            return EditorStatus("no_permission", error = true)
        }
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            return EditorStatus("in_battle", error = true)
        }
        val index = nbt.getInt("index")
        val pokemon = Cobblemon.storage.getParty(player).get(index) ?: return EditorStatus("pokemon_changed", error = true)
        val target = PokemonEditData.fromNbt(nbt.getCompound("data"))
        val registries = player.server.registryManager

        when (session.mode) {
            EditorMode.CREATIVE -> {
                PokemonEditing.validateCreative(pokemon, target)?.let { return EditorStatus(it, error = true) }
                val backup = pokemon.saveToNBT(registries, NbtCompound())
                try {
                    PokemonEditing.applyCreative(pokemon, target)
                } catch (e: Throwable) {
                    runCatching { pokemon.loadFromNBT(registries, backup) }
                    TrainersWorkshop.LOGGER.error("Failed to apply creative changes to Pokemon ${pokemon.uuid}", e)
                    return EditorStatus("apply_failed", error = true)
                }
            }
            EditorMode.SURVIVAL -> {
                val current = PokemonEditing.snapshot(pokemon)
                val normalizedTarget = target.copy(
                    currentHealth = current.currentHealth,
                    maxHealth = current.maxHealth,
                    heldItemId = current.heldItemId,
                    heldItemCount = current.heldItemCount,
                )
                if (normalizedTarget == current) return EditorStatus("nothing_to_apply")
                val options = PokemonEditing.options(pokemon)
                PokemonEditing.validateMoves(pokemon, normalizedTarget.moves, options.legalMoves)?.let { return EditorStatus(it, error = true) }
                val cost = SurvivalCosts.compute(current, normalizedTarget, options) { player.inventory.count(it) }
                cost.error?.let { return EditorStatus(it, error = true) }
                if (cost.problems.isNotEmpty()) return EditorStatus("missing_items", error = true)

                val backup = pokemon.saveToNBT(registries, NbtCompound())
                val toDeduct = LinkedHashMap(cost.needed)
                var deducted = false
                try {
                    if (toDeduct.isNotEmpty()) {
                        InventoryUtil.take(player, toDeduct)
                        deducted = true
                        player.playerScreenHandler.sendContentUpdates()
                    }
                    PokemonEditing.applySurvival(pokemon, current, normalizedTarget)
                } catch (e: Throwable) {
                    runCatching { pokemon.loadFromNBT(registries, backup) }
                    if (deducted) {
                        runCatching {
                            InventoryUtil.refund(player, toDeduct)
                            player.playerScreenHandler.sendContentUpdates()
                        }
                    }
                    TrainersWorkshop.LOGGER.error("Failed to apply survival changes to Pokemon ${pokemon.uuid}", e)
                    return EditorStatus("apply_failed", error = true)
                }
            }
            else -> return EditorStatus("invalid_request", error = true)
        }
        return EditorStatus("applied", listOf(pokemon.getDisplayName(false).string))
    }
}
