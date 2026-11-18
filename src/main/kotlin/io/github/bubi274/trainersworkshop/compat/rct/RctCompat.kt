package io.github.bubi274.trainersworkshop.compat.rct

import com.cobblemon.mod.common.pokemon.Pokemon
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import java.util.UUID

/**
 * Safe gateway to Radical Cobblemon Trainers (an optional dependency).
 * This class doesn't mention any RCT class, so it can be called even when RCT isn't installed;
 * everything that needs RCT lives in [RctAccess] and is only loaded when [loaded] is true.
 */
object RctCompat {
    val loaded: Boolean by lazy { FabricLoader.getInstance().isModLoaded("rctmod") }

    fun init() {
        if (!loaded) return
        RctAccess.checkHooks()
    }

    fun isTrainer(entity: Entity): Boolean = loaded && RctAccess.isTrainer(entity)

    // The functions below are only called after isTrainer(entity) returned true.
    fun trainerId(entity: Entity): String = RctAccess.trainerId(entity)
    fun trainerName(entity: Entity): Text = RctAccess.trainerName(entity)
    fun defaultTeam(entity: Entity, server: MinecraftServer): List<Pokemon> = RctAccess.defaultTeam(entity, server)
    fun defaultLootTable(entity: Entity): String = RctAccess.defaultLootTable(entity)
    fun makePersistent(entity: Entity) = RctAccess.makePersistent(entity)

    /**
     * The player's level cap from RCT, server side (TrainerManager.getData(player).levelCap).
     * Returns null without RCT or when RCT throws; then only Cobblemon's max level is used.
     */
    fun serverLevelCap(player: PlayerEntity): Int? = if (loaded) RctAccess.serverLevelCap(player) else null

    /** Level cap to show in the UI, client side (PlayerState.get(player).levelCap). null without RCT or on error. */
    fun clientLevelCap(player: PlayerEntity): Int? = if (loaded) RctAccess.clientLevelCap(player) else null

    /** Drops the team built for battles (after saving or resetting). */
    fun forget(entityUuid: UUID) {
        if (loaded) RctAccess.forget(entityUuid)
    }

    internal fun warn(message: String, error: Throwable? = null) {
        TrainersWorkshop.LOGGER.warn("[RCT compat] $message", error)
    }
}
