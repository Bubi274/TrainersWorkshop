package io.github.bubi274.trainersworkshop.editor

/*
 * IV rules of the Survival PokeEditor: Bottle Caps (Only Bottle Caps) and IV candies (Cobblemon).
 * The rules only use plain numbers, so they don't depend on Minecraft/Cobblemon and plain JUnit can test them.
 * The client uses them to plan the draft, the server uses them to check the cost and to apply it.
 */

/** Value in PokemonEditData.caps when no cap is used on that stat. */
const val NO_CAP = -1

/**
 * What a Bottle Cap does, which depends on the Only Bottle Caps version and config.
 * Checked against the OBC 1.3.0 and 1.5.0 jars (BottleCap.applyToPokemon).
 */
enum class BottleCapMode(val hintSuffix: String) {
    /** OBC before 1.5: calls Pokemon.setIV, so the base IV changes and the hyper-trained IV is untouched. */
    BASE_IV(""),

    /** OBC 1.5+ with breedingBuff = false (the default, also in COBBLEVERSE 1.8.0): only IVs.setHyperTrainedIV. */
    HYPER_TRAIN(".hyper"),

    /** OBC 1.5+ with breedingBuff = true: Pokemon.setIV, then IVs.setHyperTrainedIV. */
    BOTH(".both");

    companion object {
        fun fromName(name: String): BottleCapMode = entries.firstOrNull { it.name == name } ?: HYPER_TRAIN
    }
}

/** IV state of one stat: the base IV and the hyper-trained IV ([NO_HYPER] when it is not hyper trained). */
data class IvState(val base: Int, val hyper: Int) {
    /** Same as Cobblemon's IVs.getEffectiveBattleIV. */
    val effective: Int get() = if (hyper >= 0) hyper else base

    /** Same as Cobblemon's IVs.setHyperTrainedIV: a value equal to the base IV clears the hyper training. */
    fun hyperTrain(value: Int): IvState = if (value == base) copy(hyper = NO_HYPER) else copy(hyper = value)
}

object BottleCaps {
    /** Only Bottle Caps refuses a cap (and keeps the item) when it would change nothing. */
    fun canUse(state: IvState, value: Int, mode: BottleCapMode): Boolean = when (mode) {
        BottleCapMode.BASE_IV -> state.base != value
        BottleCapMode.HYPER_TRAIN -> state.effective != value
        BottleCapMode.BOTH -> state.effective != value || state.base != value
    }

    /** IV state after using a cap that sets the stat to [value] (31 for a Bottle Cap, 0 for a Withered one). */
    fun apply(state: IvState, value: Int, mode: BottleCapMode): IvState = when (mode) {
        BottleCapMode.BASE_IV -> state.copy(base = value)
        BottleCapMode.HYPER_TRAIN -> state.hyperTrain(value)
        BottleCapMode.BOTH -> state.copy(base = value).hyperTrain(value)
    }

    /**
     * IV state after candies move the effective IV from [afterCaps] to [effective].
     * Each candy calls hyperTrainIV(stat, effective IV ± 1), so only the last step matters.
     */
    fun withCandies(afterCaps: IvState, effective: Int): IvState =
        if (effective == afterCaps.effective) afterCaps else afterCaps.hyperTrain(effective)
}
