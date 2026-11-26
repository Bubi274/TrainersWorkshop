package io.github.bubi274.trainersworkshop.challenge

/**
 * Checks the TW1 code the player pastes after "Reroll 1": it must be the active team with at most [allow] slots
 * rolled again (docs/challenge-code.md, "Rerolls").
 */
object ChallengeReroll {
    /** Returns null when [result] is a valid reroll of [active], otherwise a sentence for the player. */
    fun problem(active: Challenge, allow: Int, result: Challenge): String? {
        if (result.parent != active.id) {
            return if (result.parent == null) {
                "This is a new team, not a reroll of your team ${active.id}."
            } else {
                "This team was rerolled from ${result.parent}, not from your team ${active.id}."
            }
        }
        if (result.id == active.id) return "This is your current team, not a rerolled one."
        if (result.rerolled.size > allow) {
            return "This team has ${result.rerolled.size} rerolled slots; you may reroll $allow."
        }
        for (i in 0 until ChallengeCode.TEAM_SIZE) {
            val before = active.team[i]
            val after = result.team[i]
            if (i !in result.rerolled) {
                if (after != before) return "Slot ${i + 1} changed, but it was not rerolled."
                continue
            }
            if (!keepsSource(before, after)) {
                return "Slot ${i + 1} was rolled from a different source (${before.mode} → ${after.mode})."
            }
        }
        return null
    }

    /**
     * A rerolled slot is rolled again from the same mode. Exception: a Minigame slot (older codes) is rerolled
     * from Case, so it becomes "case" / "evo" with a normal tier.
     */
    private fun keepsSource(before: ChallengeSlot, after: ChallengeSlot): Boolean =
        if (before.mode == "minigame") {
            after.mode == "case" && after.rule == SlotRule.EVO && after.tier != "minigame"
        } else {
            after.mode == before.mode
        }
}
