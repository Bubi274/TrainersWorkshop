package io.github.bubi274.trainersworkshop.challenge

/** A species + form, for example cobblemon:meowth / "galar". Form names are compared in lowercase. */
data class FormKey(val species: String, val form: String) {
    companion object {
        fun of(species: String, form: String) = FormKey(species.lowercase(), form.lowercase())
    }
}

/**
 * Which Pokémon a team slot allows, and whether a party fits the team. Plain values only, so plain JUnit can test it;
 * CobblemonForms supplies the real species, forms and evolutions.
 */
object PartyCheck {
    /**
     * Every form reachable from [starts]: the starts themselves, plus (when [evolve] is true, rule "evo") every later
     * evolution, following [next] (the direct evolutions of a form). Loops in the data are safe.
     */
    fun allowedForms(starts: Collection<FormKey>, evolve: Boolean, next: (FormKey) -> Collection<FormKey>): Set<FormKey> {
        val seen = LinkedHashSet<FormKey>(starts)
        if (!evolve) return seen
        val queue = ArrayDeque(starts)
        while (queue.isNotEmpty()) {
            for (child in next(queue.removeFirst())) {
                if (seen.add(child)) queue.addLast(child)
            }
        }
        return seen
    }

    enum class Reason { OWNED_BEFORE, NOT_ON_TEAM, NO_FREE_SLOT }

    /** One party Pokémon: was it obtained during the challenge, and which team slots (0-5) does its form allow. */
    data class Member(val obtainedDuringChallenge: Boolean, val slots: Set<Int>)

    data class Problem(val member: Int, val reason: Reason)

    /** Problems of the party (empty = the party is valid) and, for valid members, the slot each one fills. */
    data class Result(val problems: List<Problem>, val slotOf: Map<Int, Int>) {
        val valid: Boolean get() = problems.isEmpty()
    }

    /**
     * Party ↔ team is one-to-one: each party Pokémon needs its own slot. Pokémon owned before the start and Pokémon
     * that match no slot are problems first; the rest are matched as well as possible (so the order of the party
     * doesn't matter), and any that still have no slot left are NO_FREE_SLOT.
     */
    fun check(members: List<Member>): Result {
        val problems = mutableListOf<Problem>()
        val candidates = HashMap<Int, Set<Int>>()
        members.forEachIndexed { i, m ->
            when {
                !m.obtainedDuringChallenge -> problems += Problem(i, Reason.OWNED_BEFORE)
                m.slots.isEmpty() -> problems += Problem(i, Reason.NOT_ON_TEAM)
                else -> candidates[i] = m.slots
            }
        }
        val slotOf = match(candidates)
        candidates.keys.filter { it !in slotOf }.forEach { problems += Problem(it, Reason.NO_FREE_SLOT) }
        return Result(problems.sortedBy { it.member }, slotOf)
    }

    /** Maximum bipartite matching (augmenting paths): party member → slot. */
    private fun match(candidates: Map<Int, Set<Int>>): Map<Int, Int> {
        val memberOfSlot = HashMap<Int, Int>()
        fun tryAssign(member: Int, visited: MutableSet<Int>): Boolean {
            for (slot in candidates.getValue(member).sorted()) {
                if (!visited.add(slot)) continue
                val holder = memberOfSlot[slot]
                if (holder == null || tryAssign(holder, visited)) {
                    memberOfSlot[slot] = member
                    return true
                }
            }
            return false
        }
        candidates.keys.sorted().forEach { tryAssign(it, HashSet()) }
        return memberOfSlot.entries.associate { (slot, member) -> member to slot }
    }
}
