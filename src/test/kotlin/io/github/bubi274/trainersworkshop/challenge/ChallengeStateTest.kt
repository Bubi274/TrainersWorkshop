package io.github.bubi274.trainersworkshop.challenge

import com.google.gson.JsonParser
import net.minecraft.nbt.NbtCompound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Challenge state rules and save/load (needs Minecraft's NBT classes, so it runs in the Gradle build). */
class ChallengeStateTest {
    /** Vector 1 (AB12CD34, slot 3 = shiny Flabébé) and vector 2 (EF56GH78, slot 5 rerolled from AB12CD34). */
    private val codes: List<String> by lazy {
        val stream = javaClass.getResourceAsStream("/challenge/challenge-vectors.json") ?: error("vectors missing")
        val vectors = JsonParser.parseString(String(stream.readAllBytes(), StandardCharsets.UTF_8)).asJsonObject
        vectors.getAsJsonArray("challenge").map { it.asJsonObject["code"].asString }
    }

    private val player = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val oldMon = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val newMon = UUID.fromString("00000000-0000-0000-0000-0000000000a2")

    @Test
    fun onlyPokemonFirstSeenAfterTheStartCount() {
        val state = ChallengeState()
        state.recordOwned(player, oldMon)
        state.start(player, codes[0], now = 1000)
        state.recordOwned(player, newMon)
        val data = state.player(player)
        assertFalse(data.obtainedDuringChallenge(oldMon))
        assertTrue(data.obtainedDuringChallenge(newMon))
        // Seeing a Pokémon again (for example after a trade back) doesn't move its first-seen position.
        assertFalse(state.recordOwned(player, oldMon))
        assertFalse(data.obtainedDuringChallenge(oldMon))
    }

    @Test
    fun theCodeIsKeptAsPastedButTrimmed() {
        val state = ChallengeState()
        val active = state.start(player, "  ${codes[0]}\n", now = 0)
        assertEquals(codes[0], active.code)
        assertEquals("AB12CD34", active.challenge.id)
    }

    @Test
    fun onlyOneChallengeAtATime() {
        val state = ChallengeState()
        state.start(player, codes[0], now = 0)
        assertThrows(ChallengeActionException::class.java) { state.start(player, codes[1], now = 0) }
    }

    @Test
    fun aBadCodeIsRefusedWithTheDecoderMessage() {
        val state = ChallengeState()
        val error = assertThrows(ChallengeActionException::class.java) { state.start(player, "TW2.abc.12345678", now = 0) }
        assertTrue(error.message!!.contains("TW1."))
        assertNull(state.player(player).active)
    }

    @Test
    fun rerollKeepsTheOriginalStart() {
        val state = ChallengeState()
        val first = state.start(player, codes[0], now = 0)
        state.recordOwned(player, newMon)
        val rerollCode = state.requestReroll(player, 1)
        assertEquals(codes[0], ChallengeCode.decodeReroll(rerollCode).code)
        val next = state.applyReroll(player, codes[1])
        assertEquals("EF56GH78", next.challenge.id)
        assertEquals(first.startSeq, next.startSeq)
        assertNull(next.rerollAllow)
        assertTrue(state.player(player).obtainedDuringChallenge(newMon))
    }

    @Test
    fun aRerollNeedsRerollOneFirst() {
        val state = ChallengeState()
        state.start(player, codes[0], now = 0)
        assertThrows(ChallengeActionException::class.java) { state.applyReroll(player, codes[1]) }
    }

    @Test
    fun aWrongRerollIsRefusedAndNothingChanges() {
        val state = ChallengeState()
        state.start(player, codes[1], now = 0)
        state.requestReroll(player, 1)
        // codes[0] is the parent of codes[1], not a reroll of it.
        assertThrows(ChallengeActionException::class.java) { state.applyReroll(player, codes[0]) }
        assertEquals("EF56GH78", state.player(player).active!!.challenge.id)
        assertEquals(1, state.player(player).active!!.rerollAllow)
    }

    @Test
    fun shinyFeathersArePaidOncePerSlot() {
        val state = ChallengeState()
        state.start(player, codes[0], now = 0)
        val data = state.player(player)
        assertEquals(listOf(2), data.unpaidShinySlots())
        state.markShinyPaid(player, 2)
        assertEquals(emptyList<Int>(), data.unpaidShinySlots())

        // Abandon and import the same code again: nothing to pay.
        state.abandon(player)
        state.start(player, codes[0], now = 0)
        assertEquals(emptyList<Int>(), data.unpaidShinySlots())

        // The reroll result keeps slot 3 (not rerolled), so it stays paid.
        state.requestReroll(player, 1)
        state.applyReroll(player, codes[1])
        assertEquals(emptyList<Int>(), data.unpaidShinySlots())
    }

    @Test
    fun aRerollResultStartedDirectlyInheritsPaidSlots() {
        val state = ChallengeState()
        state.start(player, codes[0], now = 0)
        state.markShinyPaid(player, 2)
        state.abandon(player)
        state.start(player, codes[1], now = 0)
        assertEquals(emptyList<Int>(), state.player(player).unpaidShinySlots())
    }

    @Test
    fun completeEndsTheChallengeAndCountsIt() {
        val state = ChallengeState()
        state.start(player, codes[0], now = 0)
        state.complete(player)
        val data = state.player(player)
        assertNull(data.active)
        assertEquals(1, data.completed)
        assertThrows(ChallengeActionException::class.java) { state.abandon(player) }
    }

    @Test
    fun everythingSurvivesASaveAndLoad() {
        val state = ChallengeState()
        state.recordOwned(player, oldMon)
        state.start(player, codes[0], now = 1234)
        state.recordOwned(player, newMon)
        state.markShinyPaid(player, 2)
        state.requestReroll(player, 1)
        state.player(player).eliteFourBeaten += "kanto_league_lorelei"
        state.player(player).championsRewarded += "kanto_champion_blue"

        val loaded = ChallengeState.fromNbt(state.writeTo(NbtCompound()))
        val before = state.player(player)
        val after = loaded.player(player)
        assertEquals(before.active, after.active)
        assertEquals(before.ledger, after.ledger)
        assertEquals(before.paidShiny, after.paidShiny)
        assertEquals(before.eliteFourBeaten, after.eliteFourBeaten)
        assertEquals(before.championsRewarded, after.championsRewarded)
        assertTrue(after.obtainedDuringChallenge(newMon))

        // The counter continues after the saved positions.
        val another = UUID.fromString("00000000-0000-0000-0000-0000000000a3")
        loaded.recordOwned(player, another)
        assertTrue(after.ownedSince(another)!! > after.ownedSince(newMon)!!)
    }

    @Test
    fun aSavedChallengeWithABrokenCodeIsDropped() {
        val nbt = NbtCompound().apply {
            put("players", net.minecraft.nbt.NbtList().apply {
                add(NbtCompound().apply {
                    putUuid("player", player)
                    put("active", NbtCompound().apply { putString("code", "TW1.broken.00000000") })
                })
            })
        }
        val loaded = ChallengeState.fromNbt(nbt)
        assertNull(loaded.player(player).active)
        assertNotNull(loaded.player(player))
    }
}
