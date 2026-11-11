package io.github.bubi274.trainersworkshop.compat.rct

import com.cobblemon.mod.common.pokemon.Pokemon
import com.gitlab.srcmc.rctapi.api.trainer.Trainer
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC
import com.gitlab.srcmc.rctapi.api.trainer.TrainerRegistry
import com.gitlab.srcmc.rctmod.ModCommon
import com.gitlab.srcmc.rctmod.api.RCTMod
import com.gitlab.srcmc.rctmod.api.data.sync.PlayerState
import com.gitlab.srcmc.rctmod.world.entities.TrainerMob
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.editor.LootMode
import io.github.bubi274.trainersworkshop.editor.TrainerOverrides
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.server.MinecraftServer
import java.util.UUID

/** Mọi thao tác chạm tới class của RCT. Chỉ được nạp khi RCT đã cài (xem RctCompat). */
object RctAccess {
    private const val ID_PREFIX = "${TrainersWorkshop.MOD_ID}_"

    /** Đội đã dựng cho từng trainer: entity UUID -> (phiên bản chỉnh sửa, TrainerNPC). */
    private val built = HashMap<UUID, Pair<Int, TrainerNPC>>()

    private val registry: TrainerRegistry get() = ModCommon.RCT.trainerRegistry

    private fun registryId(entityUuid: UUID) = ID_PREFIX + entityUuid

    fun isTrainer(entity: Entity): Boolean = entity is TrainerMob

    fun trainerId(entity: Entity): String = (entity as TrainerMob).trainerId ?: ""

    fun trainerName(entity: Entity): Text = (entity as TrainerMob).displayName ?: Text.literal(trainerId(entity))

    fun defaultTeam(entity: Entity, server: MinecraftServer): List<Pokemon> {
        val npc = registry.getById(trainerId(entity), TrainerNPC::class.java) ?: return emptyList()
        return npc.team.map { it.clone(false, server.registryManager) }
    }

    fun defaultLootTable(entity: Entity): String =
        RCTMod.getInstance().trainerManager.getData(entity as TrainerMob)?.lootTableResource?.toString() ?: ""

    fun makePersistent(entity: Entity) = (entity as TrainerMob).setPersistent(true)

    fun forget(entityUuid: UUID) {
        built.remove(entityUuid)
        runCatching { registry.unregisterById(registryId(entityUuid)) }
    }

    private var levelCapWarned = false

    /** Gọi API level cap của RCT; mọi lỗi (đổi phiên bản, thiếu class...) trả về null và chỉ ghi log một lần. */
    private fun levelCapOrNull(read: () -> Int): Int? = try {
        read()
    } catch (e: Throwable) {
        if (!levelCapWarned) {
            levelCapWarned = true
            RctCompat.warn("Could not read the player's level cap; using the Cobblemon max level.", e)
        }
        null
    }

    fun serverLevelCap(player: PlayerEntity): Int? =
        levelCapOrNull { RCTMod.getInstance().trainerManager.getData(player).levelCap }

    fun clientLevelCap(player: PlayerEntity): Int? =
        levelCapOrNull { PlayerState.get(player).levelCap }

    /** Kiểm tra lúc khởi động: các hàm RCT mà mixin bám vào còn tồn tại không. */
    fun checkHooks() {
        val ok = runCatching {
            RCTMod::class.java.getMethod("makeBattle", TrainerMob::class.java, PlayerEntity::class.java)
            TrainerMob::class.java.getDeclaredMethod("dropBattleLoot", Identifier::class.java, PlayerEntity::class.java)
        }.isSuccess
        if (!ok) RctCompat.warn("This RCT version is not supported: Trainer Editor changes will not apply in battles.")
    }

    /**
     * Gọi từ mixin khi RCT bắt đầu trận đấu: trả về TrainerNPC có đội đã chỉnh, hoặc null để dùng đội gốc.
     */
    @JvmStatic
    fun editedTrainer(mob: TrainerMob): Trainer? = try {
        buildTrainer(mob)
    } catch (e: Exception) {
        RctCompat.warn("Could not build the edited team for ${mob.uuid}; using the default team.", e)
        null
    }

    private fun buildTrainer(mob: TrainerMob): Trainer? {
        val server = mob.server ?: return null
        val override = TrainerOverrides.get(server)[mob.uuid] ?: return null
        val team = override.team ?: return null
        if (override.trainerId != mob.trainerId || team.isEmpty()) return null

        val id = registryId(mob.uuid)
        built[mob.uuid]?.let { (version, npc) ->
            if (version == override.version && registry.getById(id) === npc) return npc
        }
        val original = registry.getById(mob.trainerId, TrainerNPC::class.java) ?: return null
        val pokemon = team.map { Pokemon.loadFromNBT(server.registryManager, it) }.toTypedArray()
        // TODO(rct): gimmick (Mega/Tera/Dynamax) gắn theo UUID Pokémon gốc, nên đội đã chỉnh thường mất gimmick.
        val npc = TrainerNPC(
            original.name,
            pokemon,
            TrainerNPC.GimmicksMap(original.gimmicks),
            original.bag,
            original.battleTheme,
            original.battleAI,
            mob,
        )
        runCatching { registry.unregisterById(id) }
        registry.registerNPC(id, npc)
        built[mob.uuid] = Pair(override.version, npc)
        return npc
    }

    /**
     * Gọi từ mixin khi trainer bị đánh bại và RCT sắp thả đồ.
     * Thả đồ tuỳ chỉnh; trả về true nếu cần bỏ bảng loot mặc định của RCT.
     */
    @JvmStatic
    fun dropEditedLoot(mob: TrainerMob, player: PlayerEntity): Boolean = try {
        dropLoot(mob, player)
    } catch (e: Exception) {
        RctCompat.warn("Could not drop custom loot for ${mob.uuid}.", e)
        false
    }

    private fun dropLoot(mob: TrainerMob, player: PlayerEntity): Boolean {
        val server = mob.server ?: return false
        val overrides = TrainerOverrides.get(server)
        val override = overrides[mob.uuid] ?: return false
        if (override.trainerId != mob.trainerId) return false

        if (override.loot.isNotEmpty()) {
            val give = when (override.lootMode) {
                LootMode.EVERY_WIN -> true
                LootMode.FIRST_WIN_PER_PLAYER -> override.claimedBy.add(player.uuid).also { if (it) overrides.markDirty() }
            }
            if (give) {
                override.loot.forEach { entry ->
                    if (mob.random.nextFloat() < entry.chance) mob.dropStack(entry.stack.copy())
                }
            }
        }
        return !override.keepDefaultLoot
    }
}
