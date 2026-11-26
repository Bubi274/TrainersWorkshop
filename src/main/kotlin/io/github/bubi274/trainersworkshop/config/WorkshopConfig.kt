package io.github.bubi274.trainersworkshop.config

import com.google.gson.GsonBuilder
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.challenge.ChallengeRegion
import io.github.bubi274.trainersworkshop.challenge.ChallengeTrainers
import io.github.bubi274.trainersworkshop.editor.AlphaRules
import io.github.bubi274.trainersworkshop.editor.MoveSourceRules
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

/** Values that can be changed in config/trainers_workshop.json. */
data class WorkshopConfigData(
    /** Shiny Berry drop chance from 7-star raids (0.05 = 5%). */
    var raidTierSevenShinyBerryChance: Double = 0.05,
    /** Type Gems needed to teach one egg move in the Survival PokeEditor (1..64). */
    var eggMoveGemCost: Int = 3,
    /** Levels a trainer Pokémon gains when the Trainer Editor makes it an Alpha (0..100, never above level 100). */
    var alphaTrainerLevelBonus: Int = 5,
    /** false: a rematch win against an RCT trainer drops nothing from RCT's loot table (badges only drop on the first win anyway). */
    var rctLootOnRematch: Boolean = true,
    /** true: every RCT trainer can be battled again any number of times, even after the player won. */
    var unlimitedRematches: Boolean = true,
    /** Gym Leaders, Elite Four and Champions that are challenge battles, by region (see ChallengeTrainers.DEFAULTS). */
    var challengeRegions: List<ChallengeRegion> = ChallengeTrainers.DEFAULTS,
)

object WorkshopConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path = FabricLoader.getInstance().configDir.resolve("${TrainersWorkshop.MOD_ID}.json")

    var data = WorkshopConfigData()
        private set

    /** Reads the config file; if it is missing or broken, uses the defaults and writes the file again. */
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
        data.eggMoveGemCost = MoveSourceRules.gemCost(data.eggMoveGemCost)
        data.alphaTrainerLevelBonus = AlphaRules.levelBonus(data.alphaTrainerLevelBonus)
        data.challengeRegions = ChallengeTrainers.sanitize(data.challengeRegions)
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
