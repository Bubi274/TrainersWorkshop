package io.github.bubi274.trainersworkshop.editor

/*
 * How the Survival PokeEditor pays for a move. Plain values only, so plain JUnit can test it.
 */

/** What a move costs in the Survival PokeEditor. */
enum class MoveSource {
    /** Already known or relearnable: free. */
    FREE,

    /** Taught with a TM (Cobblemon or TMCraft, see [TmRules]). */
    TM,

    /** An egg move that no TM can teach: paid with Type Gems of the move's type. */
    GEM,

    /** Can't be learned in Survival. */
    NONE,
}

object MoveSourceRules {
    const val MIN_GEM_COST = 1
    const val MAX_GEM_COST = 64
    const val DEFAULT_GEM_COST = 3

    /**
     * Free moves keep the existing rules, TM moves keep using TMs, and only egg moves that no TM can teach use Type Gems.
     * @param gemExists a Type Gem item exists for the move's type.
     */
    fun source(free: Boolean, tmCanTeach: Boolean, isEggMove: Boolean, gemExists: Boolean): MoveSource = when {
        free -> MoveSource.FREE
        tmCanTeach -> MoveSource.TM
        isEggMove && gemExists -> MoveSource.GEM
        else -> MoveSource.NONE
    }

    /** The gem cost from the config, kept within [MIN_GEM_COST]..[MAX_GEM_COST]. */
    fun gemCost(configValue: Int): Int = configValue.coerceIn(MIN_GEM_COST, MAX_GEM_COST)
}
