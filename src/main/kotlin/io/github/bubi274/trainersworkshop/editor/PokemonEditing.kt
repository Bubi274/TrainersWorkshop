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
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbilityType
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

/** Đọc và ghi dữ liệu Pokémon cho màn hình chỉnh sửa. Chỉ dùng ở phía server. */
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
        val abilities = pokemon.form.abilities.map { it.template.name } + pokemon.ability.name
        val commonAbilities = pokemon.form.abilities.filter { it.type == CommonAbilityType }.map { it.template.name }.distinct()
        val hiddenAbilities = pokemon.form.abilities.filter { it.type == HiddenAbilityType }.map { it.template.name }.distinct()
        return PokemonEditOptions(
            legalMoves = legal.distinct().sorted(),
            freeMoves = free.distinct().sorted(),
            tmMoves = tm.distinct().sorted(),
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
        // 0) Level trước (giống bản Creative), vì chiêu học theo level phụ thuộc vào nó.
        applyLevelByExperience(pokemon, target.level)
        // 1) Bottle Cap / Withered Bottle Cap: Only Bottle Caps đổi IV gốc bằng Pokemon.setIV.
        setIvs(pokemon, target.ivs)
        // TODO(build): thử trong game rằng kết quả giống hệt khi ăn từng viên kẹo (xem tooltip "effective IV").
        // 2) Kẹo IV: giống HyperTrainingItem của Cobblemon, mỗi kẹo gọi
        //    hyperTrainIV(stat, IV có hiệu lực ± 1); IV gốc không đổi.
        EDITOR_STATS.forEachIndexed { i, stat ->
            if (target.hyperIvs[i] == current.hyperIvs[i]) return@forEachIndexed
            val goal = target.effectiveIv(i)
            repeat(SurvivalCosts.MAX_IV + 1) {
                val effective = pokemon.ivs.getEffectiveBattleIV(stat)
                if (effective == goal) return@forEachIndexed
                pokemon.hyperTrainIV(stat, if (goal > effective) effective + 1 else effective - 1)
                // Sự kiện của Cobblemon có thể huỷ việc hyper train: dừng để không lặp mãi.
                if (pokemon.ivs.getEffectiveBattleIV(stat) == effective) return@forEachIndexed
            }
        }
        setEvs(pokemon, target.evs)
        if (target.effectiveNature != current.effectiveNature) {
            // Giống hệt khi dùng mint của Cobblemon: chỉ đặt mintedNature.
            Natures.getNature(Identifier.of(target.mintedNature))?.let { pokemon.mintedNature = it }
        }
        if (target.ability != current.ability) {
            // Giống AbilityChangeItem / AbilityTypeChanger của Cobblemon: forced = false, priority = potential.priority.
            val potential = pokemon.form.abilities.firstOrNull { it.template.name == target.ability }
            if (potential != null) {
                pokemon.updateAbility(potential.template.create(false, potential.priority))
            }
        }
        // Chỉ đổi chiêu khi người chơi có chọn: nếu không, chiêu Pokémon vừa học khi lên level được giữ nguyên.
        if (target.moves != current.moves) applyMoves(pokemon, target.moves)
        if (target.shiny && !pokemon.shiny) pokemon.shiny = true
    }

    /**
     * Tăng level bằng cách cộng đúng số EXP còn thiếu để tới [level] (API EXP của Cobblemon),
     * để Pokémon học chiêu theo level như khi dùng kẹo. Không gọi evolve: Cobblemon tự gửi thông báo
     * tiến hoá như bình thường và người chơi tự chọn. Phần EXP dư của viên kẹo cuối bị bỏ.
     * Ném lỗi nếu level cuối cùng không đúng (để Apply hoàn trả kẹo và khôi phục Pokémon).
     */
    fun applyLevelByExperience(pokemon: Pokemon, level: Int) {
        if (level <= pokemon.level) return
        val missing = pokemon.getExperienceToLevel(level)
        if (missing > 0) pokemon.addExperience(SidemodExperienceSource(TrainersWorkshop.MOD_ID), missing)
        check(pokemon.level == level) { "Expected level $level but got ${pokemon.level}" }
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

    /**
     * Tính sẵn danh sách 4 chiêu cuối cùng rồi ghi một lần duy nhất trong cơ chế tắt phát sự kiện
     * (doWithoutEmitting / doThenEmit) để chỉ gửi đúng 1 gói cập nhật moveset và 1 gói cập nhật benchedMoves.
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
