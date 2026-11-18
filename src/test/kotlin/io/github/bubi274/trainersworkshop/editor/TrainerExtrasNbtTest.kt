package io.github.bubi274.trainersworkshop.editor

import net.minecraft.nbt.NbtCompound
import org.junit.Assert.assertEquals
import org.junit.Test

/** Save/load round trip of the per-slot trainer data (needs Minecraft's NBT classes, so it runs in the Gradle build). */
class TrainerExtrasNbtTest {
    @Test
    fun gimmicksSurviveARoundTrip() {
        val extras = TrainerSlotExtras(TrainerGimmicks(tera = "fire", dynamax = true, gmax = true))
        assertEquals(extras, TrainerSlotExtras.fromNbt(extras.toNbt()))
    }

    @Test
    fun teraOffSurvivesARoundTrip() {
        val extras = TrainerSlotExtras(TrainerGimmicks(tera = "", dynamax = false, gmax = true))
        assertEquals(extras, TrainerSlotExtras.fromNbt(extras.toNbt()))
    }

    @Test
    fun alphaBackupSurvivesARoundTrip() {
        val extras = TrainerSlotExtras(
            TrainerGimmicks(tera = "dragon"),
            AlphaBackup(ivs = listOf(1, 2, 3, 4, 5, 6), level = 42, moves = listOf("tackle", "ember", "", "")),
        )
        assertEquals(extras, TrainerSlotExtras.fromNbt(extras.toNbt()))
    }

    @Test
    fun missingDataReadsAsNoGimmicks() {
        assertEquals(TrainerSlotExtras(), TrainerSlotExtras.fromNbt(NbtCompound()))
    }

    @Test
    fun overrideKeepsSlotsInOrder() {
        val slots = listOf(
            TrainerSlotExtras(TrainerGimmicks(tera = "water")),
            TrainerSlotExtras(TrainerGimmicks(dynamax = true)),
        )
        val nbt = TrainerSlotListCodec.write(slots)
        assertEquals(slots, TrainerSlotListCodec.read(nbt))
    }
}
