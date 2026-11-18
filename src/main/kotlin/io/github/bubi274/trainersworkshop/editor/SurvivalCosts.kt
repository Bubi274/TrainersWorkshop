package io.github.bubi274.trainersworkshop.editor

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.item.berry.FriendshipRaisingBerryItem
import com.cobblemon.mod.common.item.interactive.EVIncreaseItem
import com.cobblemon.mod.common.item.interactive.HyperTrainingItem
import com.cobblemon.mod.common.item.interactive.MintItem
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.registry.ModItems
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.min

/**
 * Works out the items needed by the survival PokeEditor.
 * Shared by the client (red boxes, locked Apply button) and the server (checking and taking items),
 * so both sides always calculate the same thing.
 */
object SurvivalCosts {
    const val MAX_IV = 31
    const val EV_PER_VITAMIN = 10
    const val EV_PER_FEATHER = 1
    /**
     * EV-lowering berries (Pomeg, Kelpsy...) lower EVs by 10 each.
     * TODO(api): Cobblemon reads the amount from the berry mechanics config (evLowerAmount); here it is fixed at 10 as requested.
     */
    const val EV_PER_BERRY = 10

    const val RARE_CANDY_ID = "cobblemon:rare_candy"

    /** Items that raise the level (Rare Candy only); used for tooltips and the inventory hash. */
    fun levelCandies(): List<Item> = listOfNotNull(rareCandy())

    fun rareCandy(): Item? = itemById(RARE_CANDY_ID)

    /** Highest level the player may reach: the lower of the RCT level cap (null = no RCT) and Cobblemon's max level. */
    fun levelLimit(cobblemonMax: Int, rctCap: Int?): Int = if (rctCap == null) cobblemonMax else minOf(cobblemonMax, rctCap)

    /**
     * One cost line, linked to the part being edited (so the right spot turns red).
     * [cost] null = the item's mod isn't installed ([missingItemId] says which item).
     * [consumed] false = the item must be in the inventory but isn't used up (Cobblemon TMs with infiniteTmUses).
     */
    data class Line(
        val field: String,
        val cost: CostItem?,
        val count: Int,
        val missingItemId: String? = null,
        val consumed: Boolean = true,
    ) {
        constructor(field: String, item: Item?, count: Int, missingItemId: String? = null) :
            this(field, item?.let { CostItem(it) }, count, missingItemId, true)

        val item: Item? get() = cost?.item
    }

    data class Result(
        val lines: List<Line>,
        /** Total of each item that must be in the inventory. */
        val needed: Map<CostItem, Int>,
        /** Total of each item that Apply takes (the same as [needed] except for TMs that aren't used up). */
        val consumed: Map<CostItem, Int>,
        /** Edited parts with missing items or invalid values (for example "iv.0", "move.2", "nature"). */
        val problems: Set<String>,
        /** Error key that blocks Apply completely (a change that isn't allowed in survival). */
        val error: String?,
    ) {
        val ok: Boolean get() = error == null && problems.isEmpty()
        val isEmpty: Boolean get() = lines.isEmpty()
    }

    private val CAP_SUFFIX: Map<Stat, String> = mapOf(
        Stats.HP to "hp",
        Stats.ATTACK to "attack",
        Stats.DEFENCE to "defence",
        Stats.SPECIAL_ATTACK to "special_attack",
        Stats.SPECIAL_DEFENCE to "special_defence",
        Stats.SPEED to "speed",
    )

    // Item lists read from the registry (on first use), so Cobblemon's vitamins/feathers/berries/mints are found automatically.
    private val vitamins by lazy { evItems(EV_PER_VITAMIN) }
    private val feathers by lazy { evItems(EV_PER_FEATHER) }
    private val evBerries: Map<Stat, Item> by lazy {
        Registries.ITEM.filterIsInstance<FriendshipRaisingBerryItem>().associateBy { it.stat }
    }
    /**
     * Cobblemon's 12 IV candies (Health/Mighty/... Candy raise, Sickly/Weak/... Candy lower).
     * In Cobblemon they are HyperTrainingItems with ivIncreaseAmount = +1 or -1 and one stat.
     */
    private val candiesUp: Map<Stat, Item> by lazy { candies(1) }
    private val candiesDown: Map<Stat, Item> by lazy { candies(-1) }

