package io.github.bubi274.trainersworkshop.rematch

/**
 * Rematch rules for RCT trainers. Plain values only, so plain JUnit can test it.
 *
 * RCT counts each win in TrainerBattleMemory before the loot drops (TrainerBattle.distributeRewards calls
 * addDefeatedBy, then TrainerMob.finishBattle calls dropBattleLoot), so at drop time the first win has count 1.
 */
object RematchRules {
    /**
     * True when RCT's loot table should not drop: [rctLootOnRematch] is off and this player has beaten this trainer
     * before ([defeatCount] includes the battle just won). Badges in COBBLEVERSE need defeat_count == 1, so they
     * already only drop on the first win either way.
     */
    fun skipRctLoot(rctLootOnRematch: Boolean, defeatCount: Int): Boolean = !rctLootOnRematch && defeatCount > 1
}
