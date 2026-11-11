package io.github.bubi274.trainersworkshop.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelRulesTest {
    @Test
    fun threeRareCandiesRaiseThreeLevels() {
        val max = LevelRules.maxLevel(from = 20, cap = 30, rareCandies = 3)
        assertEquals(23, max)
        assertEquals(3, LevelRules.cost(20, max))
        assertNull(LevelRules.error(20, max, 30))
    }

    @Test
    fun manyRareCandiesStopAtCap() {
        val max = LevelRules.maxLevel(from = 20, cap = 30, rareCandies = 50)
        assertEquals(30, max)
        assertEquals(10, LevelRules.cost(20, max))
        assertNull(LevelRules.error(20, max, 30))
    }

    @Test
    fun atCapCannotRaise() {
        assertEquals(30, LevelRules.maxLevel(from = 30, cap = 30, rareCandies = 50))
        assertEquals("level_over_cap", LevelRules.error(30, 31, 30))
        // Đang trên cap (cap bị hạ): không tăng được, nhưng giữ nguyên level thì hợp lệ.
        assertEquals(35, LevelRules.maxLevel(from = 35, cap = 30, rareCandies = 50))
        assertNull(LevelRules.error(35, 35, 30))
    }

    @Test
    fun noRareCandyCannotRaise() {
        assertEquals(20, LevelRules.maxLevel(from = 20, cap = 30, rareCandies = 0))
        assertEquals(0, LevelRules.cost(20, 20))
    }

    @Test
    fun lowerLevelIsAnError() {
        assertEquals("level_decrease", LevelRules.error(20, 19, 30))
        assertEquals(0, LevelRules.cost(20, 19))
    }

    @Test
    fun overCapIsAnError() {
        assertEquals("level_over_cap", LevelRules.error(20, 31, 30))
    }
}
