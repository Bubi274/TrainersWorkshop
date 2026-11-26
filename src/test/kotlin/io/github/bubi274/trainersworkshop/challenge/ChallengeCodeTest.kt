package io.github.bubi274.trainersworkshop.challenge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.charset.StandardCharsets

/** Tests against the fixed vectors copied from PokéRoll (src/test/resources/challenge/challenge-vectors.json). */
class ChallengeCodeTest {
    private val vectors: JsonObject by lazy {
        val stream = javaClass.getResourceAsStream("/challenge/challenge-vectors.json") ?: error("vectors missing")
        JsonParser.parseString(String(stream.readAllBytes(), StandardCharsets.UTF_8)).asJsonObject
    }

    private fun list(name: String): List<JsonObject> = vectors.getAsJsonArray(name).map { it.asJsonObject }

    /** The expected Challenge, read field by field from a vector's "json". */
    private fun expected(json: JsonObject) = Challenge(
        id = json["id"].asString,
        ts = json["ts"].asLong,
        app = json["app"].asString,
        team = json.getAsJsonArray("team").map { e ->
            val s = e.asJsonObject
            ChallengeSlot(
                dex = s["dex"].asInt,
                name = s["name"].asString,
                special = s["special"].let { if (it.isJsonNull) null else it.asString },
                rule = SlotRule.of(s["rule"].asString)!!,
                shiny = s["shiny"].asBoolean,
                tier = s["tier"].asString,
                mode = s["mode"].asString,
            )
        },
        parent = json["parent"].let { if (it.isJsonNull) null else it.asString },
        rerolled = (json["rerolled"] as JsonArray).map { it.asInt },
    )

    @Test
    fun challengeVectorsDecode() {
        val all = list("challenge")
        assertTrue(all.isNotEmpty())
        all.forEach { v ->
            assertEquals(v["name"].asString, expected(v.getAsJsonObject("json")), ChallengeCode.decodeChallenge(v["code"].asString))
        }
    }

    @Test
    fun challengeVectorsEncodeToTheSameCode() {
        list("challenge").forEach { v ->
            val code = v["code"].asString
            assertEquals(v["name"].asString, code, ChallengeCode.encodeChallenge(ChallengeCode.decodeChallenge(code)))
        }
    }

    @Test
    fun rerollVectorsEncodeToTheSameCode() {
        val all = list("reroll")
        assertTrue(all.isNotEmpty())
        all.forEach { v ->
            val json = v.getAsJsonObject("json")
            val made = ChallengeCode.encodeReroll(json["code"].asString, json["allow"].asInt)
            assertEquals(v["name"].asString, v["code"].asString, made)
        }
    }

    @Test
    fun rerollVectorsDecode() {
        list("reroll").forEach { v ->
            val json = v.getAsJsonObject("json")
            val reroll = ChallengeCode.decodeReroll(v["code"].asString)
            assertEquals(json["code"].asString, reroll.code)
            assertEquals(json["allow"].asInt, reroll.allow)
            assertEquals(ChallengeCode.decodeChallenge(json["code"].asString), reroll.challenge)
        }
    }

    @Test
    fun acceptedVectorsDecodeLikeTheOriginal() {
        val all = list("accepted")
        assertTrue(all.isNotEmpty())
        all.forEach { v ->
            assertEquals(
                v["name"].asString,
                ChallengeCode.decodeChallenge(v["same_as"].asString),
                ChallengeCode.decodeChallenge(v["code"].asString),
            )
        }
    }

    @Test
    fun rejectedVectorsFailWithTheRightError() {
        val all = list("rejected")
        assertTrue(all.isNotEmpty())
        all.forEach { v ->
            val name = v["name"].asString
            val expects = v["expects"]?.asString ?: "challenge"
            assertEquals(name, "challenge", expects)
            try {
                ChallengeCode.decodeChallenge(v["code"].asString)
                fail("$name: was accepted")
            } catch (e: ChallengeCodeException) {
                assertEquals(name, v["error"].asString, e.error.name.lowercase())
                assertTrue(name, !e.message.isNullOrBlank())
            }
        }
    }

