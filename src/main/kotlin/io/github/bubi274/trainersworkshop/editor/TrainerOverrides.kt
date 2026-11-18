package io.github.bubi274.trainersworkshop.editor

import io.github.bubi274.trainersworkshop.TrainersWorkshop
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.MinecraftServer
import net.minecraft.world.PersistentState
import java.util.UUID

enum class LootMode { EVERY_WIN, FIRST_WIN_PER_PLAYER }

/** One loot entry: an item (with its count) and a chance from 0 to 1. */
data class LootEntry(val stack: ItemStack, val chance: Float) {
    fun toNbt(registries: RegistryWrapper.WrapperLookup): NbtCompound = NbtCompound().apply {
        put("item", stack.encode(registries))
        putFloat("chance", chance)
    }

    companion object {
        const val MAX_ENTRIES = 27

        fun fromNbt(registries: RegistryWrapper.WrapperLookup, nbt: NbtCompound): LootEntry? {
            val stack = ItemStack.fromNbt(registries, nbt.get("item") ?: return null).orElse(null) ?: return null
            return LootEntry(stack, nbt.getFloat("chance").coerceIn(0f, 1f))
        }
    }
}

/**
 * Edits for one specific RCT trainer (by entity UUID).
 * A null [team] means the original RCT team is used; Pokémon are stored as Cobblemon NBT.
 */
class TrainerOverride(
    val trainerId: String,
    val team: List<NbtCompound>?,
    val loot: List<LootEntry>,
    val lootMode: LootMode,
    val keepDefaultLoot: Boolean,
    val claimedBy: MutableSet<UUID> = mutableSetOf(),
) {
    /** Goes up on every save, so we know when the battle team must be rebuilt. */
    var version: Int = 0

    fun toNbt(registries: RegistryWrapper.WrapperLookup): NbtCompound = NbtCompound().apply {
        putString("trainerId", trainerId)
        team?.let { list -> put("team", NbtList().apply { list.forEach { add(it.copy()) } }) }
        put("loot", NbtList().apply { loot.forEach { add(it.toNbt(registries)) } })
        putString("lootMode", lootMode.name)
        putBoolean("keepDefaultLoot", keepDefaultLoot)
        put("claimedBy", stringList(claimedBy.map { it.toString() }))
    }

    companion object {
        fun fromNbt(registries: RegistryWrapper.WrapperLookup, nbt: NbtCompound) = TrainerOverride(
            trainerId = nbt.getString("trainerId"),
            team = if (nbt.contains("team")) readCompoundList(nbt, "team") else null,
            loot = readCompoundList(nbt, "loot").mapNotNull { LootEntry.fromNbt(registries, it) },
            lootMode = enumValueOrNull<LootMode>(nbt.getString("lootMode")) ?: LootMode.EVERY_WIN,
            keepDefaultLoot = nbt.getBoolean("keepDefaultLoot"),
            claimedBy = readStringList(nbt, "claimedBy").mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.toMutableSet(),
        )
    }
}

/** Saves trainer edits in the world data (world/data/trainers_workshop_trainers.dat). */
class TrainerOverrides : PersistentState() {
    private val overrides = HashMap<UUID, TrainerOverride>()

    operator fun get(entity: UUID): TrainerOverride? = overrides[entity]

    fun put(entity: UUID, override: TrainerOverride) {
        override.version = (overrides[entity]?.version ?: 0) + 1
        overrides[entity] = override
        markDirty()
    }

    fun remove(entity: UUID): TrainerOverride? = overrides.remove(entity)?.also { markDirty() }

    override fun writeNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup): NbtCompound {
        val list = NbtList()
        overrides.forEach { (uuid, override) ->
            list.add(override.toNbt(registries).apply { putUuid("entity", uuid) })
        }
        nbt.put("trainers", list)
        return nbt
    }

    companion object {
        private const val NAME = "${TrainersWorkshop.MOD_ID}_trainers"

        private val TYPE = PersistentState.Type<TrainerOverrides>({ TrainerOverrides() }, { nbt, registries -> fromNbt(nbt, registries) }, null)

        private fun fromNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup): TrainerOverrides {
            val state = TrainerOverrides()
            readCompoundList(nbt, "trainers").forEach {
                if (it.containsUuid("entity")) state.overrides[it.getUuid("entity")] = TrainerOverride.fromNbt(registries, it)
            }
            return state
        }

        fun get(server: MinecraftServer): TrainerOverrides =
            server.overworld.persistentStateManager.getOrCreate(TYPE, NAME)
    }
}