    // TODO(build): check in game that all 12 candies are found (Cobblemon 1.8.4 still creates them with hyperTrainingItem(id, ±1, setOf(stat), 0..31)).
    private fun candies(amount: Int): Map<Stat, Item> =
        Registries.ITEM.filterIsInstance<HyperTrainingItem>()
            .filter { it.ivIncreaseAmount == amount && it.targetStats.size == 1 }
            .associateBy { it.targetStats.first() }

    fun candy(stat: Stat, up: Boolean): Item? = if (up) candiesUp[stat] else candiesDown[stat]

    private val mints: Map<String, Item> by lazy {
        Registries.ITEM.filterIsInstance<MintItem>().associateBy { it.nature.name.toString() }
    }

    // EVIncreaseItem is an interface since Cobblemon 1.8 (VitaminItem, FeatherItem), so keep the Item and read the stat from it.
    private fun evItems(amount: Int): Map<Stat, Item> =
        Registries.ITEM.mapNotNull { item ->
            val ev = item as? EVIncreaseItem ?: return@mapNotNull null
            if (ev.evIncreaseAmount == amount) ev.stat to item else null
        }.toMap()

    fun itemById(id: String): Item? {
        val parsed = Identifier.tryParse(id) ?: return null
        return Registries.ITEM.getOrEmpty(parsed).orElse(null)
    }

    fun mintFor(nature: String): Item? = mints[nature]
    fun vitamin(stat: Stat): Item? = vitamins[stat]
    fun feather(stat: Stat): Item? = feathers[stat]
    fun evBerry(stat: Stat): Item? = evBerries[stat]

    // Only Bottle Caps item IDs (checked in the obc 1.3.0 and 1.5.0 jars).
    fun bottleCapId(stat: Stat): String = "obc:bottle_cap_${CAP_SUFFIX[stat]}"
    fun witheredCapId(stat: Stat): String = "obc:bottle_cap_${CAP_SUFFIX[stat]}_withered"
    const val GOLD_CAP_ID = "obc:bottle_cap_gold"
    const val GOLD_WITHERED_CAP_ID = "obc:bottle_cap_gold_withered"
    const val MIN_IV = 0
    fun tmId(move: String): String = "tmcraft:tm_$move"
    const val ABILITY_CAPSULE_ID = "cobblemon:ability_capsule"
    const val ABILITY_PATCH_ID = "cobblemon:ability_patch"
    fun abilityCapsule(): Item? = itemById(ABILITY_CAPSULE_ID)
    fun abilityPatch(): Item? = itemById(ABILITY_PATCH_ID)

    /** Valid abilities that can be picked in Survival mode (starting with the current ability). */
    fun survivalAbilities(current: PokemonEditData, options: PokemonEditOptions): List<String> {
        if (current.abilityForced || current.ability !in options.commonAbilities) {
            return listOf(current.ability)
        }
        val otherCommons = if (options.commonAbilities.size > 1) {
            options.commonAbilities.filter { it != current.ability }
        } else {
            emptyList()
        }
        val hiddens = options.hiddenAbilities.filter { it != current.ability }
        return (listOf(current.ability) + otherCommons + hiddens).distinct()
    }

    /**
     * Works out a hash of the counts of the related items in the inventory, to notice changes.
     */
    fun inventoryHash(options: PokemonEditOptions?, have: (CostItem) -> Int): Int {
        var h = 1
        fun add(item: Item?) {
            h = 31 * h + (if (item != null) have(CostItem(item)) else -1)
        }
        EDITOR_STATS.forEach { stat ->
            add(vitamin(stat))
            add(feather(stat))
            add(evBerry(stat))
            add(candy(stat, true))
            add(candy(stat, false))
            add(itemById(bottleCapId(stat)))
            add(itemById(witheredCapId(stat)))
        }
        add(itemById(GOLD_CAP_ID))
        add(itemById(GOLD_WITHERED_CAP_ID))
        add(ModItems.SHINY_BERRY)
        add(abilityCapsule())
        add(abilityPatch())
        levelCandies().forEach { add(it) }
        mints.values.forEach { add(it) }
        options?.tmMoves?.forEach { move -> add(tmcraftTm(move)) }
        options?.cobblemonTmMoves?.forEach { move -> h = 31 * h + have(cobblemonTm(move)) }
        return h
    }

