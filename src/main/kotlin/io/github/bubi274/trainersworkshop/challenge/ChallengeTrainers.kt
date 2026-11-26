package io.github.bubi274.trainersworkshop.challenge

/**
 * The RCT trainers that are challenge battles, by region (config "challengeRegions").
 * [eliteFour] and [champions] hold one entry per person; an entry lists that person's RCT trainer IDs, because RCT's
 * own series have several versions of the same trainer (for example elite_four_lorelei_004d and _004e).
 */
data class ChallengeRegion(
    val id: String = "",
    val enabled: Boolean = true,
    val gymLeaders: List<String> = emptyList(),
    val eliteFour: List<List<String>> = emptyList(),
    val champions: List<List<String>> = emptyList(),
)

/** What a trainer is in challenge mode. [member] is the index in [ChallengeRegion.eliteFour] or [ChallengeRegion.champions]. */
data class TrainerRole(val region: ChallengeRegion, val kind: Kind, val member: Int) {
    enum class Kind { GYM_LEADER, ELITE_FOUR, CHAMPION }

    /** The ID that stands for this Elite Four member or champion (its first trainer ID), for rewards. */
    val memberKey: String
        get() = when (kind) {
            Kind.GYM_LEADER -> ""
            Kind.ELITE_FOUR -> region.eliteFour[member].first()
            Kind.CHAMPION -> region.champions[member].first()
        }
}

/** Plain values only, so plain JUnit can test it. */
object ChallengeTrainers {
    /** The role of [trainerId] in the first enabled region that lists it, or null if it is not a challenge trainer. */
    fun role(regions: List<ChallengeRegion>, trainerId: String): TrainerRole? {
        for (region in regions) {
            if (!region.enabled) continue
            if (trainerId in region.gymLeaders) return TrainerRole(region, TrainerRole.Kind.GYM_LEADER, 0)
            region.eliteFour.indexOfFirst { trainerId in it }.takeIf { it >= 0 }?.let { return TrainerRole(region, TrainerRole.Kind.ELITE_FOUR, it) }
            region.champions.indexOfFirst { trainerId in it }.takeIf { it >= 0 }?.let { return TrainerRole(region, TrainerRole.Kind.CHAMPION, it) }
        }
        return null
    }

    /** True when every Elite Four member of [region] has been beaten (any of its trainer IDs is in [beaten]). */
    fun eliteFourComplete(region: ChallengeRegion, beaten: Set<String>): Boolean =
        region.eliteFour.isNotEmpty() && region.eliteFour.all { member -> member.any { it in beaten } }

    /** Repairs a config edited by hand: missing lists become empty, blank IDs and empty members are dropped. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun sanitize(regions: List<ChallengeRegion>?): List<ChallengeRegion> =
        (regions ?: DEFAULTS).filter { it != null }.map { r ->
            ChallengeRegion(
                id = (r.id ?: "").trim(),
                enabled = r.enabled,
                gymLeaders = (r.gymLeaders ?: emptyList()).filter { it != null && it.isNotBlank() }.map { it.trim() },
                eliteFour = cleanMembers(r.eliteFour),
                champions = cleanMembers(r.champions),
            )
        }

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun cleanMembers(members: List<List<String>>?): List<List<String>> =
        (members ?: emptyList()).filter { it != null }
            .map { ids -> ids.filter { it != null && it.isNotBlank() }.map { it.trim() } }
            .filter { it.isNotEmpty() }

    private fun one(vararg ids: String): List<List<String>> = ids.map { listOf(it) }

    /**
     * Defaults from COBBLEVERSE-RCT-DP-v20 (same gyms / Elite Four / champions in v21, COBBLEVERSE 1.8.0).
     * The four COBBLEVERSE regions are on; RCT's own series (Radical Red, BDSP, Unbound) are listed but off.
     */
    val DEFAULTS: List<ChallengeRegion> = listOf(
        ChallengeRegion(
            "kanto", true,
            listOf("kanto_brock", "kanto_misty", "kanto_ltsurge", "kanto_erika", "kanto_koga", "kanto_sabrina", "kanto_blaine", "kanto_giovanni"),
            one("kanto_league_lorelei", "kanto_league_bruno", "kanto_league_agatha", "kanto_league_lance"),
            one("kanto_champion_blue"),
        ),
        ChallengeRegion(
            "johto", true,
            listOf("johto_valerio", "johto_raffaello", "johto_chiara", "johto_angelo", "johto_furio", "johto_jasmine", "johto_alfredo", "johto_sandra"),
            one("johto_league_pino", "johto_league_koga", "johto_league_bruno", "johto_league_karen"),
            one("johto_champion_lance"),
        ),
        ChallengeRegion(
            "hoenn", true,
            listOf("hoenn_petra", "hoenn_rudi", "hoenn_walter", "hoenn_fiammetta", "hoenn_norman", "hoenn_alice", "hoenn_tell", "hoenn_adriano"),
            one("hoenn_league_fosco", "hoenn_league_ester", "hoenn_league_frida", "hoenn_league_drake"),
            one("hoenn_champion_rocco"),
        ),
        ChallengeRegion(
            "sinnoh", true,
            listOf("sinnoh_pedro", "sinnoh_gardenia", "sinnoh_marzia", "sinnoh_omar", "sinnoh_fannie", "sinnoh_ferruccio", "sinnoh_bianca", "sinnoh_corrado"),
            one("sinnoh_league_aaron", "sinnoh_league_terrie", "sinnoh_league_vulcano", "sinnoh_league_luciano"),
            one("sinnoh_champion_camilla"),
        ),
        ChallengeRegion(
            "radicalred", false,
            listOf(
                "leader_brock_019e", "leader_misty_019f", "leader_lt_surge_01a0", "leader_erika_01a1",
                "leader_koga_01a2", "leader_sabrina_01a4", "leader_blaine_01a3", "leader_clair_004a",
            ),
            listOf(
                listOf("elite_four_lorelei_004d", "elite_four_lorelei_004e"),
                listOf("elite_four_bruno_0050", "elite_four_bruno_0051"),
                listOf("elite_four_agatha_0053", "elite_four_agatha_0054"),
                listOf("elite_four_lance_0056", "elite_four_lance_0057", "elite_four_lance_019d", "elite_four_lance_02e2"),
            ),
            listOf(listOf("champion_terry_01b6", "champion_terry_01b7", "champion_terry_01b8")),
        ),
        ChallengeRegion("bdsp", false, listOf("gym_leader_byron_0399"), one("elite_four_lucian_03a2"), emptyList()),
        ChallengeRegion("unbound", false, emptyList(), one("elite_four_moleman_05b3", "elite_four_penny_05b6"), emptyList()),
    )
}
