package io.github.bubi274.trainersworkshop.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChallengePasteTest {
    private val code = "TW1.eyJ2IjoxfQ.12345678"

    @Test
    fun classifiesTheClipboard() {
        assertEquals(ChallengePaste.Kind.EMPTY, ChallengePaste.classify(null))
        assertEquals(ChallengePaste.Kind.EMPTY, ChallengePaste.classify(" \n\t "))
        assertEquals(ChallengePaste.Kind.CHALLENGE_CODE, ChallengePaste.classify("  $code\n"))
        assertEquals(ChallengePaste.Kind.REROLL_CODE, ChallengePaste.classify("TWR1.abc.12345678"))
        assertEquals(ChallengePaste.Kind.NOT_A_CODE, ChallengePaste.classify("hello world"))
        // Lowercase or a missing dot is not a code.
        assertEquals(ChallengePaste.Kind.NOT_A_CODE, ChallengePaste.classify("tw1.abc.12345678"))
        assertEquals(ChallengePaste.Kind.NOT_A_CODE, ChallengePaste.classify("TW1"))
    }

    @Test
    fun theLengthLimitCountsTheTrimmedText() {
        val atLimit = "TW1." + "A".repeat(ChallengePaste.MAX_LENGTH - 4)
        assertEquals(ChallengePaste.Kind.CHALLENGE_CODE, ChallengePaste.classify("   $atLimit   "))
        assertEquals(ChallengePaste.Kind.TOO_LONG, ChallengePaste.classify(atLimit + "A"))
    }

    @Test
    fun everyProblemHasAMessageWithoutThePastedText() {
        val secret = "my secret clipboard text"
        for (kind in ChallengePaste.Kind.entries) {
            val message = ChallengePaste.problem(kind)
            if (kind == ChallengePaste.Kind.CHALLENGE_CODE) {
                assertNull(message)
            } else {
                assertNotNull(kind.name, message)
                assertFalse(kind.name, message!!.contains(secret))
            }
        }
        assertEquals(ChallengePaste.Kind.NOT_A_CODE, ChallengePaste.classify(secret))
    }

    @Test
    fun decoderErrorsDoNotRepeatTheCode() {
        val broken = "TW1.eyJ2IjoxLCJpZCI6IkFCMTJDRDM0In0.00000000"
        val error = try {
            ChallengeCode.decodeChallenge(broken)
            null
        } catch (e: ChallengeCodeException) {
            e.message
        }
        assertNotNull(error)
        assertFalse(error!!.contains("eyJ2"))
    }

    @Test
    fun aPasteStartsOrContinuesAfterRerollOne() {
        assertEquals(ChallengePaste.Action.START, ChallengePaste.action(null))
        val challenge = Challenge("AB12CD34", 0, "pokeroll 1.4.0", emptyList(), null, emptyList())
        val active = ActiveChallenge("TW1.x.y", challenge, startSeq = 1, startedAt = 0)
        assertEquals(ChallengePaste.Action.START, ChallengePaste.action(active))
        assertEquals(ChallengePaste.Action.REROLL, ChallengePaste.action(active.copy(rerollAllow = 1)))
    }
}