    /**
     * @param levelCap highest level allowed (see [levelLimit]).
     * @param rules server settings: what a Bottle Cap does, and whether Cobblemon TMs are used up (sent by the server).
     * @param have how many of an item the player has.
     */
    fun compute(
        current: PokemonEditData,
        target: PokemonEditData,
        options: PokemonEditOptions,
        levelCap: Int,
        rules: SurvivalRules,
        have: (CostItem) -> Int,
    ): Result {
        val haveItem: (Item) -> Int = { have(CostItem(it)) }
        val lines = mutableListOf<Line>()
        var error: String? = null

        // Parts that survival can't change.
        if (target.uuid != current.uuid || target.species != current.species) error = "pokemon_changed"
        if (target.gender != current.gender || target.ball != current.ball || target.nature != current.nature) error = "survival_locked"

        addLevelCost(current, target, levelCap, lines)?.let { error = it }

        addIvCosts(current, target, rules.bottleCapMode, lines)?.let { error = it }
        addEvCosts(current, target, lines, haveItem)?.let { error = it }
        addNatureCost(current, target, lines)?.let { error = it }
        addAbilityCost(current, target, options, lines)?.let { error = it }
        addMoveCosts(current, target, options, rules, have, lines)?.let { error = it }
        if (target.shiny != current.shiny) {
            if (!target.shiny) error = "survival_unshiny" else lines += Line("shiny", ModItems.SHINY_BERRY, 1)
        }

        val needed = LinkedHashMap<CostItem, Int>()
        val consumed = LinkedHashMap<CostItem, Int>()
        lines.forEach { line ->
            val cost = line.cost ?: return@forEach
            needed.merge(cost, line.count, Int::plus)
            if (line.consumed) consumed.merge(cost, line.count, Int::plus)
        }

        val problems = mutableSetOf<String>()
        lines.filter { it.cost == null }.forEach { problems += it.field }
        // Missing items: mark every part that uses that item (always checked, even in Creative).
        val short = needed.filter { (cost, count) -> have(cost) < count }.keys
        lines.filter { it.cost in short }.forEach { problems += it.field }
        return Result(lines, needed, consumed, problems, error)
    }

    /**
     * The level can only go up, at most to [levelCap]; each level costs 1 Rare Candy ([LevelRules]).
     * Returns the error key "level_decrease" or "level_over_cap"; missing candies are reported through the cost line (red like the other parts).
     */
    private fun addLevelCost(current: PokemonEditData, target: PokemonEditData, levelCap: Int, lines: MutableList<Line>): String? {
        LevelRules.error(current.level, target.level, levelCap)?.let { return it }
        val count = LevelRules.cost(current.level, target.level)
        if (count > 0) lines += Line("level", rareCandy(), count, RARE_CANDY_ID)
        return null
    }

    /** Highest level reachable: min(level cap, current level + Rare Candies owned). */
    fun maxAffordableLevel(current: PokemonEditData, levelCap: Int, have: (Item) -> Int): Int =
        LevelRules.maxLevel(current.level, levelCap, rareCandy()?.let(have) ?: 0)

    /**
     * IVs have two parts, like Cobblemon:
     * - Bottle Cap / Withered Bottle Cap (Only Bottle Caps): sets the stat to 31 / 0. What exactly changes depends on
     *   [BottleCapMode]: the base IV (OBC 1.3), the hyper-trained IV (OBC 1.5 default), or both (OBC 1.5 breedingBuff).
     *   Gold / Gold Withered do every stat the cap would change. OBC refuses a cap that changes nothing.
     * - IV candies (Cobblemon): each candy hyper trains the effective IV by ±1, within 0..31.
     * The server uses the caps first and the candies after, so the candy count starts from the effective IV after the caps.
     * The base and hyper IVs in [target] must be exactly what those steps produce ([planIvs]).
     */
    private fun addIvCosts(current: PokemonEditData, target: PokemonEditData, capMode: BottleCapMode, lines: MutableList<Line>): String? {
        if (target.useGoldCap && target.useGoldWithered) return "survival_iv"
        val gold = goldCapValue(target)
        if (gold != null) {
            if (target.caps.any { it != NO_CAP }) return "survival_iv"
            if (EDITOR_STATS.indices.none { BottleCaps.canUse(ivState(current, it), gold, capMode) }) return "survival_iv"
            val id = if (gold == MAX_IV) GOLD_CAP_ID else GOLD_WITHERED_CAP_ID
            lines += Line("iv.gold", itemById(id), 1, id)
        } else {
            EDITOR_STATS.forEachIndexed { i, stat ->
                val cap = target.caps[i]
                if (cap == NO_CAP) return@forEachIndexed
                val id = when (cap) {
                    MAX_IV -> bottleCapId(stat)
                    MIN_IV -> witheredCapId(stat)
                    else -> return "survival_iv"
                }
                if (!BottleCaps.canUse(ivState(current, i), cap, capMode)) return "survival_iv"
                lines += Line("iv.$i", itemById(id), 1, id)
            }
        }

        EDITOR_STATS.forEachIndexed { i, stat ->
            val capped = afterCaps(current, target, i, capMode)
            val to = target.effectiveIv(i)
            if (to !in MIN_IV..MAX_IV) return "survival_iv"
            if (BottleCaps.withCandies(capped, to) != ivState(target, i)) return "survival_iv"
            val from = capped.effective
            if (to != from) lines += Line("iv.$i", candy(stat, to > from), kotlin.math.abs(to - from))
        }
        return null
    }

