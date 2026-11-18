package io.github.bubi274.trainersworkshop.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveSourceRulesTest {
    @Test
    fun knownMovesStayFree() {
        assertEquals(MoveSource.FREE, MoveSourceRules.source(free = true, tmCanTeach = true, isEggMove = true, gemExists = true))
    }

    @Test
    fun tmMovesKeepUsingTms() {
        assertEquals(MoveSource.TM, MoveSourceRules.source(free = false, tmCanTeach = true, isEggMove = false, gemExists = true))
    }

    @Test
    fun eggMoveThatATmCanTeachUsesTheTm() {
        assertEquals(MoveSource.TM, MoveSourceRules.source(free = false, tmCanTeach = true, isEggMove = true, gemExists = true))
    }

    @Test
    fun eggMoveWithoutTmUsesGems() {
        assertEquals(MoveSource.GEM, MoveSourceRules.source(free = false, tmCanTeach = false, isEggMove = true, gemExists = true))
    }

    @Test
    fun eggMoveWithoutGemItemCantBeLearned() {
        assertEquals(MoveSource.NONE, MoveSourceRules.source(free = false, tmCanTeach = false, isEggMove = true, gemExists = false))
    }

    @Test
    fun otherMovesCantBeLearned() {
        assertEquals(MoveSource.NONE, MoveSourceRules.source(free = false, tmCanTeach = false, isEggMove = false, gemExists = true))
    }

    @Test
    fun gemCostIsClamped() {
        assertEquals(3, MoveSourceRules.gemCost(3))
        assertEquals(1, MoveSourceRules.gemCost(0))
        assertEquals(1, MoveSourceRules.gemCost(-5))
        assertEquals(64, MoveSourceRules.gemCost(500))
    }
}
