package io.github.bubi274.trainersworkshop.editor

import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import java.util.UUID

/*
 * Dữ liệu trao đổi giữa server và màn hình chỉnh sửa.
 * Client chỉ hiển thị và gửi "trạng thái mong muốn"; server luôn kiểm tra lại mọi thứ.
 */

enum class EditorMode { CREATIVE, SURVIVAL, TRAINER }

/** Thứ tự 6 chỉ số dùng trong mảng IV/EV. */
val EDITOR_STATS: List<Stat> = listOf(
    Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED,
)

const val MOVE_SLOTS = 4

/** Giá trị trong PokemonEditData.hyperIvs khi chỉ số đó không được "hyper train". */
const val NO_HYPER = -1

/** Các giá trị chỉnh được của một Pokémon. */
data class PokemonEditData(
    val uuid: UUID,
    val species: String,
    val aspects: List<String>,
    val level: Int,
    /** Tổng EXP hiện có (pokemon.experience); dùng để tính kẹo cần cho level mục tiêu. */
    val experience: Int = 0,
    val shiny: Boolean,
    val gender: String,
    /** Nature gốc (identifier). */
    val nature: String,
    /** Nature do mint ("" nếu chưa dùng mint). */
    val mintedNature: String,
    val ability: String,
    /** Ability đang có bị ép ngoài pool (forced = true trong Cobblemon). */
    val abilityForced: Boolean = false,
    /** IV gốc. */
    val ivs: List<Int>,
    /**
     * IV "hyper trained" của Cobblemon (kẹo IV ghi vào đây), -1 nếu không có.
     * IV có hiệu lực trong trận = hyper nếu có, ngược lại là IV gốc.
     */
    val hyperIvs: List<Int> = List(EDITOR_STATS.size) { NO_HYPER },
    val evs: List<Int>,
    /** Luôn có [MOVE_SLOTS] phần tử, "" là ô trống. */
    val moves: List<String>,
    val ball: String,
    /** HP hiện tại và tối đa (để vẽ thanh HP giống PartySlotWidget ở cả 3 chế độ). */
    val currentHealth: Int = 1,
    val maxHealth: Int = 1,
    /** Vật phẩm đang cầm (chỉ hiển thị, không chỉnh sửa). */
    val heldItemId: String = "",
    val heldItemCount: Int = 0,
    /** Survival: dùng Gold Bottle Cap để đưa cả 6 IV gốc lên 31. */
    val useGoldCap: Boolean = false,
    /** Survival: dùng Gold Withered Bottle Cap để đưa cả 6 IV gốc về 0. */
    val useGoldWithered: Boolean = false,
) {
    /** IV có hiệu lực (giống IVs.getEffectiveBattleIV của Cobblemon). */
    fun effectiveIv(index: Int): Int = hyperIvs[index].takeIf { it >= 0 } ?: ivs[index]

    /** Nature đang có hiệu lực (mint nếu có). */
    val effectiveNature: String get() = mintedNature.ifEmpty { nature }

    fun toNbt(): NbtCompound = NbtCompound().apply {
        putUuid("uuid", uuid)
        putString("species", species)
        put("aspects", stringList(aspects))
        putInt("level", level)
        putInt("experience", experience)
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
            experience = nbt.getInt("experience"),
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

/** Những lựa chọn hợp lệ do server tính sẵn cho từng Pokémon. */
data class PokemonEditOptions(
    /** Mọi chiêu loài này học được (kèm các chiêu đang có). */
    val legalMoves: List<String>,
    /** Chiêu đổi miễn phí: đã từng học hoặc học theo level. */
    val freeMoves: List<String>,
    /** Chiêu dạy được bằng TM của TMCraft (học theo level hoặc theo TM). */
    val tmMoves: List<String>,
    val abilities: List<String>,
    /** Ability thường của loài (CommonAbilityType). */
    val commonAbilities: List<String> = emptyList(),
    /** Ability ẩn của loài (HiddenAbilityType). */
    val hiddenAbilities: List<String> = emptyList(),
    val genders: List<String>,
    /**
     * Tổng EXP cần để đạt từng level theo nhóm kinh nghiệm của Pokémon (chỉ số = level, ô 0 không dùng).
     * Server tính bằng ExperienceGroup của Cobblemon; Survival dùng để tính kẹo cần cho level mục tiêu.
     */
    val levelExp: List<Int> = emptyList(),
) {
    fun toNbt(): NbtCompound = NbtCompound().apply {
        put("legalMoves", stringList(legalMoves))
        put("freeMoves", stringList(freeMoves))
        put("tmMoves", stringList(tmMoves))
        put("abilities", stringList(abilities))
        put("commonAbilities", stringList(commonAbilities))
        put("hiddenAbilities", stringList(hiddenAbilities))
        put("genders", stringList(genders))
        putIntArray("levelExp", levelExp.toIntArray())
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
            levelExp = nbt.getIntArray("levelExp").toList(),
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

/** Toàn bộ trạng thái màn hình, server gửi xuống mỗi khi mở hoặc có thay đổi. */
data class EditorState(
    val mode: EditorMode,
    /** Tối đa 6 ô; ô trống không có trong danh sách. */
    val slots: List<EditorSlot>,
    val maxLevel: Int,
    val balls: List<String>,
    /** Chỉ có ở chế độ TRAINER. */
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

/** Các hành động client gửi lên server. */
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
