package io.github.bubi274.trainersworkshop.challenge

import io.github.bubi274.trainersworkshop.challenge.PartyCheck.Member
import io.github.bubi274.trainersworkshop.challenge.PartyCheck.Problem
import io.github.bubi274.trainersworkshop.challenge.PartyCheck.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyCheckTest {
    private fun k(species: String, form: String = "normal") = FormKey.of("cobblemon:$species", form)

    /** A small evolution graph: Meowth (normal / alola / galar), Eevee with a Kazeran branch, a loop. */
    private val graph: Map<FormKey, List<FormKey>> = mapOf(
        k("meowth") to listOf(k("persian")),
        k("meowth", "alola") to listOf(k("persian", "alola")),
        k("meowth", "galar") to listOf(k("perrserker")),
        k("eevee") to listOf(k("vaporeon"), k("glaceon"), k("glaceon", "kazeran")),
        k("loop_a") to listOf(k("loop_b")),
        k("loop_b") to listOf(k("loop_a")),
    )
    private val next: (FormKey) -> Collection<FormKey> = { graph[it].orEmpty() }

    @Test
    fun allFormsAndTheirEvolutions() {
        val starts = listOf(k("meowth"), k("meowth", "alola"), k("meowth", "galar"))
        val allowed = PartyCheck.allowedForms(starts, evolve = true, next = next)
        assertEquals(
            setOf(k("meowth"), k("meowth", "alola"), k("meowth", "galar"), k("persian"), k("persian", "alola"), k("perrserker")),
            allowed,
        )
    }

    @Test
    fun exactAllowsOnlyTheStart() {
        assertEquals(setOf(k("meowth")), PartyCheck.allowedForms(listOf(k("meowth")), evolve = false, next = next))
    }

    @Test
    fun evolutionsReachDatapackForms() {
        assertTrue(k("glaceon", "kazeran") in PartyCheck.allowedForms(listOf(k("eevee")), evolve = true, next = next))
    }

    @Test
    fun aLoopInTheDataEnds() {
        assertEquals(setOf(k("loop_a"), k("loop_b")), PartyCheck.allowedForms(listOf(k("loop_a")), evolve = true, next = next))
    }

    @Test
    fun formNamesIgnoreCase() {
        assertEquals(FormKey.of("Cobblemon:Meowth", "Galar"), k("meowth", "galar"))
    }

    @Test
    fun aFittingPartyIsValid() {
        val result = PartyCheck.check(listOf(Member(true, setOf(0)), Member(true, setOf(1, 2))))
        assertTrue(result.valid)
        assertEquals(mapOf(0 to 0, 1 to 1), result.slotOf)
    }

    @Test
    fun twoPokemonNeedTwoSlots() {
        // Two Pikachu, one Pikachu slot: the second one has no free slot.
        val one = PartyCheck.check(listOf(Member(true, setOf(3)), Member(true, setOf(3))))
        assertEquals(listOf(Problem(1, Reason.NO_FREE_SLOT)), one.problems)
        // Two Pikachu slots: both fit.
        assertTrue(PartyCheck.check(listOf(Member(true, setOf(3, 4)), Member(true, setOf(3, 4)))).valid)
    }

    @Test
    fun matchingDoesNotDependOnPartyOrder() {
        // The first Pokémon could take slot 0 or 1, the second only slot 0: a good matching gives each one a slot.
        val result = PartyCheck.check(listOf(Member(true, setOf(0, 1)), Member(true, setOf(0))))
        assertTrue(result.valid)
        assertEquals(mapOf(0 to 1, 1 to 0), result.slotOf)
    }

    @Test
    fun eachProblemHasItsReason() {
        val result = PartyCheck.check(
            listOf(
                Member(false, setOf(0)),
                Member(true, emptySet()),
                Member(true, setOf(2)),
                Member(true, setOf(2)),
            ),
        )
        assertEquals(
            listOf(Problem(0, Reason.OWNED_BEFORE), Problem(1, Reason.NOT_ON_TEAM), Problem(3, Reason.NO_FREE_SLOT)),
            result.problems,
        )
    }

    @Test
    fun anEmptyPartyHasNoProblems() {
        assertTrue(PartyCheck.check(emptyList()).valid)
    }
}
