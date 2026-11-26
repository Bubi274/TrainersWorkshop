package io.github.bubi274.trainersworkshop.challenge

import com.google.gson.Gson
import io.github.bubi274.trainersworkshop.challenge.TrainerRole.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTrainersTest {
    private val regions = ChallengeTrainers.DEFAULTS

    @Test
    fun theFourCobbleverseRegionsHaveFullLeagues() {
        regions.filter { it.enabled }.forEach { r ->
            assertEquals(r.id, 8, r.gymLeaders.size)
            assertEquals(r.id, 4, r.eliteFour.size)
            assertEquals(r.id, 1, r.champions.size)
        }
        assertEquals(listOf("kanto", "johto", "hoenn", "sinnoh"), regions.filter { it.enabled }.map { it.id })
    }

    @Test
    fun rolesByTrainerId() {
        assertEquals(Kind.GYM_LEADER, ChallengeTrainers.role(regions, "kanto_brock")!!.kind)
        val agatha = ChallengeTrainers.role(regions, "kanto_league_agatha")!!
        assertEquals(Kind.ELITE_FOUR, agatha.kind)
        assertEquals("kanto", agatha.region.id)
        assertEquals("kanto_league_agatha", agatha.memberKey)
        // Hoenn's Elite Four share their RCT type with the gyms; the ID list tells them apart.
        assertEquals(Kind.ELITE_FOUR, ChallengeTrainers.role(regions, "hoenn_league_drake")!!.kind)
        assertEquals(Kind.CHAMPION, ChallengeTrainers.role(regions, "sinnoh_champion_camilla")!!.kind)
    }

    @Test
    fun otherTrainersAreNotChallengeBattles() {
        assertNull(ChallengeTrainers.role(regions, "hoenn_pat"))
        assertNull(ChallengeTrainers.role(regions, "old_sage"))
        assertNull(ChallengeTrainers.role(regions, "prof_prof_oak_01ff"))
    }

    @Test
    fun disabledRegionsDontCount() {
        assertNull(ChallengeTrainers.role(regions, "leader_brock_019e"))
        val on = regions.map { if (it.id == "radicalred") it.copy(enabled = true) else it }
        val terry = ChallengeTrainers.role(on, "champion_terry_01b7")!!
        assertEquals(Kind.CHAMPION, terry.kind)
        assertEquals("champion_terry_01b6", terry.memberKey)
    }

    @Test
    fun eliteFourIsCompleteWhenAllFourAreBeaten() {
        val kanto = regions.first { it.id == "kanto" }
        val three = setOf("kanto_league_lorelei", "kanto_league_bruno", "kanto_league_agatha")
        assertFalse(ChallengeTrainers.eliteFourComplete(kanto, three))
        assertTrue(ChallengeTrainers.eliteFourComplete(kanto, three + "kanto_league_lance"))
        // Beating Elite Four of different regions doesn't add up.
        assertFalse(ChallengeTrainers.eliteFourComplete(kanto, three + "johto_league_karen"))
    }

    @Test
    fun anyVersionOfAMemberCounts() {
        val rr = regions.first { it.id == "radicalred" }
        val beaten = setOf("elite_four_lorelei_004e", "elite_four_bruno_0050", "elite_four_agatha_0054", "elite_four_lance_02e2")
        assertTrue(ChallengeTrainers.eliteFourComplete(rr, beaten))
    }

    @Test
    fun aHandEditedConfigIsRepaired() {
        val json = """[{"id":" kanto ","enabled":true,"gymLeaders":["kanto_brock",""," kanto_misty "],"eliteFour":[["kanto_league_lorelei"],[]]}]"""
        val parsed = Gson().fromJson(json, Array<ChallengeRegion>::class.java).toList()
        val clean = ChallengeTrainers.sanitize(parsed)
        assertEquals("kanto", clean[0].id)
        assertEquals(listOf("kanto_brock", "kanto_misty"), clean[0].gymLeaders)
        assertEquals(listOf(listOf("kanto_league_lorelei")), clean[0].eliteFour)
        assertEquals(emptyList<List<String>>(), clean[0].champions)
        assertEquals(ChallengeTrainers.DEFAULTS, ChallengeTrainers.sanitize(null))
    }
}
