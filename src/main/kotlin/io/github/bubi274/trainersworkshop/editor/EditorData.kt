package io.github.bubi274.trainersworkshop.editor

import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import java.util.UUID

/*
 * Data exchanged between the server and the editor screen.
 * The client only shows it and sends the "wanted state"; the server always checks everything again.
 */

enum class EditorMode { CREATIVE, SURVIVAL, TRAINER }

/** Order of the 6 stats used in the IV/EV lists. */
val EDITOR_STATS: List<Stat> = listOf(
    Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED,
)

const val MOVE_SLOTS = 4

/** Value in PokemonEditData.hyperIvs when that stat is not hyper trained. */
const val NO_HYPER = -1

/** The editable values of one Pokémon. */
data class PokemonEditData(
    val uuid: UUID,
    val species: String,
    val aspects: List<String>,
    val level: Int,
    val shiny: Boolean,
    val gender: String,
    /** Original nature (identifier). */
    val nature: String,
    /** Nature from a mint ("" if no mint was used). */
    val mintedNature: String,
    val ability: String,
    /** The current ability is forced outside the species pool (forced = true in Cobblemon). */
    val abilityForced: Boolean = false,
    /** Base IVs. */
    val ivs: List<Int>,
    /**
     * Cobblemon's "hyper trained" IVs (IV candies write here), -1 if there is none.
     * The effective IV in battle = the hyper IV if there is one, otherwise the base IV.
     */
    val hyperIvs: List<Int> = List(EDITOR_STATS.size) { NO_HYPER },
    val evs: List<Int>,
    /** Always has [MOVE_SLOTS] entries, "" is an empty slot. */
    val moves: List<String>,
    val ball: String,
    /** Current and max HP (to draw the HP bar like PartySlotWidget in all 3 modes). */
    val currentHealth: Int = 1,
    val maxHealth: Int = 1,
    /** Held item (only shown, not editable). */
    val heldItemId: String = "",
    val heldItemCount: Int = 0,
    /** Survival: use a Gold Bottle Cap to raise all 6 base IVs to 31. */
    val useGoldCap: Boolean = false,
    /** Survival: use a Gold Withered Bottle Cap to lower all 6 base IVs to 0. */
    val useGoldWithered: Boolean = false,
) {
    /** Effective IV (same as Cobblemon's IVs.getEffectiveBattleIV). */
    fun effectiveIv(index: Int): Int = hyperIvs[index].takeIf { it >= 0 } ?: ivs[index]

    /** The nature in effect (the mint if there is one). */
    val effectiveNature: String get() = mintedNature.ifEmpty { nature }

    fun toNbt(): NbtCompound = NbtCompound().apply {
        putUuid("uuid", uuid)
        putString("species", species)
        put("aspects", stringList(aspects))
        putInt("level", level)
        putBoolean("shiny", shiny)
        putString("gender", gender)
        putString("nature", nature)
        putString("mintedNature", mintedNature)
        putString("ability", ability)
        putBoolean("abilityForced", abilityForced)
        putIntArray("ivs", ivs.toIntArray())
        putIntArray("hyperIvs", hyperIvs.toIntArray())
        putIntArray("evs", evs.toIntArray())
        put("moves", stringList(moves))
        putString("ball", ball)
        putInt("currentHealth", currentHealth)
        putInt("maxHealth", maxHealth)
        putString("heldItemId", heldItemId)
        putInt("heldItemCount", heldItemCount)
        putBoolean("useGoldCap", useGoldCap)
        putBoolean("useGoldWithered", useGoldWithered)
    }

    companion object {
        fun fromNbt(nbt: NbtCompound) = PokemonEditData(
            uuid = nbt.getUuid("uuid"),
            species = nbt.getString("species"),
            aspects = readStringList(nbt, "aspects"),
            level = nbt.getInt("level"),
            shiny = nbt.getBoolean("shiny"),
            gender = nbt.getString("gender"),
            nature = nbt.getString("nature"),
            mintedNature = nbt.getString("mintedNature"),
            ability = nbt.getString("ability"),
            abilityForced = nbt.getBoolean("abilityForced"),
            ivs = fixedSize(nbt.getIntArray("ivs").toList(), EDITOR_STATS.size, 0),
            hyperIvs = fixedSize(nbt.getIntArray("hyperIvs").toList(), EDITOR_STATS.size, NO_HYPER),
            evs = fixedSize(nbt.getIntArray("evs").toList(), EDITOR_STATS.size, 0),
            moves = fixedSize(readStringList(nbt, "moves"), MOVE_SLOTS, ""),
            ball = nbt.getString("ball"),
            currentHealth = if (nbt.contains("currentHealth")) nbt.getInt("currentHealth") else 1,
            maxHealth = if (nbt.contains("maxHealth")) nbt.getInt("maxHealth").coerceAtLeast(1) else 1,
            heldItemId = nbt.getString("heldItemId"),
            heldItemCount = nbt.getInt("heldItemCount"),
            useGoldCap = nbt.getBoolean("useGoldCap"),
            useGoldWithered = nbt.getBoolean("useGoldWithered"),
        )
    }
}

