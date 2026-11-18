package io.github.bubi274.trainersworkshop.editor

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.abilities.CommonAbilityType
import com.cobblemon.mod.common.api.moves.BenchedMove
import com.cobblemon.mod.common.api.moves.Move
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbilityType
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

/** Reads and writes Pokémon data for the editor screen. Server side only. */
object PokemonEditing {
    const val MAX_IV = 31
    const val MAX_EV = 252
    const val MAX_EV_TOTAL = 510

    fun maxLevel(): Int = Cobblemon.config.maxPokemonLevel

    fun allBalls(): List<String> = PokeBalls.all().map { it.name.toString() }

    fun snapshot(pokemon: Pokemon): PokemonEditData {
        val held = pokemon.heldItem()
        val heldId = if (held.isEmpty) "" else Registries.ITEM.getId(held.item).toString()
        val heldCount = if (held.isEmpty) 0 else held.count
        return PokemonEditData(
            uuid = pokemon.uuid,
            species = pokemon.species.resourceIdentifier.toString(),
            aspects = pokemon.aspects.toList(),
            level = pokemon.level,
            shiny = pokemon.shiny,
            gender = pokemon.gender.name,
            nature = pokemon.nature.name.toString(),
            mintedNature = pokemon.mintedNature?.name?.toString() ?: "",
            ability = pokemon.ability.name,
            abilityForced = pokemon.ability.forced,
            ivs = EDITOR_STATS.map { pokemon.ivs.getOrDefault(it) },
            hyperIvs = EDITOR_STATS.map { pokemon.ivs.hyperTrainedIVs[it] ?: NO_HYPER },
            evs = EDITOR_STATS.map { pokemon.evs.getOrDefault(it) },
            moves = currentMoves(pokemon),
            ball = pokemon.caughtBall.name.toString(),
            currentHealth = pokemon.currentHealth,
            maxHealth = pokemon.maxHealth.coerceAtLeast(1),
            heldItemId = heldId,
            heldItemCount = heldCount,
        )
    }

    fun options(pokemon: Pokemon): PokemonEditOptions {
        val current = currentMoves(pokemon).filter { it.isNotEmpty() }
        val legal = pokemon.form.moves.getAllLegalMoves().map { it.name } + current
        val free = pokemon.allAccessibleMoves.map { it.name } + current
        val learnset = pokemon.form.moves
        val tm = learnset.levelUpMoves.values.flatten().map { it.name } + learnset.tmMoves.map { it.name }
        // Same rule as Cobblemon's TechnicalMachineItem: tmLearnableMoves; only moves that have a Cobblemon TM.
        val cobblemonTm = learnset.tmLearnableMoves().filter { it in TechnicalMachines.moveToTM }.map { it.name }
        val abilities = pokemon.form.abilities.map { it.template.name } + pokemon.ability.name
        val commonAbilities = pokemon.form.abilities.filter { it.type == CommonAbilityType }.map { it.template.name }.distinct()
        val hiddenAbilities = pokemon.form.abilities.filter { it.type == HiddenAbilityType }.map { it.template.name }.distinct()
        return PokemonEditOptions(
            legalMoves = legal.distinct().sorted(),
            freeMoves = free.distinct().sorted(),
            tmMoves = tm.distinct().sorted(),
            cobblemonTmMoves = cobblemonTm.distinct().sorted(),
            eggMoves = learnset.eggMoves.map { it.name }.distinct().sorted(),
            abilities = abilities.distinct(),
            commonAbilities = commonAbilities,
            hiddenAbilities = hiddenAbilities,
            genders = allowedGenders(pokemon).map { it.name },
        )
    }

    fun slot(index: Int, pokemon: Pokemon) = EditorSlot(index, snapshot(pokemon), options(pokemon))

    fun allowedGenders(pokemon: Pokemon): List<Gender> {
        val ratio = pokemon.form.maleRatio
        return when {
            ratio < 0f -> listOf(Gender.GENDERLESS)
            ratio == 0f -> listOf(Gender.FEMALE)
            ratio == 1f -> listOf(Gender.MALE)
            else -> listOf(Gender.MALE, Gender.FEMALE)
        }
    }

    private fun currentMoves(pokemon: Pokemon): List<String> {
        val moves = pokemon.moveSet.getMovesWithNulls()
        return List(MOVE_SLOTS) { moves.getOrNull(it)?.template?.name ?: "" }
    }

    /**
     * Checks the wanted state in creative mode.
     * Returns an error key (to translate) or null if valid.
     */
    fun validateCreative(pokemon: Pokemon, target: PokemonEditData): String? {
        val options = options(pokemon)
        if (target.uuid != pokemon.uuid) return "pokemon_changed"
        if (target.level !in 1..maxLevel()) return "invalid_level"
        if (target.ivs.any { it !in 0..MAX_IV }) return "invalid_ivs"
        if (target.evs.any { it !in 0..MAX_EV } || target.evs.sum() > MAX_EV_TOTAL) return "invalid_evs"
        if (Natures.getNature(Identifier.of(target.nature)) == null) return "invalid_nature"
        if (target.ability !in options.abilities || Abilities.get(target.ability) == null) return "invalid_ability"
        if (target.gender !in options.genders) return "invalid_gender"
        if (PokeBalls.getPokeBall(Identifier.of(target.ball)) == null) return "invalid_ball"
        return validateMoves(pokemon, target.moves, options.legalMoves)
    }

