package io.github.bubi274.trainersworkshop.compat.rct

import com.cobblemon.mod.common.pokemon.Pokemon
import com.gitlab.srcmc.rctapi.api.trainer.Trainer
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC
import com.gitlab.srcmc.rctapi.api.trainer.TrainerRegistry
import com.gitlab.srcmc.rctmod.ModCommon
import com.gitlab.srcmc.rctmod.api.RCTMod
import com.gitlab.srcmc.rctmod.api.data.sync.PlayerState
import com.gitlab.srcmc.rctmod.world.entities.TrainerMob
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.editor.LootMode
import io.github.bubi274.trainersworkshop.editor.TrainerOverrides
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.server.MinecraftServer
import java.util.UUID

/** Everything that touches RCT classes. Only loaded when RCT is installed (see RctCompat). */
object RctAccess {
    private const val ID_PREFIX = "${TrainersWorkshop.MOD_ID}_"

    /** Team built for each trainer: entity UUID -> (edit version, TrainerNPC). */
    private val built = HashMap<UUID, Pair<Int, TrainerNPC>>()

    private val registry: TrainerRegistry get() = ModCommon.RCT.trainerRegistry

    private fun registryId(entityUuid: UUID) = ID_PREFIX + entityUuid

    fun isTrainer(entity: Entity): Boolean = entity is TrainerMob

    fun trainerId(entity: Entity): String = (entity as TrainerMob).trainerId ?: ""

    fun trainerName(entity: Entity): Text = (entity as TrainerMob).displayName ?: Text.literal(trainerId(entity))

    fun defaultTeam(entity: Entity, server: MinecraftServer): List<Pokemon> {
        val npc = registry.getById(trainerId(entity), TrainerNPC::class.java) ?: return emptyList()
        return npc.team.map { it.clone(false, server.registryManager) }
    }

    fun defaultLootTable(entity: Entity): String =
        RCTMod.getInstance().trainerManager.getData(entity as TrainerMob)?.lootTableResource?.toString() ?: ""

    fun makePersistent(entity: Entity) = (entity as TrainerMob).setPersistent(true)

    fun forget(entityUuid: UUID) {
        built.remove(entityUuid)
        runCatching { registry.unregisterById(registryId(entityUuid)) }
    }

    private var levelCapWarned = false

    /** Calls the RCT level cap API; any error (version change, missing class...) returns null and is logged only once. */
    private fun levelCapOrNull(read: () -> Int): Int? = try {
        read()
    } catch (e: Throwable) {
        if (!levelCapWarned) {
            levelCapWarned = true
            RctCompat.warn("Could not read the player's level cap; using the Cobblemon max level.", e)
        }
        null
    }

    fun serverLevelCap(player: PlayerEntity): Int? =
        levelCapOrNull { RCTMod.getInstance().trainerManager.getData(player).levelCap }

    fun clientLevelCap(player: PlayerEntity): Int? =
        levelCapOrNull { PlayerState.get(player).levelCap }

    /** Startup check: do the RCT methods the mixins hook into still exist? */
    fun checkHooks() {
        val ok = runCatching {
            RCTMod::class.java.getMethod("makeBattle", TrainerMob::class.java, PlayerEntity::class.java)
            TrainerMob::class.java.getDeclaredMethod("dropBattleLoot", Identifier::class.java, PlayerEntity::class.java)
        }.isSuccess
        if (!ok) RctCompat.warn("This RCT version is not supported: Trainer Editor changes will not apply in battles.")
    }

    /**
     * Called from the mixin when RCT starts a battle: returns a TrainerNPC with the edited team, or null to use the original team.
     */
    @JvmStatic
    fun editedTrainer(mob: TrainerMob): Trainer? = try {
        buildTrainer(mob)
    } catch (e: Exception) {
        RctCompat.warn("Could not build the edited team for ${mob.uuid}; using the default team.", e)
        null
    }

    private fun buildTrainer(mob: TrainerMob): Trainer? {
        val server = mob.server ?: return null
        val override = TrainerOverrides.get(server)[mob.uuid] ?: return null
        val team = override.team ?: return null
        if (override.trainerId != mob.trainerId || team.isEmpty()) return null

        val id = registryId(mob.uuid)
        built[mob.uuid]?.let { (version, npc) ->
            if (version == override.version && registry.getById(id) === npc) return npc
        }
        val original = registry.getById(mob.trainerId, TrainerNPC::class.java) ?: return null
        val pokemon = team.map { Pokemon.loadFromNBT(server.registryManager, it) }.toTypedArray()
        // TODO(rct): gimmicks (Mega/Tera/Dynamax) are linked to the original Pokémon UUIDs, so an edited team usually loses them.
        val npc = TrainerNPC(
            original.name,
            pokemon,
            TrainerNPC.GimmicksMap(original.gimmicks),
            original.bag,
            original.battleTheme,
            original.battleAI,
            mob,
        )
        runCatching { registry.unregisterById(id) }
        registry.registerNPC(id, npc)
        built[mob.uuid] = Pair(override.version, npc)
        return npc
    }

    /**
     * Called from the mixin when a trainer is defeated and RCT is about to drop loot.
     * Drops the custom loot; returns true if RCT's default loot table should be skipped.
     */
    @JvmStatic
    fun dropEditedLoot(mob: TrainerMob, player: PlayerEntity): Boolean = try {
        dropLoot(mob, player)
    } catch (e: Exception) {
        RctCompat.warn("Could not drop custom loot for ${mob.uuid}.", e)
        false
    }

    private fun dropLoot(mob: TrainerMob, player: PlayerEntity): Boolean {
        val server = mob.server ?: return false
        val overrides = TrainerOverrides.get(server)
        val override = overrides[mob.uuid] ?: return false
        if (override.trainerId != mob.trainerId) return false

        if (override.loot.isNotEmpty()) {
            val give = when (override.lootMode) {
                LootMode.EVERY_WIN -> true
                LootMode.FIRST_WIN_PER_PLAYER -> override.claimedBy.add(player.uuid).also { if (it) overrides.markDirty() }
            }
            if (give) {
                override.loot.forEach { entry ->
                    if (mob.random.nextFloat() < entry.chance) mob.dropStack(entry.stack.copy())
                }
            }
        }
        return !override.keepDefaultLoot
    }
}
