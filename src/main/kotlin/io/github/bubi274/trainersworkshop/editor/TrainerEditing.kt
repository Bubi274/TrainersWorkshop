package io.github.bubi274.trainersworkshop.editor

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.compat.rct.RctCompat
import io.github.bubi274.trainersworkshop.config.WorkshopConfig
import net.minecraft.entity.Entity
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import java.util.UUID

/** Draft edits of one RCT trainer. Only written to the world when Save is pressed. */
class TrainerEditorSession(
    val entityUuid: UUID,
    val trainerId: String,
    val trainerName: String,
    val defaultLootTable: String,
    var team: MutableList<Pokemon>,
    /** Extra data per team slot (battle gimmicks), always the same size and order as [team]. */
    var extras: MutableList<TrainerSlotExtras>,
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
    const val SET_TERA = "trainer_tera"
    const val SET_DYNAMAX = "trainer_dynamax"
    const val SET_GMAX = "trainer_gmax"
    const val HELD_FROM_HAND = "trainer_held_hand"
    const val HELD_CLEAR = "trainer_held_clear"
    const val SET_ALPHA = "trainer_alpha"

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
        val team = savedTeam ?: RctCompat.defaultTeam(entity, server)
        val savedSlots = override?.slots?.takeIf { savedTeam != null && it.size == savedTeam.size }
        val gimmicks = when {
            savedTeam == null -> RctCompat.defaultGimmicks(entity, team)
            savedSlots != null -> savedSlots.map { it.gimmicks }
            // Saved before 2.2.0 (or a slot failed to load): recover the gimmicks from the original team.
            else -> RctCompat.fallbackGimmicks(trainerId, savedTeam)
        }
        return TrainerEditorSession(
            entityUuid = entity.uuid,
            trainerId = trainerId,
            trainerName = RctCompat.trainerName(entity).string,
            defaultLootTable = RctCompat.defaultLootTable(entity),
            team = team.toMutableList(),
            extras = gimmicks.map { TrainerSlotExtras(gimmicks = it) }.toMutableList(),
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
            SET_TERA, SET_DYNAMAX, SET_GMAX -> setGimmick(session, nbt)
            HELD_FROM_HAND -> setHeldItem(player, session, nbt, fromHand = true)
            HELD_CLEAR -> setHeldItem(player, session, nbt, fromHand = false)
            SET_ALPHA -> setAlpha(session, nbt)
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
        val created = species.create(old.level)
        // The slot keeps its battle gimmicks; Gigantamax turns off if the new species has no G-Max form.
        val gimmicks = TrainerGimmickRules.forNewSpecies(session.extras[index].gimmicks, created)
        TrainerGimmickRules.applyToPokemon(created, gimmicks)
        session.team[index] = created
        // A new Pokémon is never an Alpha, so there is nothing to restore later.
        session.extras[index] = TrainerSlotExtras(gimmicks = gimmicks, alphaBackup = null)
        session.teamChanged = true
        return EditorStatus("species_changed", listOf(species.translatedName.string))
    }

    private fun add(session: TrainerEditorSession, nbt: NbtCompound): EditorStatus {
        if (session.team.size >= MAX_TEAM) return EditorStatus("team_full", error = true)
        val species = findSpecies(nbt.getString("species")) ?: return EditorStatus("unknown_species", listOf(nbt.getString("species")), error = true)
        val level = session.team.maxOfOrNull { it.level } ?: DEFAULT_LEVEL
        session.team.add(species.create(level))
        session.extras.add(TrainerSlotExtras())
        session.teamChanged = true
        return EditorStatus("pokemon_added", listOf(species.translatedName.string))
    }

    private fun remove(session: TrainerEditorSession, nbt: NbtCompound): EditorStatus {
        val index = nbt.getInt("index")
        if (index !in session.team.indices) return EditorStatus("pokemon_changed", error = true)
        if (session.team.size <= 1) return EditorStatus("team_min", error = true)
        session.team.removeAt(index)
        session.extras.removeAt(index)
        session.teamChanged = true
        return EditorStatus("pokemon_removed")
    }

    /** Tera type ("" = off), Dynamax or Gigantamax of one slot. Also written onto the Pokémon, like RCT does. */
    private fun setGimmick(session: TrainerEditorSession, nbt: NbtCompound): EditorStatus? {
        val index = nbt.getInt("index")
        val pokemon = session.team.getOrNull(index) ?: return EditorStatus("pokemon_changed", error = true)
        val old = session.extras[index].gimmicks
        val gimmicks = when (nbt.getString("action")) {
            SET_TERA -> {
                val tera = nbt.getString("tera")
                if (tera.isNotEmpty() && tera !in TrainerGimmickRules.teraTypes()) return EditorStatus("invalid_request", error = true)
                old.copy(tera = tera)
            }
            SET_DYNAMAX -> old.copy(dynamax = nbt.getBoolean("value"))
            else -> {
                val gmax = nbt.getBoolean("value")
                if (gmax && !TrainerGimmickRules.canGmax(pokemon)) return EditorStatus("trainer_no_gmax", error = true)
                old.copy(gmax = gmax)
            }
        }
        TrainerGimmickRules.applyToPokemon(pokemon, gimmicks)
        session.extras[index] = session.extras[index].copy(gimmicks = gimmicks)
        session.teamChanged = true
        return null
    }

    /**
     * Alpha toggle. On: Pokemon.isAlpha (size, alpha aspect/eyes, alpha mark) plus real stat boosts, because Cobblemon's
     * alpha battle boost ("Wild Alpha" rule) only exists in wild battles: all IVs 31, +[WorkshopConfigData.alphaTrainerLevelBonus]
     * levels (never above 100) and, like a wild Alpha, a random TM move of the species if it knows none.
     * Off: restores the IVs, level and moves from before.
     */
    private fun setAlpha(session: TrainerEditorSession, nbt: NbtCompound): EditorStatus? {
        val index = nbt.getInt("index")
        val pokemon = session.team.getOrNull(index) ?: return EditorStatus("pokemon_changed", error = true)
        val on = nbt.getBoolean("value")
        if (on == pokemon.isAlpha) return null
        val extras = session.extras[index]
        if (on) {
            val before = PokemonEditing.snapshot(pokemon)
            pokemon.isAlpha = true
            EDITOR_STATS.forEach { pokemon.setIV(it, PokemonEditing.MAX_IV) }
            val cap = minOf(AlphaRules.MAX_LEVEL, PokemonEditing.maxLevel())
            val level = AlphaRules.boostedLevel(pokemon.level, WorkshopConfig.data.alphaTrainerLevelBonus, cap)
            if (level != pokemon.level) pokemon.level = level
            val tmMoves = pokemon.form.moves.tmLearnableMoves().map { it.name }.toSet()
            if (AlphaRules.needsTmMove(before.moves, tmMoves)) {
                (tmMoves - before.moves.toSet()).randomOrNull()?.let { move ->
                    val moves = before.moves.toMutableList()
                    moves[AlphaRules.tmMoveSlot(moves)] = move
                    PokemonEditing.applyMoves(pokemon, moves)
                }
            }
            session.extras[index] = extras.copy(alphaBackup = AlphaBackup(before.ivs, before.level, before.moves))
        } else {
            pokemon.isAlpha = false
            extras.alphaBackup?.let { backup ->
                if (backup.level in 1..PokemonEditing.maxLevel()) pokemon.level = backup.level
                if (backup.ivs.size == EDITOR_STATS.size) PokemonEditing.setIvs(pokemon, backup.ivs)
                if (backup.moves.size == MOVE_SLOTS && backup.moves.any { it.isNotEmpty() }) PokemonEditing.applyMoves(pokemon, backup.moves)
            }
            session.extras[index] = extras.copy(alphaBackup = null)
        }
        session.teamChanged = true
        return null
    }

    /**
     * Held item from the main hand (one item, the hand stack is not used up: Creative only) or cleared.
     * Lets admins give Mega Stones (Mega Showdown reads the held item in battle).
     */
    private fun setHeldItem(player: ServerPlayerEntity, session: TrainerEditorSession, nbt: NbtCompound, fromHand: Boolean): EditorStatus? {
        val pokemon = session.team.getOrNull(nbt.getInt("index")) ?: return EditorStatus("pokemon_changed", error = true)
        if (fromHand) {
            val hand = player.mainHandStack
            if (hand.isEmpty) return EditorStatus("hand_empty", error = true)
            pokemon.swapHeldItem(hand.copyWithCount(1), decrement = false)
        } else {
            pokemon.removeHeldItem()
        }
        session.teamChanged = true
        return null
    }

    /** Adds the item held in the main hand (read from the server-side inventory, client data is not trusted). */
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

    /** The trainer must still exist, be in the same world and be near the player. */
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
        // Only save the team if it changed, so a trainer whose team wasn't changed keeps the original RCT team. The slot extras (gimmicks) are saved with it.
        val saveTeam = session.teamChanged || previous?.team != null
        val team = if (saveTeam) session.team.map { it.saveToNBT(registries, NbtCompound()) } else null
        overrides.put(
            session.entityUuid,
            TrainerOverride(
                trainerId = session.trainerId,
                team = team,
                slots = if (saveTeam) session.extras.toList() else null,
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

    /** What the Battle tab shows for one slot. */
    private fun slotView(pokemon: Pokemon, extras: TrainerSlotExtras, registries: RegistryWrapper.WrapperLookup): NbtCompound =
        extras.toNbt().apply {
            putBoolean("canGmax", TrainerGimmickRules.canGmax(pokemon))
            putString("teraType", pokemon.teraType.id.path)
            putBoolean("alpha", pokemon.isAlpha)
            val held = pokemon.heldItem()
            if (!held.isEmpty) put("heldItem", held.encode(registries))
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
            put("teraTypes", stringList(TrainerGimmickRules.teraTypes()))
            put("slots", NbtList().apply {
                session.team.forEachIndexed { index, pokemon ->
                    add(slotView(pokemon, session.extras[index], registries))
                }
            })
        }
        val slots = session.team.mapIndexed { index, pokemon -> PokemonEditing.slot(index, pokemon) }
        val state = EditorState(EditorMode.TRAINER, slots, PokemonEditing.maxLevel(), PokemonEditing.allBalls(), trainer)
        EditorSessions.sendState(player, state, status)
    }
}
