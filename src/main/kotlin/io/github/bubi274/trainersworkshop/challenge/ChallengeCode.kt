package io.github.bubi274.trainersworkshop.challenge

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.CRC32

/**
 * Challenge codes (TW1) and reroll codes (TWR1), shared with PokéRoll.
 * The format is defined in docs/challenge-code.md; this follows PokéRoll's src/js/core/challenge-code.js step by step.
 * Pure code, no Minecraft classes, so it is tested with plain unit tests.
 */
object ChallengeCode {
    const val CHALLENGE_PREFIX = "TW1."
    const val REROLL_PREFIX = "TWR1."
    const val VERSION = 1
    const val TEAM_SIZE = 6

    private val TIERS = setOf("blue", "purple", "pink", "red", "gold", "minigame")
    private val MODES = setOf("case", "type", "biome", "letter", "minigame")
    private val ID = Regex("^[A-Z0-9]{8}$")
    private val BASE64URL = Regex("^[A-Za-z0-9_-]*$")
    private val HEX8 = Regex("^[0-9a-fA-F]{8}$")
    private val PADDING = Regex("={1,2}$")

    /** HTML escaping off, so "=" stays "=" (Gson writes "=" otherwise); nulls kept, for "special": null. */
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()

    // ---------- Challenge code (TW1) ----------

    /** Reads a TW1 code. Throws [ChallengeCodeException] with a sentence for the player when it is not valid. */
    fun decodeChallenge(text: String): Challenge {
        val what = "Challenge code"
        val data = unwrap(text, CHALLENGE_PREFIX, what)
        val team = data.get("team")
        if (team !is JsonArray || team.size() != TEAM_SIZE) {
            val count = if (team is JsonArray) team.size().toString() else "no"
            throw ChallengeCodeException(ChallengeError.TEAM, "This $what has $count Pokémon; a team has $TEAM_SIZE.")
        }
        return readChallenge(data, team)
    }