    // ---------- IV plan (Bottle Caps first, then candies) ----------

    fun ivState(data: PokemonEditData, index: Int): IvState = IvState(data.ivs[index], data.hyperIvs[index])

    /** 31 for a Gold Bottle Cap, 0 for a Gold Withered Bottle Cap, null if [target] uses neither. */
    fun goldCapValue(target: PokemonEditData): Int? = when {
        target.useGoldCap -> MAX_IV
        target.useGoldWithered -> MIN_IV
        else -> null
    }

    /**
     * The cap value used on stat [index]: the stat's own cap, or the gold cap on every stat
     * where Only Bottle Caps would change something. null if no cap is used on it.
     */
    fun capValue(current: PokemonEditData, target: PokemonEditData, index: Int, capMode: BottleCapMode): Int? {
        val gold = goldCapValue(target)
        if (gold != null) return gold.takeIf { BottleCaps.canUse(ivState(current, index), it, capMode) }
        return target.caps[index].takeIf { it != NO_CAP }
    }

    /** IV state of stat [index] after the caps planned in [target] are used on [current]. */
    fun afterCaps(current: PokemonEditData, target: PokemonEditData, index: Int, capMode: BottleCapMode): IvState {
        val state = ivState(current, index)
        val cap = capValue(current, target, index, capMode) ?: return state
        return BottleCaps.apply(state, cap, capMode)
    }

    /** Rebuilds the base and hyper IVs of [target] from its caps and the wanted effective IVs ([effectives]). */
    fun planIvs(current: PokemonEditData, target: PokemonEditData, effectives: List<Int>, capMode: BottleCapMode): PokemonEditData {
        val states = EDITOR_STATS.indices.map { BottleCaps.withCandies(afterCaps(current, target, it, capMode), effectives[it]) }
        return target.copy(ivs = states.map { it.base }, hyperIvs = states.map { it.hyper })
    }

    private fun addEvCosts(current: PokemonEditData, target: PokemonEditData, lines: MutableList<Line>, have: (Item) -> Int): String? {
        if (target.evs.any { it !in 0..PokemonEditing.MAX_EV } || target.evs.sum() > PokemonEditing.MAX_EV_TOTAL) return "invalid_evs"
        EDITOR_STATS.forEachIndexed { i, stat ->
            val diff = target.evs[i] - current.evs[i]
            when {
                diff > 0 -> {
                    val vitamin = vitamins[stat]
                    val feather = feathers[stat]
                    // Vitamins (+10) first; the remainder and any missing vitamins are made up with feathers (+1).
                    var useVitamins = diff / EV_PER_VITAMIN
                    var useFeathers = diff % EV_PER_VITAMIN
                    if (vitamin != null) {
                        val available = min(useVitamins, have(vitamin))
                        useFeathers += (useVitamins - available) * EV_PER_VITAMIN
                        useVitamins = available
                    } else {
                        useFeathers += useVitamins * EV_PER_VITAMIN
                        useVitamins = 0
                    }
                    if (useVitamins > 0) lines += Line("ev.$i", vitamin, useVitamins)
                    if (useFeathers > 0) lines += Line("ev.$i", feather, useFeathers)
                }
                diff < 0 -> {
                    val decrease = -diff
                    // Each berry lowers by exactly 10 (not below 0).
                    if (decrease % EV_PER_BERRY != 0 && target.evs[i] != 0) return "survival_ev_step"
                    val berries = (decrease + EV_PER_BERRY - 1) / EV_PER_BERRY
                    lines += Line("ev.$i", evBerries[stat], berries)
                }
            }
        }
        return null
    }