    /** Moves: no duplicates, all exist, all in the allowed list, and no slot that has a move becomes empty. */
    fun validateMoves(pokemon: Pokemon, moves: List<String>, allowed: List<String>): String? {
        if (moves.size != MOVE_SLOTS) return "invalid_moves"
        val chosen = moves.filter { it.isNotEmpty() }
        if (chosen.isEmpty() || chosen.size != chosen.distinct().size) return "invalid_moves"
        if (chosen.any { it !in allowed || Moves.getByName(it) == null }) return "invalid_moves"
        val current = currentMoves(pokemon)
        if (current.indices.any { current[it].isNotEmpty() && moves[it].isEmpty() }) return "invalid_moves"
        return null
    }

    /** Writes every change (already checked with [validateCreative]) to the Pokémon. */
    fun applyCreative(pokemon: Pokemon, target: PokemonEditData) {
        // Level first, because the level-up move list depends on it.
        if (pokemon.level != target.level) pokemon.level = target.level
        Gender.entries.firstOrNull { it.name == target.gender }?.let { if (pokemon.gender != it) pokemon.gender = it }

        val nature = Natures.getNature(Identifier.of(target.nature))
        if (nature != null && (pokemon.nature != nature || pokemon.mintedNature != null)) {
            pokemon.nature = nature
            pokemon.mintedNature = null
        }

        if (pokemon.ability.name != target.ability) {
            val potential = pokemon.form.abilities.firstOrNull { it.template.name == target.ability }
            val template = Abilities.get(target.ability)
            if (potential != null) {
                pokemon.updateAbility(potential.template.create(false, potential.priority))
            } else if (template != null) {
                // The current ability is outside the species pool: keep it "forced" like Cobblemon does.
                pokemon.updateAbility(template.create(true, pokemon.ability.priority))
            }
        }

        setIvs(pokemon, target.ivs)
        setEvs(pokemon, target.evs)
        applyMoves(pokemon, target.moves)
        if (pokemon.shiny != target.shiny) pokemon.shiny = target.shiny
        PokeBalls.getPokeBall(Identifier.of(target.ball))?.let { if (pokemon.caughtBall != it) pokemon.caughtBall = it }
    }

    /** Writes the survival changes (costs already checked with SurvivalCosts). Only changes IVs, EVs, nature (mint), moves, shiny. */
    fun applySurvival(pokemon: Pokemon, current: PokemonEditData, target: PokemonEditData, capMode: BottleCapMode) {
        // 0) Level first (like the Creative version), because level-up moves depend on it.
        applyLevelByExperience(pokemon, target.level)
        // 1) Bottle Cap / Withered Bottle Cap: the same calls as Only Bottle Caps ([applyBottleCap]).
        EDITOR_STATS.forEachIndexed { i, stat ->
            SurvivalCosts.capValue(current, target, i, capMode)?.let { applyBottleCap(pokemon, stat, it, capMode) }
        }
        // TODO(build): test in game that the result is exactly the same as eating the candies one by one (see the "effective IV" tooltip).
        // 2) IV candies: like Cobblemon's HyperTrainingItem, each candy calls
        //    hyperTrainIV(stat, effective IV ± 1); the base IV doesn't change.
        EDITOR_STATS.forEachIndexed { i, stat ->
            val goal = target.effectiveIv(i)
            repeat(SurvivalCosts.MAX_IV + 1) {
                val effective = pokemon.ivs.getEffectiveBattleIV(stat)
                if (effective == goal) return@forEachIndexed
                pokemon.hyperTrainIV(stat, if (goal > effective) effective + 1 else effective - 1)
                // A Cobblemon event can cancel hyper training: stop so the loop doesn't run forever.
                if (pokemon.ivs.getEffectiveBattleIV(stat) == effective) return@forEachIndexed
            }
        }
        // The IVs must now be exactly the planned ones; otherwise Apply restores the Pokémon and refunds the items.
        EDITOR_STATS.forEachIndexed { i, stat ->
            val base = pokemon.ivs.getOrDefault(stat)
            val effective = pokemon.ivs.getEffectiveBattleIV(stat)
            check(base == target.ivs[i] && effective == target.effectiveIv(i)) {
                "Expected $stat IV ${target.ivs[i]} (effective ${target.effectiveIv(i)}) but got $base (effective $effective)"
            }
        }
        setEvs(pokemon, target.evs)
        if (target.effectiveNature != current.effectiveNature) {
            // Exactly like using a Cobblemon mint: only mintedNature is set.
            Natures.getNature(Identifier.of(target.mintedNature))?.let { pokemon.mintedNature = it }
        }
        if (target.ability != current.ability) {
            // Like Cobblemon's AbilityChangeItem / AbilityTypeChanger: forced = false, priority = potential.priority.
            val potential = pokemon.form.abilities.firstOrNull { it.template.name == target.ability }
            if (potential != null) {
                pokemon.updateAbility(potential.template.create(false, potential.priority))
            }
        }
        // Only change moves if the player picked some: otherwise the moves just learned on level-up are kept.
        if (target.moves != current.moves) applyMoves(pokemon, target.moves)
        if (target.shiny && !pokemon.shiny) pokemon.shiny = true
    }