    /** Writes a TW1 code (the game never makes one itself; used by tests and kept for symmetry with PokéRoll). */
    fun encodeChallenge(challenge: Challenge): String {
        val json = JsonObject().apply {
            addProperty("v", VERSION)
            addProperty("id", challenge.id)
            addProperty("ts", challenge.ts)
            addProperty("app", challenge.app)
            add("team", JsonArray().apply {
                challenge.team.forEach { slot ->
                    add(JsonObject().apply {
                        addProperty("dex", slot.dex)
                        addProperty("name", slot.name)
                        add("special", slot.special?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                        addProperty("rule", slot.rule.id)
                        addProperty("shiny", slot.shiny)
                        addProperty("tier", slot.tier)
                        addProperty("mode", slot.mode)
                    })
                }
            })
            add("parent", challenge.parent?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
            add("rerolled", JsonArray().apply { challenge.rerolled.forEach { add(it) } })
        }
        return wrap(CHALLENGE_PREFIX, json)
    }

    // ---------- Reroll code (TWR1) ----------

    /** Makes the reroll code shown after a loss. [code] is the active TW1 code, exactly as the player pasted it (trimmed). */
    fun encodeReroll(code: String, allow: Int): String {
        require(allow in 1..TEAM_SIZE) { "allow must be 1..$TEAM_SIZE" }
        // Field order v, code, allow, like PokéRoll's JSON.stringify (the test vector depends on it).
        val json = JsonObject().apply {
            addProperty("v", VERSION)
            addProperty("code", code)
            addProperty("allow", allow)
        }
        return wrap(REROLL_PREFIX, json)
    }

    /** Reads a TWR1 code (only needed by tests: PokéRoll reads these, the game only writes them). */
    fun decodeReroll(text: String): RerollCode {
        val what = "reroll code"
        val data = unwrap(text, REROLL_PREFIX, what)
        val code = (data.get("code") as? JsonPrimitive)?.takeIf { it.isString }?.asString
            ?: throw ChallengeCodeException(ChallengeError.INVALID, "This $what is damaged: it has no Challenge code inside.")
        val allow = intOrNull(data.get("allow"))
        if (allow == null || allow !in 1..TEAM_SIZE) {
            throw ChallengeCodeException(ChallengeError.INVALID, "This $what is damaged: it must allow 1 to $TEAM_SIZE rerolls.")
        }
        val challenge = try {
            decodeChallenge(code)
        } catch (e: ChallengeCodeException) {
            throw ChallengeCodeException(e.error, "The Challenge code inside this $what is damaged. ${e.message}")
        }
        return RerollCode(code.trim(), allow, challenge)
    }

    // ---------- Shared frame: PREFIX + base64url(JSON) + "." + CRC32 ----------

    /** CRC32 (IEEE, same as zlib) of the ASCII base64url part, as 8 lowercase hex chars. */
    fun crc32Hex(ascii: String): String {
        val crc = CRC32()
        crc.update(ascii.toByteArray(StandardCharsets.US_ASCII))
        return "%08x".format(crc.value)
    }

    private fun wrap(prefix: String, json: JsonObject): String {
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(gson.toJson(json).toByteArray(StandardCharsets.UTF_8))
        return "$prefix$body.${crc32Hex(body)}"
    }

    /**
     * Reads the shared frame and returns the JSON object (with v = 1 checked).
     * Spaces around the code are ignored; "=" padding and an uppercase CRC are accepted.
     */
    private fun unwrap(text: String, prefix: String, what: String): JsonObject {
        val code = text.trim()
        if (code.isEmpty()) throw ChallengeCodeException(ChallengeError.EMPTY, "Paste a $what first.")
        if (!code.startsWith(prefix)) throw ChallengeCodeException(ChallengeError.PREFIX, "This is not a $what: it must start with \"$prefix\".")
        val rest = code.substring(prefix.length)
        val dot = rest.lastIndexOf('.')
        if (dot < 0) throw ChallengeCodeException(ChallengeError.FORMAT, "This $what is incomplete: the checksum after the last \".\" is missing.")
        val body = rest.substring(0, dot).replace(PADDING, "")
        val crc = rest.substring(dot + 1)
        val bytes = base64urlDecode(body)
        if (body.isEmpty() || bytes == null) {
            throw ChallengeCodeException(ChallengeError.BASE64, "This $what is damaged: part of it is missing or has characters that can't be in a code.")
        }
        if (!HEX8.matches(crc)) throw ChallengeCodeException(ChallengeError.FORMAT, "This $what is damaged: the checksum must be 8 letters or digits (0-9, a-f).")
        if (crc.lowercase() != crc32Hex(body)) throw ChallengeCodeException(ChallengeError.CRC, "This $what has a typo: its checksum doesn't match. Copy it again.")
        val data = parseObject(bytes) ?: throw ChallengeCodeException(ChallengeError.JSON, "This $what is damaged: its contents can't be read.")
        val v = data.get("v")
        if (!isNumber(v, VERSION.toLong())) {
            val shown = if (v == null) "no version" else "version ${gson.toJson(v)}"
            throw ChallengeCodeException(ChallengeError.VERSION, "This $what has $shown; this mod reads version $VERSION codes.")
        }
        return data
    }

    /** base64url without "=" → bytes; null for characters outside base64url or an impossible length (4n+1). */
    private fun base64urlDecode(text: String): ByteArray? {
        if (!BASE64URL.matches(text) || text.length % 4 == 1) return null
        return try {
            Base64.getUrlDecoder().decode(text)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Strict UTF-8 + strict JSON (like JSON.parse); null unless the whole text is one JSON object. */
    private fun parseObject(bytes: ByteArray): JsonObject? {
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            return null
        }
        return try {
            val reader = JsonReader(StringReader(text))
            @Suppress("DEPRECATION")
            reader.isLenient = false
            // The adapter keeps the reader strict (JsonParser.parseReader would switch it to lenient).
            val element = gson.getAdapter(JsonElement::class.java).read(reader)
            if (reader.peek() != JsonToken.END_DOCUMENT) return null
            element as? JsonObject
        } catch (e: JsonParseException) {
            null
        } catch (e: java.io.IOException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }

    // ---------- Fields ----------

    private fun readChallenge(d: JsonObject, team: JsonArray): Challenge {
        fun damaged(problem: String): Nothing =
            throw ChallengeCodeException(ChallengeError.INVALID, "This Challenge code is damaged: ${problem.replaceFirstChar { it.lowercase() }}")

        val id = stringOrNull(d.get("id"))?.takeIf { ID.matches(it) } ?: damaged("The code id must be 8 letters A-Z or digits.")
        val ts = longOrNull(d.get("ts"))?.takeIf { it >= 0 } ?: damaged("The code has no valid time.")
        val app = stringOrNull(d.get("app")) ?: damaged("The code doesn't say which app made it.")
        val slots = team.mapIndexed { i, element -> readSlot(element, i) ?: damaged(slotProblem(element, i)) }
        val parentElement = d.get("parent")
        val parent = when {
            parentElement == null || parentElement.isJsonNull -> null
            else -> stringOrNull(parentElement)?.takeIf { ID.matches(it) } ?: damaged("The code has an invalid parent id.")
        }
        val rerolledElement = d.get("rerolled") as? JsonArray ?: damaged("The code has an invalid list of rerolled slots.")
        val rerolled = rerolledElement.map { intOrNull(it)?.takeIf { n -> n in 0 until TEAM_SIZE } ?: damaged("The code has an invalid list of rerolled slots.") }
        if (rerolled.toSet().size != rerolled.size) damaged("The code has an invalid list of rerolled slots.")
        return Challenge(id, ts, app, slots, parent, rerolled)
    }

    private fun readSlot(element: JsonElement, index: Int): ChallengeSlot? {
        if (slotProblem(element, index).isNotEmpty()) return null
        val s = element.asJsonObject
        val special = s.get("special").let { if (it.isJsonNull) null else it.asString }
        return ChallengeSlot(
            dex = intOrNull(s.get("dex"))!!,
            name = s.get("name").asString,
            special = special,
            rule = SlotRule.of(s.get("rule").asString)!!,
            shiny = s.get("shiny").asBoolean,
            tier = s.get("tier").asString,
            mode = s.get("mode").asString,
        )
    }

    /** The first problem of a team slot, "" if it is valid (same checks and order as PokéRoll). */
    private fun slotProblem(element: JsonElement, index: Int): String {
        val at = "Team slot ${index + 1}"
        val s = element as? JsonObject ?: return "$at is not a Pokémon."
        val dex = intOrNull(s.get("dex"))
        if (dex == null || dex < 1) return "$at has no valid Pokédex number."
        if (stringOrNull(s.get("name")).isNullOrEmpty()) return "$at has no name."
        val special = s.get("special")
        if (!(special != null && (special.isJsonNull || !stringOrNull(special).isNullOrEmpty()))) return "$at has an invalid Special id."
        val rule = stringOrNull(s.get("rule"))
        if (rule == null || SlotRule.of(rule) == null) return "$at has an unknown rule ${shown(s.get("rule"))}."
        val shiny = s.get("shiny")
        if (!(shiny is JsonPrimitive && shiny.isBoolean)) return "$at has no Shiny flag."
        if (stringOrNull(s.get("tier")) !in TIERS) return "$at has an unknown tier ${shown(s.get("tier"))}."
        if (stringOrNull(s.get("mode")) !in MODES) return "$at has an unknown mode ${shown(s.get("mode"))}."
        return ""
    }

    /** Like JSON.stringify for an error message; a missing value prints as "undefined", like in PokéRoll. */
    private fun shown(element: JsonElement?): String = if (element == null) "undefined" else gson.toJson(element)

    private fun stringOrNull(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.asString

    /**
     * A JSON number that is a whole number (like Number.isInteger: 3 and 3.0 count, 3.5 and "3" don't).
     * Gson's getAsInt would accept "3" and turn 3.5 into 3, so this checks the exact value.
     */
    private fun longOrNull(element: JsonElement?): Long? {
        val p = element as? JsonPrimitive ?: return null
        if (!p.isNumber) return null
        return try {
            val value = BigDecimal(p.asString)
            if (value.signum() != 0 && value.stripTrailingZeros().scale() > 0) null else value.longValueExact()
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun intOrNull(element: JsonElement?): Int? =
        longOrNull(element)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    private fun isNumber(element: JsonElement?, expected: Long): Boolean = longOrNull(element) == expected
}

/** What kind of problem a code has (the vectors' "error" values are the lowercase names). */
enum class ChallengeError { EMPTY, PREFIX, FORMAT, BASE64, CRC, JSON, VERSION, TEAM, INVALID }

class ChallengeCodeException(val error: ChallengeError, message: String) : Exception(message)

/** "evo": that Pokémon and all its later evolutions. "exact": only that species, base form (older codes only). */
enum class SlotRule(val id: String) {
    EVO("evo"),
    EXACT("exact");

    companion object {
        fun of(id: String): SlotRule? = entries.firstOrNull { it.id == id }
    }
}

/** One team slot. A slot is identified by [dex] + [special]; [name] is only for people to read. */
data class ChallengeSlot(
    val dex: Int,
    val name: String,
    val special: String?,
    val rule: SlotRule,
    val shiny: Boolean,
    val tier: String,
    val mode: String,
)

/** A decoded TW1 code (only the known fields; unknown fields are ignored). [ts] is in unix seconds. */
data class Challenge(
    val id: String,
    val ts: Long,
    val app: String,
    val team: List<ChallengeSlot>,
    val parent: String?,
    val rerolled: List<Int>,
)

/** A decoded TWR1 code: the TW1 code inside it (trimmed), how many slots may be rerolled, and that code's challenge. */
data class RerollCode(val code: String, val allow: Int, val challenge: Challenge)
