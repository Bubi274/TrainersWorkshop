package io.github.bubi274.trainersworkshop.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmRulesTest {
    @Test
    fun cobblemonTmIsPreferredWhenThePlayerHasBoth() {
        assertEquals(TmSource.COBBLEMON, TmRules.choose(cobblemonCanTeach = true, tmcraftCanTeach = true, haveCobblemon = 1, haveTmcraft = 3))
    }

    @Test
    fun tmcraftTmIsUsedWhenThePlayerHasNoCobblemonTm() {
        assertEquals(TmSource.TMCRAFT, TmRules.choose(cobblemonCanTeach = true, tmcraftCanTeach = true, haveCobblemon = 0, haveTmcraft = 1))
    }

    @Test
    fun withNoTmTheCobblemonTmIsShownAsMissing() {
        assertEquals(TmSource.COBBLEMON, TmRules.choose(cobblemonCanTeach = true, tmcraftCanTeach = true, haveCobblemon = 0, haveTmcraft = 0))
    }

    @Test
    fun worksWithoutTmcraft() {
        assertEquals(TmSource.COBBLEMON, TmRules.choose(cobblemonCanTeach = true, tmcraftCanTeach = false, haveCobblemon = 2, haveTmcraft = 0))
        assertNull(TmRules.choose(cobblemonCanTeach = false, tmcraftCanTeach = false, haveCobblemon = 0, haveTmcraft = 0))
    }

    @Test
    fun onlyTmcraftCanTeachTheMove() {
        // For example a level-up move that TMCraft has a TM for but Cobblemon doesn't.
        assertEquals(TmSource.TMCRAFT, TmRules.choose(cobblemonCanTeach = false, tmcraftCanTeach = true, haveCobblemon = 5, haveTmcraft = 0))
    }

    @Test
    fun cobblemonTmsFollowTheInfiniteTmUsesConfig() {
        assertTrue(TmRules.consumes(TmSource.COBBLEMON, infiniteCobblemonTms = false))
        assertFalse(TmRules.consumes(TmSource.COBBLEMON, infiniteCobblemonTms = true))
    }

    @Test
    fun tmcraftTmsAreAlwaysUsedUp() {
        assertTrue(TmRules.consumes(TmSource.TMCRAFT, infiniteCobblemonTms = false))
        assertTrue(TmRules.consumes(TmSource.TMCRAFT, infiniteCobblemonTms = true))
    }
}
