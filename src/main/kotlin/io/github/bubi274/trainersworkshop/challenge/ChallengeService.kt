package io.github.bubi274.trainersworkshop.challenge

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.BattleRegistry
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

/**
 * Server side of challenge mode: keeps the ownership ledger up to date and runs the player's actions.
 *
 * Ledger: POKEMON_GAINED fires every time a Pokémon's store changes (also party ↔ PC moves), so it is only used to
 * record "this player has this Pokémon"; the first time a UUID is seen is what counts. Party + PC are also swept on
 * login and right before a challenge starts, so nothing is missed.
 */
object ChallengeService {
    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register {
            server = it
            CobblemonForms.clearCache()
        }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { _, _, _ -> CobblemonForms.clearCache() }
        ServerLifecycleEvents.SERVER_STOPPED.register { server = null }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> sweep(handler.player) }

        CobblemonEvents.POKEMON_GAINED.subscribe(Priority.NORMAL) { event ->
            val server = server ?: return@subscribe
            val player = event.playerId
            val pokemon = event.pokemon
            // Only the owner: someone else looking at this PC also gets the event.
            if (pokemon.getOwnerUUID() != player) return@subscribe
            val uuid = pokemon.uuid
            onServerThread(server) { ChallengeState.get(server).recordOwned(player, uuid) }
        }
    }

    private fun onServerThread(server: MinecraftServer, action: () -> Unit) {
        if (server.isOnThread) action() else server.execute(action)
    }

    /** Records every Pokémon in the player's party and PCs. */
    fun sweep(player: ServerPlayerEntity) {
        val state = ChallengeState.get(player.server)
        try {
            val storage = Cobblemon.storage
            storage.getParty(player).forEach { state.recordOwned(player.uuid, it.uuid) }
            storage.getPCs(player.uuid, player.server.registryManager).forEach { pc ->
                pc.forEach { state.recordOwned(player.uuid, it.uuid) }
            }
        } catch (e: Exception) {
            TrainersWorkshop.LOGGER.warn("Could not read the party / PC of {}", player.name.string, e)
        }
    }

    fun data(player: ServerPlayerEntity): PlayerChallengeData = ChallengeState.get(player.server).player(player.uuid)

    /** Changing the team in the middle of a battle is refused (that battle's result wouldn't fit the new team). */
    private fun notInBattle(player: ServerPlayerEntity) {
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            throw ChallengeActionException("You are in a battle. Paste your code after the battle ends.")
        }
    }

    /** Starts a challenge; everything the player has right now counts as owned before the start. */
    fun start(player: ServerPlayerEntity, code: String): ActiveChallenge {
        notInBattle(player)
        sweep(player)
        return ChallengeState.get(player.server).start(player.uuid, code, System.currentTimeMillis())
    }

    fun applyReroll(player: ServerPlayerEntity, code: String): ActiveChallenge {
        notInBattle(player)
        return ChallengeState.get(player.server).applyReroll(player.uuid, code)
    }

    fun rerollCode(player: ServerPlayerEntity): String =
        ChallengeState.get(player.server).rerollCode(player.uuid)

    fun abandon(player: ServerPlayerEntity): ActiveChallenge =
        ChallengeState.get(player.server).abandon(player.uuid)

    /** For later parts (battles): is this Pokémon of [player] obtained after the active challenge started? */
    fun obtainedDuringChallenge(player: UUID, pokemon: UUID): Boolean =
        server?.let { ChallengeState.get(it).player(player).obtainedDuringChallenge(pokemon) } ?: false
}