    private fun addNatureCost(current: PokemonEditData, target: PokemonEditData, lines: MutableList<Line>): String? {
        if (target.effectiveNature == current.effectiveNature) {
            return if (target.mintedNature != current.mintedNature) "survival_locked" else null
        }
        if (target.mintedNature.isEmpty()) return "survival_locked"
        lines += Line("nature", mints[target.mintedNature], 1)
        return null
    }

    private fun addAbilityCost(current: PokemonEditData, target: PokemonEditData, options: PokemonEditOptions, lines: MutableList<Line>): String? {
        if (target.ability == current.ability) return null
        if (current.abilityForced || current.ability !in options.commonAbilities) return "survival_ability"
        return when {
            target.ability in options.commonAbilities && options.commonAbilities.size > 1 -> {
                lines += Line("ability", abilityCapsule(), 1, ABILITY_CAPSULE_ID)
                null
            }
            target.ability in options.hiddenAbilities -> {
                lines += Line("ability", abilityPatch(), 1, ABILITY_PATCH_ID)
                null
            }
            else -> "survival_ability"
        }
    }

    /** Each new move needs one TM: a Cobblemon TM or a TMCraft TM ([tmFor]). */
    private fun addMoveCosts(
        current: PokemonEditData,
        target: PokemonEditData,
        options: PokemonEditOptions,
        rules: SurvivalRules,
        have: (CostItem) -> Int,
        lines: MutableList<Line>,
    ): String? {
        target.moves.forEachIndexed { slot, move ->
            if (move.isEmpty() || move == current.moves[slot] || move in options.freeMoves || move in current.moves) return@forEachIndexed
            val tm = tmFor(move, options, have) ?: return "survival_move"
            lines += Line("move.$slot", tm, 1, consumed = TmRules.consumes(tmSource(tm), rules.infiniteTms))
        }
        return null
    }

    // ---------- TMs (Cobblemon 1.8 and TMCraft) ----------

    /** The TMCraft TM item for [move], or null if TMCraft isn't installed (or has no TM for it). */
    fun tmcraftTm(move: String): Item? = itemById(tmId(move))

    /** A Cobblemon TM holding [move] (the cobblemon:tm_move component, see TMMoveComponent). */
    fun cobblemonTm(move: String): CostItem = CostItem(CobblemonItems.TECHNICAL_MACHINE, move)

    fun tmSource(cost: CostItem): TmSource = if (cost.tmMove != null) TmSource.COBBLEMON else TmSource.TMCRAFT

    /** Whether some installed TM can teach [move] to this Pokémon. */
    fun canTeachByTm(move: String, options: PokemonEditOptions): Boolean =
        move in options.cobblemonTmMoves || (move in options.tmMoves && tmcraftTm(move) != null)

    /** The TM used to teach [move] ([TmRules.choose]: a Cobblemon TM first), or null if no installed TM can teach it. */
    fun tmFor(move: String, options: PokemonEditOptions, have: (CostItem) -> Int): CostItem? {
        val cobblemon = cobblemonTm(move).takeIf { move in options.cobblemonTmMoves }
        val tmcraft = tmcraftTm(move)?.takeIf { move in options.tmMoves }?.let { CostItem(it) }
        return when (TmRules.choose(cobblemon != null, tmcraft != null, cobblemon?.let(have) ?: 0, tmcraft?.let(have) ?: 0)) {
            TmSource.COBBLEMON -> cobblemon
            TmSource.TMCRAFT -> tmcraft
            null -> null
        }
    }

    /** Name for the cost list and tooltips, saying which TM is used ("Cobblemon TM: Thunderbolt", "TMCraft TM-24: Thunderbolt"). */
    fun costName(cost: CostItem): Text {
        val move = cost.tmMove
        if (move != null) {
            val moveName = Moves.getByName(move)?.displayName ?: Text.literal(move)
            return Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.cobblemon_tm", moveName)
        }
        if (Registries.ITEM.getId(cost.item).namespace == "tmcraft") {
            return Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.tmcraft_tm", cost.item.name)
        }
        return cost.item.name
    }
}
