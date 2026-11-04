package io.github.bubi274.trainersworkshop.editor

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.util.Identifier

/** Đọc và ghi dữ liệu Pokémon cho màn hình chỉnh sửa. Chỉ dùng ở phía server. */
object PokemonEditing {
    const val MAX_IV = 31
    const val MAX_EV = 252
    const val MAX_EV_TOTAL = 510

    fun maxLevel(): Int = Cobblemon.config.maxPokemonLevel

    fun allBalls(): List<String> = PokeBalls.all().map { it.name.toString() }

    fun snapshot(pokemon: Pokemon) = PokemonEditData(
        uuid = pokemon.uuid,
        species = pokemon.species.resourceIdentifier.toString(),
        aspects = pokemon.aspects.toList(),
        level = pokemon.level,
        shiny = pokemon.shiny,
        gender = pokemon.gender.name,
        nature = pokemon.nature.name.toString(),
        mintedNature = pokemon.mintedNature?.name?.toString() ?: "",
        ability = pokemon.ability.name,
        ivs = EDITOR_STATS.map { pokemon.ivs.getOrDefault(it) },
        evs = EDITOR_STATS.map { pokemon.evs.getOrDefault(it) },
        moves = currentMoves(pokemon),
        ball = pokemon.caughtBall.name.toString(),
    )

    fun options(pokemon: Pokemon): PokemonEditOptions {
        val current = currentMoves(pokemon).filter { it.isNotEmpty() }
        val legal = pokemon.form.moves.getAllLegalMoves().map { it.name } + current
        val free = pokemon.allAccessibleMoves.map { it.name } + current
        val learnset = pokemon.form.moves
        val tm = learnset.levelUpMoves.values.flatten().map { it.name } + learnset.tmMoves.map { it.name }
        val abilities = pokemon.form.abilities.map { it.template.name } + pokemon.ability.name
        return PokemonEditOptions(
            legalMoves = legal.distinct().sorted(),
            freeMoves = free.distinct().sorted(),
            tmMoves = tm.distinct().sorted(),
            abilities = abilities.distinct(),
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
     * Kiểm tra trạng thái mong muốn ở chế độ creative.
     * Trả về khoá lỗi (để dịch) hoặc null nếu hợp lệ.
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

    /** Chiêu: không trùng, đều tồn tại, nằm trong danh sách cho phép, không làm trống ô đang có chiêu. */
    fun validateMoves(pokemon: Pokemon, moves: List<String>, allowed: List<String>): String? {
        if (moves.size != MOVE_SLOTS) return "invalid_moves"
        val chosen = moves.filter { it.isNotEmpty() }
        if (chosen.isEmpty() || chosen.size != chosen.distinct().size) return "invalid_moves"
        if (chosen.any { it !in allowed || Moves.getByName(it) == null }) return "invalid_moves"
        val current = currentMoves(pokemon)
        if (current.indices.any { current[it].isNotEmpty() && moves[it].isEmpty() }) return "invalid_moves"
        return null
    }

    /** Ghi mọi thay đổi (đã kiểm tra bằng [validateCreative]) vào Pokémon. */
    fun applyCreative(pokemon: Pokemon, target: PokemonEditData) {
        // Level trước, vì danh sách chiêu theo level phụ thuộc vào nó.
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
                // Ability hiện tại nằm ngoài pool của loài: giữ dạng "forced" như Cobblemon.
                pokemon.updateAbility(template.create(true, pokemon.ability.priority))
            }
        }

        setIvs(pokemon, target.ivs)
        setEvs(pokemon, target.evs)
        applyMoves(pokemon, target.moves)
        if (pokemon.shiny != target.shiny) pokemon.shiny = target.shiny
        PokeBalls.getPokeBall(Identifier.of(target.ball))?.let { if (pokemon.caughtBall != it) pokemon.caughtBall = it }
    }

    /** Ghi thay đổi survival (đã kiểm tra chi phí bằng SurvivalCosts). Chỉ đổi IV, EV, nature (mint), chiêu, shiny. */
    fun applySurvival(pokemon: Pokemon, current: PokemonEditData, target: PokemonEditData) {
        setIvs(pokemon, target.ivs)
        setEvs(pokemon, target.evs)
        if (target.effectiveNature != current.effectiveNature) {
            // Giống hệt khi dùng mint của Cobblemon: chỉ đặt mintedNature.
            Natures.getNature(Identifier.of(target.mintedNature))?.let { pokemon.mintedNature = it }
        }
        applyMoves(pokemon, target.moves)
        if (target.shiny && !pokemon.shiny) pokemon.shiny = true
    }

    fun setIvs(pokemon: Pokemon, ivs: List<Int>) {
        EDITOR_STATS.forEachIndexed { i, stat ->
            if (pokemon.ivs.getOrDefault(stat) != ivs[i]) pokemon.setIV(stat, ivs[i])
        }
    }

    fun setEvs(pokemon: Pokemon, evs: List<Int>) {
        if (EDITOR_STATS.indices.all { pokemon.evs.getOrDefault(EDITOR_STATS[it]) == evs[it] }) return
        // Đưa về 0 trước để không vướng giới hạn tổng 510 khi chuyển EV giữa các chỉ số.
        EDITOR_STATS.forEach { pokemon.setEV(it, 0) }
        EDITOR_STATS.forEachIndexed { i, stat -> if (evs[i] > 0) pokemon.setEV(stat, evs[i]) }
    }

    /** Đổi chiêu giống cách màn hình Summary của Cobblemon làm (chiêu cũ được cất vào danh sách đã học). */
    fun applyMoves(pokemon: Pokemon, moves: List<String>) {
        for (slot in 0 until MOVE_SLOTS) {
            val wanted = moves[slot]
            if (wanted.isEmpty()) continue
            val old = pokemon.moveSet.getMovesWithNulls().getOrNull(slot)
            if (old?.template?.name == wanted) continue
            val template = Moves.getByName(wanted) ?: continue
            if (old == null) {
                pokemon.moveSet.setMove(slot, template.create())
                pokemon.benchedMoves.remove(template)
            } else {
                pokemon.exchangeMove(old.template, template)
            }
        }
    }
}
