package io.github.bubi274.trainersworkshop.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShinyRewardTest {
    @Test
    fun eachStatHasItsFeather() {
        assertEquals("cobblemon:health_feather", ShinyReward.feather("hp"))
        assertEquals("cobblemon:muscle_feather", ShinyReward.feather("attack"))
        assertEquals("cobblemon:resist_feather", ShinyReward.feather("defence"))
        assertEquals("cobblemon:genius_feather", ShinyReward.feather("special_attack"))
        assertEquals("cobblemon:clever_feather", ShinyReward.feather("special_defence"))
        assertEquals("cobblemon:swift_feather", ShinyReward.feather("Speed"))
        assertNull(ShinyReward.feather("luck"))
        assertEquals(ShinyReward.FEATHERS.keys, ShinyReward.LABELS.keys)
    }

    @Test
    fun featherCountIsSplitIntoStacks() {
        assertEquals(listOf(64, 64), ShinyReward.stacks(ShinyReward.FEATHERS_PER_SLOT, 64))
        assertEquals(listOf(16, 16, 16, 16, 16, 16, 16, 16), ShinyReward.stacks(128, 16))
        assertEquals(listOf(64, 36), ShinyReward.stacks(100, 64))
        assertEquals(emptyList<Int>(), ShinyReward.stacks(0, 64))
    }
}
