package io.github.bubi274.trainersworkshop.challenge

import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.editor.readCompoundList
import io.github.bubi274.trainersworkshop.editor.readStringList
import io.github.bubi274.trainersworkshop.editor.stringList
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.MinecraftServer
import net.minecraft.world.PersistentState
import java.util.UUID

/**
 * The running challenge of one player.
 * [code] is the TW1 code exactly as pasted (trimmed): PokéRoll compares reroll codes by that exact string.
 * [startSeq] is the ownership-ledger position at the original start; "Reroll 1" doesn't change it.
 * [rerollAllow] is set after the player picked "Reroll 1": the game is waiting for the rerolled TW1 code.
 */
data class ActiveChallenge(
    val code: String,
    val challenge: Challenge,
    val startSeq: Long,
    val startedAt: Long,
    val rerollAllow: Int? = null,
)

/** Everything the mod remembers about one player's challenges. */
class PlayerChallengeData {
    var active: ActiveChallenge? = null
        internal set

    /** Pokémon UUID → ledger position when this player first had it. Never shrinks. */
    internal val ledger = HashMap<UUID, Long>()

    /** Shiny feather rewards already given, as "<code id>:<slot index>". */
    internal val paidShiny = HashSet<String>()

    /** Elite Four trainer IDs beaten in challenge battles (kept across challenges, for the per-region Master Ball). */
    val eliteFourBeaten = HashSet<String>()

    /** Regions whose Master Ball was given, and champions whose Shiny Berry was given. */
    val eliteFourRegionsRewarded = HashSet<String>()
    val championsRewarded = HashSet<String>()

    /** Challenges won. */
    var completed: Int = 0
        internal set

    fun ownedSince(pokemon: UUID): Long? = ledger[pokemon]

    /** True if this Pokémon was first seen after the active challenge started. False without a challenge or for unknown Pokémon. */
    fun obtainedDuringChallenge(pokemon: UUID): Boolean {
        val start = active?.startSeq ?: return false
        val seen = ledger[pokemon] ?: return false
        return seen > start
    }

    /** Team slots with shiny = true whose feathers were not given yet (for this code or the codes it was rerolled from). */
    fun unpaidShinySlots(): List<Int> {
        val active = active ?: return emptyList()
        return active.challenge.team.indices.filter { i ->
            active.challenge.team[i].shiny && shinyKey(active.challenge.id, i) !in paidShiny
        }
    }

    fun isShinyPaid(id: String, slot: Int): Boolean = shinyKey(id, slot) in paidShiny

    internal fun markShinyPaid(id: String, slot: Int) {
        paidShiny += shinyKey(id, slot)
    }

    /**
     * A rerolled code keeps the slots that were not rerolled, so their paid rewards carry over from the parent code.
     * Rerolled slots start unpaid.
     */
    internal fun inheritPaidShiny(challenge: Challenge) {
        val parent = challenge.parent ?: return
        for (i in challenge.team.indices) {
            if (i !in challenge.rerolled && shinyKey(parent, i) in paidShiny) paidShiny += shinyKey(challenge.id, i)
        }
    }

    fun toNbt(): NbtCompound = NbtCompound().apply {
        active?.let { a ->
            put("active", NbtCompound().apply {
                putString("code", a.code)
                putLong("startSeq", a.startSeq)
                putLong("startedAt", a.startedAt)
                a.rerollAllow?.let { putInt("rerollAllow", it) }
            })
        }
        put("ledger", NbtList().apply {
            ledger.forEach { (uuid, seq) -> add(NbtCompound().apply { putUuid("uuid", uuid); putLong("seq", seq) }) }
        })
        put("paidShiny", stringList(paidShiny.sorted()))
        put("eliteFourBeaten", stringList(eliteFourBeaten.sorted()))
        put("eliteFourRegionsRewarded", stringList(eliteFourRegionsRewarded.sorted()))
        put("championsRewarded", stringList(championsRewarded.sorted()))
        putInt("completed", completed)
    }

    companion object {
        fun shinyKey(id: String, slot: Int) = "$id:$slot"

        fun fromNbt(nbt: NbtCompound): PlayerChallengeData = PlayerChallengeData().apply {
            if (nbt.contains("active")) {
                val a = nbt.getCompound("active")
                val code = a.getString("code")
                active = try {
                    ActiveChallenge(
                        code = code,
                        challenge = ChallengeCode.decodeChallenge(code),
                        startSeq = a.getLong("startSeq"),
                        startedAt = a.getLong("startedAt"),
                        rerollAllow = if (a.contains("rerollAllow")) a.getInt("rerollAllow") else null,
                    )
                } catch (e: ChallengeCodeException) {
                    TrainersWorkshop.LOGGER.warn("Dropping a saved challenge whose code can't be read: {}", e.message)
                    null
                }
            }
            readCompoundList(nbt, "ledger").forEach { if (it.containsUuid("uuid")) ledger[it.getUuid("uuid")] = it.getLong("seq") }
            paidShiny += readStringList(nbt, "paidShiny")
            eliteFourBeaten += readStringList(nbt, "eliteFourBeaten")
            eliteFourRegionsRewarded += readStringList(nbt, "eliteFourRegionsRewarded")
            championsRewarded += readStringList(nbt, "championsRewarded")
            completed = nbt.getInt("completed")
        }
    }
}

