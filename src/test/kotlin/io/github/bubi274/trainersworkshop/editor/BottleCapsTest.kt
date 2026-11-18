package io.github.bubi274.trainersworkshop.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected results come from the OBC 1.3.0 / 1.5.0 BottleCap bytecode and Cobblemon's IVs.setHyperTrainedIV. */
class BottleCapsTest {
    @Test
    fun hyperTrainingCapKeepsTheBaseIv() {
        val after = BottleCaps.apply(IvState(base = 12, hyper = NO_HYPER), 31, BottleCapMode.HYPER_TRAIN)
        assertEquals(IvState(base = 12, hyper = 31), after)
        assertEquals(31, after.effective)
    }

    @Test
    fun hyperTrainingCapEqualToBaseClearsHyperTraining() {
        val after = BottleCaps.apply(IvState(base = 31, hyper = 20), 31, BottleCapMode.HYPER_TRAIN)
        assertEquals(IvState(base = 31, hyper = NO_HYPER), after)
    }

    @Test
    fun hyperTrainingCapIsRefusedWhenTheEffectiveIvIsAlreadySet() {
        assertFalse(BottleCaps.canUse(IvState(base = 5, hyper = 31), 31, BottleCapMode.HYPER_TRAIN))
        assertTrue(BottleCaps.canUse(IvState(base = 31, hyper = 20), 31, BottleCapMode.HYPER_TRAIN))
    }

    @Test
    fun baseIvCapLeavesHyperTrainingAlone() {
        val after = BottleCaps.apply(IvState(base = 10, hyper = 20), 31, BottleCapMode.BASE_IV)
        assertEquals(IvState(base = 31, hyper = 20), after)
        assertEquals(20, after.effective)
        assertFalse(BottleCaps.canUse(IvState(base = 31, hyper = 20), 31, BottleCapMode.BASE_IV))
    }

    @Test
    fun breedingBuffCapSetsBothAndClearsHyperTraining() {
        val after = BottleCaps.apply(IvState(base = 10, hyper = 20), 0, BottleCapMode.BOTH)
        assertEquals(IvState(base = 0, hyper = NO_HYPER), after)
        // Effective IV already 0 but the base IV isn't: OBC still accepts the cap.
        assertTrue(BottleCaps.canUse(IvState(base = 10, hyper = 0), 0, BottleCapMode.BOTH))
        assertFalse(BottleCaps.canUse(IvState(base = 0, hyper = NO_HYPER), 0, BottleCapMode.BOTH))
    }

    @Test
    fun candiesStartFromTheStateAfterTheCap() {
        val afterCap = BottleCaps.apply(IvState(base = 12, hyper = NO_HYPER), 31, BottleCapMode.HYPER_TRAIN)
        assertEquals(IvState(base = 12, hyper = 29), BottleCaps.withCandies(afterCap, 29))
        // Candies back down to the base IV clear the hyper training, like Cobblemon.
        assertEquals(IvState(base = 12, hyper = NO_HYPER), BottleCaps.withCandies(afterCap, 12))
        assertEquals(afterCap, BottleCaps.withCandies(afterCap, 31))
    }

    @Test
    fun unknownModeNameFallsBackToHyperTraining() {
        assertEquals(BottleCapMode.HYPER_TRAIN, BottleCapMode.fromName(""))
        assertEquals(BottleCapMode.BOTH, BottleCapMode.fromName("BOTH"))
    }
}
