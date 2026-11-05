package io.github.bubi274.trainersworkshop.editor

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.compat.rct.RctCompat
import net.minecraft.entity.Entity
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import java.util.UUID

/** Bản nháp đang chỉnh của một trainer RCT. Chỉ ghi vào thế giới khi bấm Save. */
class TrainerEditorSession(
    val entityUuid: UUID,
    val trainerId: String,
    val trainerName: String,
    val defaultLootTable: String,
    var team: MutableList<Pokemon>,
    var teamChanged: Boolean,
    var loot: MutableList<LootEntry>,
    var lootMode: LootMode,
    var keepDefaultLoot: Boolean,
    var customized: Boolean,
) : EditorSession(EditorMode.TRAINER)

object TrainerEditing {
    const val SAVE = "trainer_save"
    const val RESET = "trainer_reset"
    const val EDIT = "trainer_edit"
    const val SET_SPECIES = "trainer_species"
    const val ADD = "trainer_add"
    const val REMOVE = "trainer_remove"
    const val LOOT_ADD_HAND = "loot_add_hand"
    const val LOOT_SET = "loot_set"
    const val LOOT_REMOVE = "loot_remove"
    const val LOOT_MODE = "loot_mode"
    const val LOOT_KEEP_DEFAULT = "loot_keep_default"

    private const val MAX_TEAM = 6
    private const val DEFAULT_LEVEL = 50
    private const val MAX_DISTANCE = 32.0

    fun open(player: ServerPlayerEntity, entity: Entity) {
        if (!player.isCreative) {
            player.sendMessage(Text.translatable("message.${TrainersWorkshop.MOD_ID}.no_permission").formatted(Formatting.RED), true)
            return
        }
        val session = loadSession(player, entity)
        EditorSessions.put(player, session)
        sendState(player, session, if (session.team.isEmpty()) EditorStatus("trainer_no_team", error = true) else null)
    }

    private fun loadSession(player: ServerPlayerEntity, entity: Entity): TrainerEditorSession {
        val server = player.server
        val trainerId = RctCompat.trainerId(entity)
        val override = TrainerOverrides.get(server)[entity.uuid]?.takeIf { it.trainerId == trainerId }
        val savedTeam = override?.team?.mapNotNull { runCatching { Pokemon.loadFromNBT(server.registryManager, it) }.getOrNull() }
        return TrainerEditorSession(
            entityUuid = entity.uuid,
            trainerId = trainerId,
            trainerName = RctCompat.trainerName(entity).string,
            defaultLootTable = RctCompat.defaultLootTable(entity),
            team = (savedTeam ?: RctCompat.defaultTeam(entity, server)).toMutableList(),
            teamChanged = false,
            loot = override?.loot?.toMutableList() ?: mutableListOf(),
            lootMode = override?.lootMode ?: LootMode.EVERY_WIN,
            keepDefaultLoot = override?.keepDefaultLoot ?: true,
            customized = override != null,
        )
    }

    fun handle(player: ServerPlayerEntity, session: TrainerEditorSession, nbt: NbtCompound) {
        if (!player.isCreative) {
            player.sendMessage(Text.translatable("message.${TrainersWorkshop.MOD_ID}.no_permission").formatted(Formatting.RED), true)
            sendState(player, session, EditorStatus("no_permission", error = true))
            return
        }
        val status = when (nbt.getString("action")) {
            EDIT -> edit(session, nbt)
            SET_SPECIES -> setSpecies(session, nbt)
            ADD -> add(session, nbt)
            REMOVE -> remove(session, nbt)
            LOOT_ADD_HAND -> addHandItem(player, session)
            LOOT_SET -> setLoot(session, nbt)
            LOOT_REMOVE -> removeLoot(session, nbt)
            LOOT_MODE -> {
                session.lootMode = enumValueOrNull<LootMode>(nbt.getString("mode")) ?: session.lootMode
                null
            }
            LOOT_KEEP_DEFAULT -> {
                session.keepDefaultLoot = nbt.getBoolean("keep")
                null
            }
            SAVE -> save(player, session)
            RESET -> return reset(player, session)
            else -> EditorStatus("invalid_request", error = true)
        }
        sendState(player, session, status)
    }

    private fun edit(session: TrainerEditorSession, nbt: NbtCompound): EditorStatus? {
        val pokemon = session.team.getOrNull(nbt.getInt("index")) ?: return EditorStatus("pokemon_changed", error = true)
        val target = PokemonEditData.fromNbt(nbt.getCompound("data"))
        PokemonEditing.validateCreative(pokemon, target)?.let { return EditorStatus(it, error = true) }
        PokemonEditing.applyCreative(pokemon, target)
        session.teamChanged = true
        return null
    }

    private fun findSpecies(name: String): Species? {
        val text = name.trim().lowercase()
        if (text.isEmpty()) return null
        val id = Identifier.tryParse(if (':' in text) text else "cobblemon:$text")
        val species = id?.let { PokemonSpecies.getByIdentifier(it) } ?: PokemonSpecies.getByName(text)
        return species?.takeIf { it.implemented }
    }

    private fun setSpecies(session: TrainerEditorSession, nbt: NbtCompound): EditorStatus {
        val index = nbt.getInt("index")
        val old = session.team.getOrNull(index) ?: return EditorStatus("pokemon_changed", error = true)
        val species = findSpecies(nbt.getString("species")) ?: return EditorStatus("unknown_species", listOf(nbt.getString("species")), error = true)
        session.team[index] = species.create(old.level)
        session.teamChanged = true
        return EditorStatus("species_changed", listOf(species.translatedName.string))
    }