    @Test
    fun crcIsOverTheBase64PartWithoutPadding() {
        val code = list("challenge").first()["code"].asString
        val body = code.removePrefix(ChallengeCode.CHALLENGE_PREFIX).substringBeforeLast('.')
        assertEquals(code.substringAfterLast('.'), ChallengeCode.crc32Hex(body))
    }

    // ---------- Cases the vectors don't cover ----------

    private fun codeFor(json: String): String {
        val body = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        return "TW1.$body.${ChallengeCode.crc32Hex(body)}"
    }

    private fun firstJson(): JsonObject = list("challenge").first().getAsJsonObject("json").deepCopy()

    private fun errorOf(code: String): ChallengeError? = try {
        ChallengeCode.decodeChallenge(code)
        null
    } catch (e: ChallengeCodeException) {
        e.error
    }

    @Test
    fun emptyAndMissingChecksum() {
        assertEquals(ChallengeError.EMPTY, errorOf("   "))
        assertEquals(ChallengeError.FORMAT, errorOf("TW1.abc"))
        assertEquals(ChallengeError.FORMAT, errorOf("TW1.eyJ2IjoxfQ.xyz"))
    }

    @Test
    fun stringVersionIsRejected() {
        val json = firstJson().apply { addProperty("v", "1") }
        assertEquals(ChallengeError.VERSION, errorOf(codeFor(json.toString())))
    }

    @Test
    fun wholeNumberFieldsMustBeIntegers() {
        val textDex = firstJson().apply { getAsJsonArray("team")[0].asJsonObject.addProperty("dex", "29") }
        assertEquals(ChallengeError.INVALID, errorOf(codeFor(textDex.toString())))
        val halfDex = firstJson().apply { getAsJsonArray("team")[0].asJsonObject.addProperty("dex", 29.5) }
        assertEquals(ChallengeError.INVALID, errorOf(codeFor(halfDex.toString())))
        // 29.0 is a whole number in JavaScript too (Number.isInteger(29.0) is true).
        val wholeDex = firstJson().apply { getAsJsonArray("team")[0].asJsonObject.addProperty("dex", 29.0) }
        assertEquals(29, ChallengeCode.decodeChallenge(codeFor(wholeDex.toString())).team[0].dex)
    }

    @Test
    fun brokenJsonIsRejected() {
        assertEquals(ChallengeError.JSON, errorOf(codeFor("{\"v\":1,")))
        assertEquals(ChallengeError.JSON, errorOf(codeFor("[1,2]")))
        assertEquals(ChallengeError.JSON, errorOf(codeFor("{'v':1}")))
        assertEquals(ChallengeError.JSON, errorOf(codeFor("{\"v\":1} {}")))
    }

    @Test
    fun badSlotsAreRejected() {
        val badRule = firstJson().apply { getAsJsonArray("team")[1].asJsonObject.addProperty("rule", "any") }
        assertEquals(ChallengeError.INVALID, errorOf(codeFor(badRule.toString())))
        val noSpecial = firstJson().apply { getAsJsonArray("team")[1].asJsonObject.remove("special") }
        assertEquals(ChallengeError.INVALID, errorOf(codeFor(noSpecial.toString())))
        val repeated = firstJson().apply { add("rerolled", JsonArray().apply { add(2); add(2) }) }
        assertEquals(ChallengeError.INVALID, errorOf(codeFor(repeated.toString())))
        val badId = firstJson().apply { addProperty("id", "ab12cd34") }
        assertEquals(ChallengeError.INVALID, errorOf(codeFor(badId.toString())))
    }

    @Test
    fun olderExactAndMinigameSlotsStillDecode() {
        val slot = ChallengeCode.decodeChallenge(list("challenge").first()["code"].asString).team[5]
        assertEquals(SlotRule.EXACT, slot.rule)
        assertEquals("minigame", slot.mode)
    }

    @Test
    fun rerollCodeKeepsTheCodeAsGiven() {
        val code = list("challenge").first()["code"].asString
        val reroll = ChallengeCode.decodeReroll(ChallengeCode.encodeReroll(code, 1))
        assertEquals(code, reroll.code)
        assertNotNull(reroll.challenge)
        assertNull(reroll.challenge.parent)
    }
}
