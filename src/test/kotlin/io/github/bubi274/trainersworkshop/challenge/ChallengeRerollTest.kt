package io.github.bubi274.trainersworkshop.challenge

import com.google.gson.JsonParser
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets

class ChallengeRerollTest {
    private val teams: List<Challenge> by lazy {
        val stream = javaClass.getResourceAsStream("/challenge/challenge-vectors.json") ?: error("vectors missing")
        val vectors = JsonParser.parseString(String(stream.readAllBytes(), StandardCharsets.UTF_8)).asJsonObject
        vectors.getAsJsonArray("challenge").map { ChallengeCode.decodeChallenge(it.asJsonObject["code"].asString) }
    }

    /** The first vector (AB12CD34) and the second (EF56GH78: slot 5 rerolled from AB12CD34). */
    private val original get() = teams[0]
    private val rerolled get() = teams[1]

    private fun slot(dex: Int, mode: String, rule: SlotRule = SlotRule.EVO, tier: String = "blue") =
        ChallengeSlot(dex, "Test", null, rule, false, tier, mode)

    @Test
    fun theVectorRerollIsValid() {
        assertNull(ChallengeReroll.problem(original, 1, rerolled))
    }

    @Test
    fun aDifferentParentIsRejected() {
        assertNotNull(ChallengeReroll.problem(rerolled, 1, rerolled.copy(id = "ZZZZ0000", parent = "QQQQ1111")))
        assertNotNull(ChallengeReroll.problem(original, 1, rerolled.copy(parent = null)))
    }

    @Test
    fun tooManyRerolledSlotsAreRejected() {
        val team = rerolled.team.toMutableList().apply { this[0] = slot(1, "case") }
        assertNotNull(ChallengeReroll.problem(original, 1, rerolled.copy(team = team, rerolled = listOf(0, 4))))
    }

    @Test
    fun aChangedSlotThatIsNotListedIsRejected() {
        val team = rerolled.team.toMutableList().apply { this[0] = slot(1, "case") }
        assertNotNull(ChallengeReroll.problem(original, 6, rerolled.copy(team = team)))
    }

    @Test
    fun aRerolledSlotKeepsItsMode() {
        // Slot 2 (index 1) came from Type Roulette.
        val team = original.team.toMutableList().apply { this[1] = slot(7, "case") }
        assertNotNull(ChallengeReroll.problem(original, 1, rerolled.copy(team = team, rerolled = listOf(1))))
        val sameMode = original.team.toMutableList().apply { this[1] = slot(7, "type") }
        assertNull(ChallengeReroll.problem(original, 1, rerolled.copy(team = sameMode, rerolled = listOf(1))))
    }

    @Test
    fun aMinigameSlotIsRerolledFromCase() {
        // Slot 6 (index 5) is an older Minigame slot (exact / minigame).
        val fromCase = original.team.toMutableList().apply { this[5] = slot(25, "case") }
        assertNull(ChallengeReroll.problem(original, 1, rerolled.copy(team = fromCase, rerolled = listOf(5))))
        val stillMinigame = original.team.toMutableList().apply { this[5] = slot(25, "minigame", SlotRule.EXACT, "minigame") }
        assertNotNull(ChallengeReroll.problem(original, 1, rerolled.copy(team = stillMinigame, rerolled = listOf(5))))
        val exactCase = original.team.toMutableList().apply { this[5] = slot(25, "case", SlotRule.EXACT) }
        assertNotNull(ChallengeReroll.problem(original, 1, rerolled.copy(team = exactCase, rerolled = listOf(5))))
    }

    @Test
    fun aListedSlotMayRollTheSamePokemonAgain() {
        assertNull(ChallengeReroll.problem(original, 1, rerolled.copy(team = original.team, rerolled = listOf(2))))
    }
}