    private fun add(session: TrainerEditorSession, nbt: NbtCompound): EditorStatus {
        if (session.team.size >= MAX_TEAM) return EditorStatus("team_full", error = true)
        val species = findSpecies(nbt.getString("species")) ?: return EditorStatus("unknown_species", listOf(nbt.getString("species")), error = true)
        val level = session.team.maxOfOrNull { it.level } ?: DEFAULT_LEVEL
        session.team.add(species.create(level))
        session.teamChanged = true
        return EditorStatus("pokemon_added", listOf(species.translatedName.string))
    }

    private fun remove(session: TrainerEditorSession, nbt: NbtCompound): EditorStatus {
        val index = nbt.getInt("index")
        if (index !in session.team.indices) return EditorStatus("pokemon_changed", error = true)
        if (session.team.size <= 1) return EditorStatus("team_min", error = true)
        session.team.removeAt(index)
        session.teamChanged = true
        return EditorStatus("pokemon_removed")
    }

    /** Thêm vật phẩm đang cầm ở tay chính (lấy từ túi phía server, không tin dữ liệu client). */
    private fun addHandItem(player: ServerPlayerEntity, session: TrainerEditorSession): EditorStatus {
        val hand = player.mainHandStack
        if (hand.isEmpty) return EditorStatus("hand_empty", error = true)
        if (session.loot.size >= LootEntry.MAX_ENTRIES) return EditorStatus("loot_full", error = true)
        session.loot.add(LootEntry(hand.copyWithCount(1), 1f))
        return EditorStatus("loot_added", listOf(hand.name.string))
    }

    private fun setLoot(session: TrainerEditorSession, nbt: NbtCompound): EditorStatus? {
        val index = nbt.getInt("index")
        val entry = session.loot.getOrNull(index) ?: return EditorStatus("invalid_request", error = true)
        val count = nbt.getInt("count").coerceIn(1, entry.stack.maxCount)
        val chance = nbt.getFloat("chance").coerceIn(0.01f, 1f)
        session.loot[index] = LootEntry(entry.stack.copyWithCount(count), chance)
        return null
    }

    private fun removeLoot(session: TrainerEditorSession, nbt: NbtCompound): EditorStatus? {
        val index = nbt.getInt("index")
        if (index !in session.loot.indices) return EditorStatus("invalid_request", error = true)
        session.loot.removeAt(index)
        return null
    }

    /** Trainer phải còn tồn tại, cùng thế giới và ở gần người chơi. */
    private fun findTrainer(player: ServerPlayerEntity, session: TrainerEditorSession): Entity? {
        val entity = player.serverWorld.getEntity(session.entityUuid) ?: return null
        if (!entity.isAlive || !RctCompat.isTrainer(entity)) return null
        if (entity.squaredDistanceTo(player) > MAX_DISTANCE * MAX_DISTANCE) return null
        return entity
    }

    private fun save(player: ServerPlayerEntity, session: TrainerEditorSession): EditorStatus {
        val entity = findTrainer(player, session) ?: return EditorStatus("trainer_gone", error = true)
        if (session.team.isEmpty()) return EditorStatus("team_min", error = true)
        val overrides = TrainerOverrides.get(player.server)
        val previous = overrides[session.entityUuid]
        val registries = player.server.registryManager
        // Chỉ lưu đội khi đã đổi, để trainer chưa đổi đội vẫn dùng đội gốc (giữ gimmick của RCT).
        val team = if (session.teamChanged || previous?.team != null) {
            session.team.map { it.saveToNBT(registries, NbtCompound()) }
        } else {
            null
        }
        overrides.put(
            session.entityUuid,
            TrainerOverride(
                trainerId = session.trainerId,
                team = team,
                loot = session.loot.toList(),
                lootMode = session.lootMode,
                keepDefaultLoot = session.keepDefaultLoot,
                claimedBy = previous?.claimedBy ?: mutableSetOf(),
            ),
        )
        RctCompat.makePersistent(entity)
        RctCompat.forget(session.entityUuid)
        session.customized = true
        session.teamChanged = false
        return EditorStatus("trainer_saved", listOf(session.trainerName))
    }

    private fun reset(player: ServerPlayerEntity, session: TrainerEditorSession) {
        val entity = findTrainer(player, session)
        if (entity == null) {
            sendState(player, session, EditorStatus("trainer_gone", error = true))
            return
        }
        TrainerOverrides.get(player.server).remove(session.entityUuid)
        RctCompat.forget(session.entityUuid)
        val fresh = loadSession(player, entity)
        EditorSessions.put(player, fresh)
        sendState(player, fresh, EditorStatus("trainer_reset", listOf(fresh.trainerName)))
    }

    fun sendState(player: ServerPlayerEntity, session: TrainerEditorSession, status: EditorStatus?) {
        val registries = player.server.registryManager
        val trainer = NbtCompound().apply {
            putString("name", session.trainerName)
            putString("trainerId", session.trainerId)
            putString("defaultLootTable", session.defaultLootTable)
            putBoolean("customized", session.customized)
            putString("lootMode", session.lootMode.name)
            putBoolean("keepDefaultLoot", session.keepDefaultLoot)
            put("loot", NbtList().apply { session.loot.forEach { add(it.toNbt(registries)) } })
        }
        val slots = session.team.mapIndexed { index, pokemon -> PokemonEditing.slot(index, pokemon) }
        val state = EditorState(EditorMode.TRAINER, slots, PokemonEditing.maxLevel(), PokemonEditing.allBalls(), trainer)
        EditorSessions.sendState(player, state, status)
    }
}
