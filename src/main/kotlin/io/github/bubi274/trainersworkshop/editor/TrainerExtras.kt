package io.github.bubi274.trainersworkshop.editor

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList

/*
 * Per-Pokémon trainer data that is not part of the Pokémon itself.
 * RCT keeps battle gimmicks in TrainerNPC.GimmicksMap, keyed by the Pokémon's UUID
 * (rctapi 0.16.2: Gimmicks(tera, dynamax, gmax), GimmicksMap.to/of use Pokemon.getUuid).
 * After a restart RCT rebuilds its teams with new UUIDs, so the editor stores the gimmicks per team slot instead.
 */

/**
 * Battle gimmicks of one trainer Pokémon, same fields as RCT's Gimmicks record.
 * @property tera tera type id path (for example "fire"); "" = this Pokémon never Terastallizes.
 * @property dynamax whether the trainer may Dynamax this Pokémon.
 * @property gmax Gigantamax factor (RCT sets Pokemon.gmaxFactor from it).
 */
data class TrainerGimmicks(
    val tera: String = "",
    val dynamax: Boolean = false,
    val gmax: Boolean = false,
) {
    fun toNbt(): NbtCompound = NbtCompound().apply {
        putString("tera", tera)
        putBoolean("dynamax", dynamax)
        putBoolean("gmax", gmax)
    }

    companion object {
        fun fromNbt(nbt: NbtCompound) = TrainerGimmicks(
            tera = nbt.getString("tera"),
            dynamax = nbt.getBoolean("dynamax"),
            gmax = nbt.getBoolean("gmax"),
        )
    }
}

/** Everything the Trainer Editor stores for one team slot besides the Pokémon NBT. */
data class TrainerSlotExtras(
    val gimmicks: TrainerGimmicks = TrainerGimmicks(),
) {
    fun toNbt(): NbtCompound = NbtCompound().apply {
        put("gimmicks", gimmicks.toNbt())
    }

    companion object {
        fun fromNbt(nbt: NbtCompound) = TrainerSlotExtras(
            gimmicks = TrainerGimmicks.fromNbt(nbt.getCompound("gimmicks")),
        )
    }
}

/** NBT list of [TrainerSlotExtras], in team order. */
object TrainerSlotListCodec {
    fun write(slots: List<TrainerSlotExtras>): NbtList = NbtList().apply { slots.forEach { add(it.toNbt()) } }

    fun read(list: NbtList): List<TrainerSlotExtras> = (0 until list.size).map { TrainerSlotExtras.fromNbt(list.getCompound(it)) }

    fun read(nbt: NbtCompound, key: String): List<TrainerSlotExtras> = read(nbt.getList(key, NbtElement.COMPOUND_TYPE.toInt()))
}
