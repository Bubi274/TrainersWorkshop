package io.github.bubi274.trainersworkshop.editor

/*
 * Trainer Alpha boost of the Trainer Editor. Plain values only, so plain JUnit can test it.
 * Cobblemon's own alpha battle boost is the showdown rule "Wild Alpha", which Cobblemon only adds to wild battles
 * (BattleBuilder.kt 1.8.4, line 285). Trainer Alphas get real stat boosts from IVs/levels instead.
 */
object AlphaRules {
    const val MAX_LEVEL = 100
    const val DEFAULT_LEVEL_BONUS = 5

    /** Level after the Alpha boost: [level] + [bonus], never above [cap] (and never lower than [level]). */
    fun boostedLevel(level: Int, bonus: Int, cap: Int): Int = maxOf(level, minOf(cap, level + maxOf(0, bonus)))

    /** Like a wild Alpha, a trainer Alpha should know a TM move; true if none of [moves] is in [tmMoves]. */
    fun needsTmMove(moves: List<String>, tmMoves: Set<String>): Boolean = moves.none { it.isNotEmpty() && it in tmMoves }

    /** Slot for the added TM move: the first empty slot, otherwise the last one (the alpha moveset builder puts TM moves in slots 3-4). */
    fun tmMoveSlot(moves: List<String>): Int {
        val empty = moves.indexOfFirst { it.isEmpty() }
        return if (empty >= 0) empty else moves.lastIndex
    }

    /** The level bonus from the config, kept within 0..[MAX_LEVEL]. */
    fun levelBonus(configValue: Int): Int = configValue.coerceIn(0, MAX_LEVEL)
}