    /**
     * Raises the level by adding exactly the EXP still missing to reach [level] (Cobblemon's EXP API),
     * so the Pokémon learns level-up moves like with candies. Doesn't call evolve: Cobblemon sends the evolution
     * notification as usual and the player chooses. Leftover EXP of the last candy is dropped.
     * Throws if the final level is wrong (so Apply refunds the candies and restores the Pokémon).
     */
    fun applyLevelByExperience(pokemon: Pokemon, level: Int) {
        if (level <= pokemon.level) return
        val missing = pokemon.getExperienceToLevel(level)
        if (missing > 0) pokemon.addExperience(SidemodExperienceSource(TrainersWorkshop.MOD_ID), missing)
        check(pokemon.level == level) { "Expected level $level but got ${pokemon.level}" }
    }

    /**
     * Uses one Bottle Cap on [stat] exactly like Only Bottle Caps (BottleCap.applyToPokemon in OBC 1.3.0 / 1.5.0):
     * Pokemon.setIV unless the mode is hyper training only, then IVs.setHyperTrainedIV unless the mode is base IV only.
     */
    private fun applyBottleCap(pokemon: Pokemon, stat: Stat, value: Int, capMode: BottleCapMode) {
        if (capMode != BottleCapMode.HYPER_TRAIN) pokemon.setIV(stat, value)
        if (capMode != BottleCapMode.BASE_IV) pokemon.ivs.setHyperTrainedIV(stat, value)
    }

    fun setIvs(pokemon: Pokemon, ivs: List<Int>) {
        EDITOR_STATS.forEachIndexed { i, stat ->
            if (pokemon.ivs.getOrDefault(stat) != ivs[i]) pokemon.setIV(stat, ivs[i])
        }
    }

    fun setEvs(pokemon: Pokemon, evs: List<Int>) {
        if (EDITOR_STATS.indices.all { pokemon.evs.getOrDefault(EDITOR_STATS[it]) == evs[it] }) return
        // Set to 0 first so the 510 total limit doesn't get in the way when moving EVs between stats.
        EDITOR_STATS.forEach { pokemon.setEV(it, 0) }
        EDITOR_STATS.forEachIndexed { i, stat -> if (evs[i] > 0) pokemon.setEV(stat, evs[i]) }
    }

    /**
     * Works out the final list of 4 moves first, then writes it once with event emitting turned off
     * (doWithoutEmitting / doThenEmit), so exactly 1 moveset update packet and 1 benchedMoves update packet are sent.
     */
    fun applyMoves(pokemon: Pokemon, moves: List<String>) {
        if (currentMoves(pokemon) == moves) return
        val oldMoves = pokemon.moveSet.getMovesWithNulls().filterNotNull()
        val oldByTemplate = oldMoves.associateBy { it.template }
        val benchedByTemplate = pokemon.benchedMoves.associateBy { it.moveTemplate }

        val finalMoves = List(MOVE_SLOTS) { slot ->
            val name = moves.getOrNull(slot).orEmpty()
            val template = if (name.isEmpty()) null else Moves.getByName(name)
            when {
                template == null -> null
                oldByTemplate.containsKey(template) -> {
                    val old = oldByTemplate.getValue(template)
                    Move(template, old.currentPp, old.raisedPpStages)
                }
                else -> {
                    val stages = benchedByTemplate[template]?.ppRaisedStages ?: 0
                    template.create().also { move ->
                        if (stages > 0) {
                            move.raisedPpStages = stages
                            move.currentPp = move.maxPp
                        }
                    }
                }
            }
        }

        val newTemplates = finalMoves.mapNotNull { it?.template }.toSet()
        val removedOldMoves = oldMoves.filter { it.template !in newTemplates }

        pokemon.moveSet.doWithoutEmitting {
            for (slot in 0 until MOVE_SLOTS) {
                pokemon.moveSet.setMove(slot, finalMoves[slot])
            }
        }
        pokemon.moveSet.update()

        val benchedChanged = removedOldMoves.isNotEmpty() || pokemon.benchedMoves.any { it.moveTemplate in newTemplates }
        if (benchedChanged) {
            pokemon.benchedMoves.doThenEmit {
                newTemplates.forEach { pokemon.benchedMoves.remove(it) }
                removedOldMoves.forEach { pokemon.benchedMoves.add(BenchedMove(it.template, it.raisedPpStages)) }
            }
        }
    }
}
