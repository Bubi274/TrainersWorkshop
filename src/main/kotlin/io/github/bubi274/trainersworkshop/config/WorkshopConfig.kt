package io.github.bubi274.trainersworkshop.config

import com.google.gson.GsonBuilder
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

/** Các giá trị chỉnh được trong config/trainers_workshop.json. */
data class WorkshopConfigData(
    /** Tỉ lệ rơi Shiny Berry từ raid 7 sao (0.05 = 5%). */
    var raidTierSevenShinyBerryChance: Double = 0.05,
)

object WorkshopConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path = FabricLoader.getInstance().configDir.resolve("${TrainersWorkshop.MOD_ID}.json")

    var data = WorkshopConfigData()
        private set

    /** Đọc file config; nếu chưa có hoặc bị lỗi thì dùng giá trị mặc định và ghi lại file. */
    fun load() {
        data = try {
            if (Files.exists(path)) {
                Files.newBufferedReader(path).use { gson.fromJson(it, WorkshopConfigData::class.java) } ?: WorkshopConfigData()
            } else {
                WorkshopConfigData()
            }
        } catch (e: Exception) {
            TrainersWorkshop.LOGGER.error("Could not read {}, using defaults", path, e)
            WorkshopConfigData()
        }
        data.raidTierSevenShinyBerryChance = data.raidTierSevenShinyBerryChance.coerceIn(0.0, 1.0)
        save()
    }

    private fun save() {
        try {
            Files.createDirectories(path.parent)
            Files.newBufferedWriter(path).use { gson.toJson(data, it) }
        } catch (e: Exception) {
            TrainersWorkshop.LOGGER.error("Could not write {}", path, e)
        }
    }
}