/** Why a challenge action was refused; [message] is shown to the player. */
class ChallengeActionException(message: String) : Exception(message)

/**
 * Challenge data for all players, saved in the world data (world/data/trainers_workshop_challenges.dat).
 * The ledger uses one counter for the whole world instead of the clock, so "obtained after the start" can't be
 * fooled by a clock change.
 */
class ChallengeState : PersistentState() {
    private val players = HashMap<UUID, PlayerChallengeData>()
    private var nextSeq: Long = 1

    fun player(uuid: UUID): PlayerChallengeData = players.getOrPut(uuid) { PlayerChallengeData() }

    /** Records that [player] has [pokemon]; only the first time counts. Returns true if it was new. */
    fun recordOwned(player: UUID, pokemon: UUID): Boolean {
        val data = player(player)
        if (pokemon in data.ledger) return false
        data.ledger[pokemon] = nextSeq++
        markDirty()
        return true
    }

    /**
     * Starts a challenge from a pasted TW1 code. Call [recordOwned] for every Pokémon the player has right now first:
     * those then count as owned before the start.
     */
    fun start(player: UUID, pastedCode: String, now: Long): ActiveChallenge {
        val data = player(player)
        if (data.active != null) throw ChallengeActionException("You already have a challenge. Abandon it first.")
        val code = pastedCode.trim()
        val challenge = decode(code)
        data.inheritPaidShiny(challenge)
        val active = ActiveChallenge(code, challenge, startSeq = nextSeq++, startedAt = now)
        data.active = active
        markDirty()
        return active
    }

    /** "Reroll 1" after a loss: returns the TWR1 code for PokéRoll and waits for the rerolled TW1 code. */
    fun requestReroll(player: UUID, allow: Int = 1): String {
        val data = player(player)
        val active = data.active ?: throw ChallengeActionException("You have no challenge.")
        data.active = active.copy(rerollAllow = allow)
        markDirty()
        return ChallengeCode.encodeReroll(active.code, allow)
    }

    /** Takes the TW1 code PokéRoll made from the reroll code. The original start (and so the ledger rule) is kept. */
    fun applyReroll(player: UUID, pastedCode: String): ActiveChallenge {
        val data = player(player)
        val active = data.active ?: throw ChallengeActionException("You have no challenge.")
        val allow = active.rerollAllow ?: throw ChallengeActionException("You have no reroll waiting. Choose Reroll 1 after a loss first.")
        val code = pastedCode.trim()
        val result = decode(code)
        ChallengeReroll.problem(active.challenge, allow, result)?.let { throw ChallengeActionException(it) }
        data.inheritPaidShiny(result)
        val next = active.copy(code = code, challenge = result, rerollAllow = null)
        data.active = next
        markDirty()
        return next
    }

    /** Gives up the challenge (no penalty; rewards already given stay). "Reroll all 6" ends it the same way. */
    fun abandon(player: UUID): ActiveChallenge {
        val data = player(player)
        val active = data.active ?: throw ChallengeActionException("You have no challenge.")
        data.active = null
        markDirty()
        return active
    }

    /** A challenge battle was won: the challenge is complete and the player rolls a new team. */
    fun complete(player: UUID): ActiveChallenge {
        val data = player(player)
        val active = data.active ?: throw ChallengeActionException("You have no challenge.")
        data.active = null
        data.completed++
        markDirty()
        return active
    }

    fun markShinyPaid(player: UUID, slot: Int) {
        val data = player(player)
        val active = data.active ?: return
        data.markShinyPaid(active.challenge.id, slot)
        markDirty()
    }

    private fun decode(code: String): Challenge = try {
        ChallengeCode.decodeChallenge(code)
    } catch (e: ChallengeCodeException) {
        throw ChallengeActionException(e.message ?: "This Challenge code can't be read.")
    }

    override fun writeNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup): NbtCompound = writeTo(nbt)

    internal fun writeTo(nbt: NbtCompound): NbtCompound {
        nbt.putLong("nextSeq", nextSeq)
        nbt.put("players", NbtList().apply {
            players.forEach { (uuid, data) -> add(data.toNbt().apply { putUuid("player", uuid) }) }
        })
        return nbt
    }

    companion object {
        private const val NAME = "${TrainersWorkshop.MOD_ID}_challenges"

        private val TYPE = PersistentState.Type({ ChallengeState() }, { nbt, _ -> fromNbt(nbt) }, null)

        fun fromNbt(nbt: NbtCompound): ChallengeState = ChallengeState().apply {
            nextSeq = maxOf(1L, nbt.getLong("nextSeq"))
            readCompoundList(nbt, "players").forEach {
                if (it.containsUuid("player")) players[it.getUuid("player")] = PlayerChallengeData.fromNbt(it)
            }
            // A damaged save could have a counter behind the ledger; never hand out a position twice.
            val highest = players.values.flatMap { p -> p.ledger.values + listOfNotNull(p.active?.startSeq) }.maxOrNull() ?: 0L
            if (nextSeq <= highest) nextSeq = highest + 1
        }

        fun get(server: MinecraftServer): ChallengeState =
            server.overworld.persistentStateManager.getOrCreate(TYPE, NAME)
    }
}
