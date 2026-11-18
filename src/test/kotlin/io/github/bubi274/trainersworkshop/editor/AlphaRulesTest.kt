package io.github.bubi274.trainersworkshop.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaRulesTest {
    @Test
    fun levelGoesUpByTheBonus() {
        assertEquals(55, AlphaRules.boostedLevel(level = 50, bonus = 5, cap = 100))
    }

    @Test
    fun levelNeverGoesAboveTheCap() {
        assertEquals(100, AlphaRules.boostedLevel(level = 98, bonus = 5, cap = 100))
        assertEquals(100, AlphaRules.boostedLevel(level = 100, bonus = 5, cap = 100))
    }

    @Test
    fun levelNeverGoesDown() {
        assertEquals(30, AlphaRules.boostedLevel(level = 30, bonus = -5, cap = 100))
        assertEquals(120, AlphaRules.boostedLevel(level = 120, bonus = 5, cap = 100))
    }

    @Test
    fun tmMoveIsAddedOnlyWhenNoneIsKnown() {
        val tmMoves = setOf("thunderbolt", "flamethrower")
        assertTrue(AlphaRules.needsTmMove(listOf("tackle", "growl", "", ""), tmMoves))
        assertFalse(AlphaRules.needsTmMove(listOf("tackle", "thunderbolt", "", ""), tmMoves))
        assertTrue(AlphaRules.needsTmMove(listOf("", "", "", ""), emptySet()))
    }

    @Test
    fun tmMoveGoesIntoTheFirstEmptySlotOrTheLastOne() {
        assertEquals(2, AlphaRules.tmMoveSlot(listOf("tackle", "growl", "", "")))
        assertEquals(3, AlphaRules.tmMoveSlot(listOf("tackle", "growl", "ember", "scratch")))
    }

    @Test
    fun levelBonusIsClamped() {
        assertEquals(5, AlphaRules.levelBonus(5))
        assertEquals(0, AlphaRules.levelBonus(-3))
        assertEquals(100, AlphaRules.levelBonus(1000))
    }
}
