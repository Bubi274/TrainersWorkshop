package io.github.bubi274.trainersworkshop.rematch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RematchRulesTest {
    @Test
    fun theFirstWinAlwaysDropsLoot() {
        assertFalse(RematchRules.skipRctLoot(rctLootOnRematch = false, defeatCount = 1))
        assertFalse(RematchRules.skipRctLoot(rctLootOnRematch = true, defeatCount = 1))
    }

    @Test
    fun rematchLootFollowsTheConfig() {
        assertTrue(RematchRules.skipRctLoot(rctLootOnRematch = false, defeatCount = 2))
        assertTrue(RematchRules.skipRctLoot(rctLootOnRematch = false, defeatCount = 10))
        assertFalse(RematchRules.skipRctLoot(rctLootOnRematch = true, defeatCount = 2))
    }

    @Test
    fun anUnknownCountNeverSkips() {
        assertFalse(RematchRules.skipRctLoot(rctLootOnRematch = false, defeatCount = 0))
    }
}
