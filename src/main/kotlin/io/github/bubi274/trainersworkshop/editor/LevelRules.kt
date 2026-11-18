package io.github.bubi274.trainersworkshop.editor

/**
 * Level rules of the Survival PokeEditor: level can only go up, each level costs exactly 1 Rare Candy,
 * and it can't go over the level cap. Doesn't depend on Minecraft/Cobblemon, so plain JUnit can test it.
 */
object LevelRules {
    /** Rare Candies needed to go from [from] to [to] (0 if the level doesn't go up). */
    fun cost(from: Int, to: Int): Int = maxOf(0, to - from)

    /** Error key: "level_decrease" if the level goes down, "level_over_cap" if it goes over [cap]; null if valid. */
    fun error(from: Int, to: Int, cap: Int): String? = when {
        to < from -> "level_decrease"
        to > from && to > cap -> "level_over_cap"
        else -> null
    }

    /** Highest level reachable: min(cap, from + Rare Candies owned). Never lower than [from]. */
    fun maxLevel(from: Int, cap: Int, rareCandies: Int): Int = maxOf(from, minOf(cap, from + maxOf(0, rareCandies)))
}
