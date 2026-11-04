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

/** Các giá trị chỉnh được của một Pokémon. */
data class PokemonEditData(
    val uuid: UUID,
    val species: String,
    val aspects: List<String>,
    val level: Int,
    val shiny: Boolean,
    val gender: String,
    /** Nature gốc (identifier). */
    val nature: String,
    /** Nature do mint ("" nếu chưa dùng mint). */
    val mintedNature: String,
    val ability: String,
    val ivs: List<Int>,
    val evs: List<Int>,
    /** Luôn có [MOVE_SLOTS] phần tử, "" là ô trống. */
    val moves: List<String>,
    val ball: String,
    /** Survival: dùng Gold Bottle Cap để đưa cả 6 IV lên 31. */
    val useGoldCap: Boolean = false,
) {
    /** Nature đang có hiệu lực (mint nếu có). */
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
        putIntArray("ivs", ivs.toIntArray())
        putIntArray("evs", evs.toIntArray())
        put("moves", stringList(moves))
        putString("ball", ball)
        putBoolean("useGoldCap", useGoldCap)
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
            ivs = fixedSize(nbt.getIntArray("ivs").toList(), EDITOR_STATS.size, 0),
            evs = fixedSize(nbt.getIntArray("evs").toList(), EDITOR_STATS.size, 0),
            moves = fixedSize(readStringList(nbt, "moves"), MOVE_SLOTS, ""),
            ball = nbt.getString("ball"),
            useGoldCap = nbt.getBoolean("useGoldCap"),
        )
    }
}

/** Những lựa chọn hợp lệ do server tính sẵn cho từng Pokémon. */
data class PokemonEditOptions(
    /** Mọi chiêu loài này học được (kèm các chiêu đang có). */
    val legalMoves: List<String>,
    /** Chiêu đổi miễn phí: đã từng học hoặc học theo level. */
    val freeMoves: List<String>,
    val abilities: List<String>,
    val genders: List<String>,
) {
    fun toNbt(): NbtCompound = NbtCompound().apply {
        put("legalMoves", stringList(legalMoves))
        put("freeMoves", stringList(freeMoves))
        put("abilities", stringList(abilities))
        put("genders", stringList(genders))
    }

    companion object {
        fun fromNbt(nbt: NbtCompound) = PokemonEditOptions(
            legalMoves = readStringList(nbt, "legalMoves"),
            freeMoves = readStringList(nbt, "freeMoves"),
            abilities = readStringList(nbt, "abilities"),
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