/** Valid choices the server works out for each Pokémon. */
data class PokemonEditOptions(
    /** Every move this species can learn (plus the moves it has now). */
    val legalMoves: List<String>,
    /** Moves that are free to swap in: already learned before, or learned by level. */
    val freeMoves: List<String>,
    /** Moves that a TMCraft TM can teach (learned by level or by TM). */
    val tmMoves: List<String>,
    val abilities: List<String>,
    /** The species' common abilities (CommonAbilityType). */
    val commonAbilities: List<String> = emptyList(),
    /** The species' hidden abilities (HiddenAbilityType). */
    val hiddenAbilities: List<String> = emptyList(),
    val genders: List<String>,
) {
    fun toNbt(): NbtCompound = NbtCompound().apply {
        put("legalMoves", stringList(legalMoves))
        put("freeMoves", stringList(freeMoves))
        put("tmMoves", stringList(tmMoves))
        put("abilities", stringList(abilities))
        put("commonAbilities", stringList(commonAbilities))
        put("hiddenAbilities", stringList(hiddenAbilities))
        put("genders", stringList(genders))
    }

    companion object {
        fun fromNbt(nbt: NbtCompound) = PokemonEditOptions(
            legalMoves = readStringList(nbt, "legalMoves"),
            freeMoves = readStringList(nbt, "freeMoves"),
            tmMoves = readStringList(nbt, "tmMoves"),
            abilities = readStringList(nbt, "abilities"),
            commonAbilities = readStringList(nbt, "commonAbilities"),
            hiddenAbilities = readStringList(nbt, "hiddenAbilities"),
            genders = readStringList(nbt, "genders"),
        )
    }
}

data class EditorSlot(val index: Int, val data: PokemonEditData, val options: PokemonEditOptions) {
    fun toNbt(): NbtCompound = NbtCompound().apply {
        putInt("index", index)
        put("data", data.toNbt())
        put("options", options.toNbt())
    }

    companion object {
        fun fromNbt(nbt: NbtCompound) = EditorSlot(
            index = nbt.getInt("index"),
            data = PokemonEditData.fromNbt(nbt.getCompound("data")),
            options = PokemonEditOptions.fromNbt(nbt.getCompound("options")),
        )
    }
}

/** The whole screen state, sent by the server when the screen opens or something changes. */
data class EditorState(
    val mode: EditorMode,
    /** At most 6 slots; empty slots are not in the list. */
    val slots: List<EditorSlot>,
    val maxLevel: Int,
    val balls: List<String>,
    /** Only present in TRAINER mode. */
    val trainer: NbtCompound? = null,
) {
    fun slot(index: Int): EditorSlot? = slots.firstOrNull { it.index == index }

    fun toNbt(): NbtCompound = NbtCompound().apply {
        putString("mode", mode.name)
        put("slots", NbtList().apply { slots.forEach { add(it.toNbt()) } })
        putInt("maxLevel", maxLevel)
        put("balls", stringList(balls))
        trainer?.let { put("trainer", it) }
    }

    companion object {
        fun fromNbt(nbt: NbtCompound) = EditorState(
            mode = enumValueOrNull<EditorMode>(nbt.getString("mode")) ?: EditorMode.CREATIVE,
            slots = readCompoundList(nbt, "slots").map(EditorSlot::fromNbt),
            maxLevel = nbt.getInt("maxLevel"),
            balls = readStringList(nbt, "balls"),
            trainer = if (nbt.contains("trainer")) nbt.getCompound("trainer") else null,
        )
    }
}

/** Actions the client sends to the server. */
object EditorActions {
    const val APPLY = "apply"
    const val CLOSE = "close"
}

fun stringList(values: List<String>): NbtList = NbtList().apply { values.forEach { add(NbtString.of(it)) } }

fun readStringList(nbt: NbtCompound, key: String): List<String> {
    val list = nbt.getList(key, NbtElement.STRING_TYPE.toInt())
    return (0 until list.size).map { list.getString(it) }
}

fun readCompoundList(nbt: NbtCompound, key: String): List<NbtCompound> {
    val list = nbt.getList(key, NbtElement.COMPOUND_TYPE.toInt())
    return (0 until list.size).map { list.getCompound(it) }
}

private fun <T> fixedSize(values: List<T>, size: Int, fill: T): List<T> =
    List(size) { values.getOrElse(it) { fill } }

inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
