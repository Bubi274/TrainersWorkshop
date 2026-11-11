package io.github.bubi274.trainersworkshop.compat.rct

import com.cobblemon.mod.common.pokemon.Pokemon
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import java.util.UUID

/**
 * Cửa ngõ an toàn tới Radical Cobblemon Trainers (phụ thuộc tuỳ chọn).
 * Lớp này không nhắc tới class nào của RCT, nên được gọi kể cả khi RCT chưa cài;
 * mọi thứ cần RCT nằm trong [RctAccess] và chỉ được nạp khi [loaded] là true.
 */
object RctCompat {
    val loaded: Boolean by lazy { FabricLoader.getInstance().isModLoaded("rctmod") }

    fun init() {
        if (!loaded) return
        RctAccess.checkHooks()
    }

    fun isTrainer(entity: Entity): Boolean = loaded && RctAccess.isTrainer(entity)

    // Các hàm dưới đây chỉ gọi sau khi isTrainer(entity) trả về true.
    fun trainerId(entity: Entity): String = RctAccess.trainerId(entity)
    fun trainerName(entity: Entity): Text = RctAccess.trainerName(entity)
    fun defaultTeam(entity: Entity, server: MinecraftServer): List<Pokemon> = RctAccess.defaultTeam(entity, server)
    fun defaultLootTable(entity: Entity): String = RctAccess.defaultLootTable(entity)
    fun makePersistent(entity: Entity) = RctAccess.makePersistent(entity)

    /**
     * Level cap của người chơi theo RCT, phía server (TrainerManager.getData(player).levelCap).
     * Trả về null nếu không có RCT hoặc RCT báo lỗi; khi đó chỉ dùng level tối đa của Cobblemon.
     */
    fun serverLevelCap(player: PlayerEntity): Int? = if (loaded) RctAccess.serverLevelCap(player) else null

    /** Level cap để hiện trên giao diện, phía client (PlayerState.get(player).levelCap). null nếu không có RCT hoặc lỗi. */
    fun clientLevelCap(player: PlayerEntity): Int? = if (loaded) RctAccess.clientLevelCap(player) else null

    /** Bỏ đội đã dựng sẵn cho trận đấu (sau khi lưu hoặc reset). */
    fun forget(entityUuid: UUID) {
        if (loaded) RctAccess.forget(entityUuid)
    }

    internal fun warn(message: String, error: Throwable? = null) {
        TrainersWorkshop.LOGGER.warn("[RCT compat] $message", error)
    }
}
