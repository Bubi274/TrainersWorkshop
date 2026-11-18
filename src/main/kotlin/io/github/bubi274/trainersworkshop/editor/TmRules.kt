package io.github.bubi274.trainersworkshop.editor

/*
 * Which TM pays for teaching a new move in the Survival PokeEditor.
 * Only plain values, so it doesn't depend on Minecraft/Cobblemon and plain JUnit can test it.
 */

/** Where a TM comes from. */
enum class TmSource {
    /** Cobblemon 1.8 TM: the cobblemon:technical_machine item with a cobblemon:tm_move component. */
    COBBLEMON,

    /** TMCraft TM: one item per move (tmcraft:tm_<move>). Optional; the mod works without TMCraft. */
    TMCRAFT,
}

object TmRules {
    /**
     * Picks the TM used for one move.
     * - A Cobblemon TM wins when the player has one, then a TMCraft TM when the player has one.
     * - With neither in the inventory, the first source that could teach the move is returned,
     *   so the cost line can say which TM is missing.
     * - null if no installed TM can teach the move.
     */
    fun choose(cobblemonCanTeach: Boolean, tmcraftCanTeach: Boolean, haveCobblemon: Int, haveTmcraft: Int): TmSource? = when {
        cobblemonCanTeach && haveCobblemon > 0 -> TmSource.COBBLEMON
        tmcraftCanTeach && haveTmcraft > 0 -> TmSource.TMCRAFT
        cobblemonCanTeach -> TmSource.COBBLEMON
        tmcraftCanTeach -> TmSource.TMCRAFT
        else -> null
    }

    /**
     * Whether teaching uses the TM up. TMCraft TMs always are (as before).
     * Cobblemon TMs follow TechnicalMachineItem: not used up when the infiniteTmUses config is on.
     */
    fun consumes(source: TmSource, infiniteCobblemonTms: Boolean): Boolean = when (source) {
        TmSource.COBBLEMON -> !infiniteCobblemonTms
        TmSource.TMCRAFT -> true
    }
}
