package io.github.bubi274.trainersworkshop.challenge

/** The shiny slot reward: 128 EV feathers of one stat, picked by the player. Plain values only (tested with JUnit). */
object ShinyReward {
    const val FEATHERS_PER_SLOT = 128

    /** Stat name the player picks → Cobblemon feather item ID. */
    val FEATHERS: Map<String, String> = linkedMapOf(
        "hp" to "cobblemon:health_feather",
        "attack" to "cobblemon:muscle_feather",
        "defence" to "cobblemon:resist_feather",
        "special_attack" to "cobblemon:genius_feather",
        "special_defence" to "cobblemon:clever_feather",
        "speed" to "cobblemon:swift_feather",
    )

    /** Short labels for the chat buttons, in the same order as [FEATHERS]. */
    val LABELS: Map<String, String> = linkedMapOf(
        "hp" to "HP", "attack" to "Atk", "defence" to "Def",
        "special_attack" to "SpA", "special_defence" to "SpD", "speed" to "Spe",
    )

    fun feather(stat: String): String? = FEATHERS[stat.lowercase()]

    /** [total] items cut into stacks of at most [maxStack] (128 feathers → 64 + 64). */
    fun stacks(total: Int, maxStack: Int): List<Int> {
        if (total <= 0 || maxStack <= 0) return emptyList()
        val full = List(total / maxStack) { maxStack }
        val rest = total % maxStack
        return if (rest > 0) full + rest else full
    }
}
