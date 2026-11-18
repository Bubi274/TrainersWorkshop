package io.github.bubi274.trainersworkshop.client.gui

import io.github.bubi274.trainersworkshop.editor.BottleCaps
import io.github.bubi274.trainersworkshop.editor.CostItem
import io.github.bubi274.trainersworkshop.editor.EDITOR_STATS
import io.github.bubi274.trainersworkshop.editor.InventoryUtil
import io.github.bubi274.trainersworkshop.editor.MoveSource
import io.github.bubi274.trainersworkshop.editor.NO_CAP
import io.github.bubi274.trainersworkshop.editor.PokemonEditData
import io.github.bubi274.trainersworkshop.editor.PokemonEditOptions
import io.github.bubi274.trainersworkshop.editor.PokemonEditing
import io.github.bubi274.trainersworkshop.editor.SurvivalCosts
import io.github.bubi274.trainersworkshop.editor.TmSource
import net.minecraft.item.Item
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/*
 * Tabs of the survival PokeEditor: same layout as the creative one,
 * but every change needs items, and a button whose item is missing shows red text.
 * The number of items you have is shown in the button tooltip and in the Cost area at the bottom of the screen.
 */

/**
 * Survival IV / EV tab, with two pages (IVs and EVs) like the Summary Stats tab.
 *
 * IVs have a base IV and an effective IV, like Cobblemon:
 * - "+"/"-": Cobblemon IV candies (Health Candy... / Sickly Candy...), ±1 effective IV (hyper training).
 * - "Max"/"0": Bottle Cap / Withered Bottle Cap (Only Bottle Caps), sets the stat to 31 / 0. Whether that changes
 *   the base IV, the effective IV or both depends on the server's Only Bottle Caps ([EditorScreen.bottleCapMode]).
 * A button is locked if the item is missing, the cap would change nothing, or the value would leave 0..31.
 */
class SurvivalStatsTab(screen: EditorScreen) : StatPageTab(screen) {
    private val original get() = screen.selectedSlot!!.data
    private val options get() = screen.selectedSlot!!.options
    private val player get() = MinecraftClient.getInstance().player
    private val capMode get() = screen.bottleCapMode

    /** Tries a change: the button is only enabled if it is valid and there are enough items for [field]. */
    private fun allowed(field: String, change: (PokemonEditData) -> PokemonEditData): Boolean {
        val inventory = player?.inventory ?: return false
        val result = SurvivalCosts.compute(original, change(draft), options, screen.levelCap, screen.rules) { InventoryUtil.count(inventory, it) }
        val goldProblem = field.startsWith("iv.") && "iv.gold" in result.problems
        return result.error == null && field !in result.problems && !goldProblem
    }

    /** Button description with the item name (first line) and the number you have (gray line below). */
    private fun tip(key: String, item: Item?): Text {
        val hint = gui(key).string
        return Text.literal(
            when {
                item == null -> "$hint\nItem not installed"
                else -> "${item.name.string}\n$hint\nHave: ${screen.haveCount(item)}"
            },
        )
    }

    /** A button that uses an item: red text if you don't have the item (or its mod isn't installed). */
    private fun itemButton(label: Text, x: Int, y: Int, w: Int, item: Item?, hintKey: String, active: Boolean, onPress: () -> Unit) {
        val button = screen.button(label, x, y, w, 9, tip(hintKey, item), active, onPress)
        if (item == null || screen.haveCount(item) == 0) button.labelColor = GuiKit.RED
    }

    override fun init() {
        initPageButtons()
        val x = cellX
        if (showEvs) initEvs(x) else initIvs(x)
    }

    // ---------- IV ----------

    private fun initIvs(x: Int) {
        val gold = draft.useGoldCap || draft.useGoldWithered
        EDITOR_STATS.forEachIndexed { row, stat ->
            val y = rowY(row) + 1
            val field = "iv.$row"
            val effective = draft.effectiveIv(row)
            val up = SurvivalCosts.candy(stat, up = true)
            val down = SurvivalCosts.candy(stat, up = false)
            // Candies start from the effective IV after the planned caps (the server uses caps first).
            val withoutCandy = SurvivalCosts.afterCaps(original, draft, row, capMode).effective
            val haveUp = up?.let { screen.haveCount(it) } ?: 0
            val haveDown = down?.let { screen.haveCount(it) } ?: 0
            val maxEffective = minOf(SurvivalCosts.MAX_IV, withoutCandy + haveUp)
            val minEffective = maxOf(SurvivalCosts.MIN_IV, withoutCandy - haveDown)
            itemButton(Text.literal("-"), x + 2, y, 9, down, "hint.candy_down",
                effective > SurvivalCosts.MIN_IV && allowed(field) { withEffective(it, row, effective - 1) }) {
                val goal = if (Screen.hasShiftDown()) minEffective else effective - 1
                if (goal < effective) screen.edit { withEffective(it, row, goal) }
            }
            itemButton(Text.literal("+"), x + 39, y, 9, up, "hint.candy_up",
                effective < SurvivalCosts.MAX_IV && allowed(field) { withEffective(it, row, effective + 1) }) {
                val goal = if (Screen.hasShiftDown()) maxEffective else effective + 1
                if (goal > effective) screen.edit { withEffective(it, row, goal) }
            }
            capButton(row, x + 50, y, 17, SurvivalCosts.MAX_IV, "button.max", "hint.bottle_cap", SurvivalCosts.bottleCapId(stat), gold)
            capButton(row, x + 68, y, 9, SurvivalCosts.MIN_IV, "button.zero", "hint.withered_cap", SurvivalCosts.witheredCapId(stat), gold)
        }
        val goldY = zoneY + 1
        goldButton(screen.panelX + 7, goldY, SurvivalCosts.MAX_IV, draft.useGoldCap, "button.gold_cap", SurvivalCosts.GOLD_CAP_ID, "hint.gold_cap", draft.useGoldWithered)
        goldButton(screen.panelX + 7, goldY + 10, SurvivalCosts.MIN_IV, draft.useGoldWithered, "button.gold_withered", SurvivalCosts.GOLD_WITHERED_CAP_ID, "hint.gold_withered", draft.useGoldCap)
    }

    /** "Max" (Bottle Cap) or "0" (Withered Bottle Cap) button for one stat; click again to undo. */
    private fun capButton(row: Int, x: Int, y: Int, w: Int, value: Int, labelKey: String, hintKey: String, itemId: String, gold: Boolean) {
        val planned = draft.caps[row] == value
        // Only Bottle Caps refuses a cap that would change nothing.
        val usable = BottleCaps.canUse(SurvivalCosts.ivState(original, row), value, capMode)
        val active = !gold && usable && (planned || allowed("iv.$row") { withCap(it, row, value) })
        val label = if (planned) gui("button.undo_short") else gui(labelKey)
        itemButton(label, x, y, w, SurvivalCosts.itemById(itemId), hintKey + capMode.hintSuffix, active) {
            screen.edit { withCap(it, row, if (planned) NO_CAP else value) }
        }
    }

    private fun goldButton(x: Int, y: Int, value: Int, on: Boolean, labelKey: String, itemId: String, hintKey: String, otherOn: Boolean) {
        val item = SurvivalCosts.itemById(itemId)
        val have = item?.let { screen.haveCount(it) } ?: 0
        val usable = EDITOR_STATS.indices.any { BottleCaps.canUse(SurvivalCosts.ivState(original, it), value, capMode) }
        val active = !otherOn && usable && (on || allowed("iv.gold") { withGold(it, value) })
        val label = if (on) gui("button.undo") else gui(labelKey, have)
        itemButton(label, x, y, 120, item, hintKey + capMode.hintSuffix, active) {
            screen.edit { if (on) withoutGold(it) else withGold(it, value) }
        }
    }

    /** Current effective IVs of [data] with stat [row] changed to [effective]. */
    private fun effectives(data: PokemonEditData, row: Int = -1, effective: Int = 0): List<Int> =
        EDITOR_STATS.indices.map { if (it == row) effective else data.effectiveIv(it) }

    /** Plans candies so that stat [row] ends at [effective]; the base and hyper IVs follow Cobblemon's rules. */
    private fun withEffective(data: PokemonEditData, row: Int, effective: Int): PokemonEditData =
        SurvivalCosts.planIvs(original, data, effectives(data, row, effective), capMode)

    /** Plans (or removes) the cap on stat [row]; the candies planned for that stat are dropped. */
    private fun withCap(data: PokemonEditData, row: Int, cap: Int): PokemonEditData {
        val capped = data.copy(caps = data.caps.toMutableList().also { it[row] = cap })
        val afterCap = SurvivalCosts.afterCaps(original, capped, row, capMode).effective
        return SurvivalCosts.planIvs(original, capped, effectives(capped, row, afterCap), capMode)
    }

    /** Plans a gold cap; per-stat caps and planned candies are dropped. */
    private fun withGold(data: PokemonEditData, value: Int): PokemonEditData {
        val golden = data.copy(
            caps = List(EDITOR_STATS.size) { NO_CAP },
            useGoldCap = value == SurvivalCosts.MAX_IV,
            useGoldWithered = value == SurvivalCosts.MIN_IV,
        )
        val cappedIvs = EDITOR_STATS.indices.map { SurvivalCosts.afterCaps(original, golden, it, capMode).effective }
        return SurvivalCosts.planIvs(original, golden, cappedIvs, capMode)
    }

    /** Removes the gold cap; planned candies are dropped. */
    private fun withoutGold(data: PokemonEditData): PokemonEditData {
        val plain = data.copy(useGoldCap = false, useGoldWithered = false)
        return SurvivalCosts.planIvs(original, plain, EDITOR_STATS.indices.map { original.effectiveIv(it) }, capMode)
    }

    // ---------- EV ----------

    /** EVs: "-" EV-lowering berry (-10), "+1" feather, "+10" vitamin (feathers fill in for missing vitamins), "Max" raises as far as possible. */
    private fun initEvs(x: Int) {
        EDITOR_STATS.forEachIndexed { row, stat ->
            val y = rowY(row) + 1
            val field = "ev.$row"
            val ev = draft.evs[row]
            val minus = evDown(row)
            val vitamin = SurvivalCosts.vitamin(stat)
            val feather = SurvivalCosts.feather(stat)
            val maxGoal = maxEvGoal(row, vitamin, feather)
            itemButton(Text.literal("-"), x + 1, y, 8, SurvivalCosts.evBerry(stat), "hint.ev_down",
                ev > 0 && allowed(field) { setEv(it, row, minus) }) { screen.edit { setEv(it, row, minus) } }
            itemButton(Text.literal("+1"), x + 33, y, 13, feather, "hint.ev_feather",
                canRaise(row, 1) && allowed(field) { setEv(it, row, ev + 1) }) { screen.edit { setEv(it, row, ev + 1) } }
            itemButton(Text.literal("+10"), x + 47, y, 15, vitamin, "hint.ev_vitamin",
                canRaise(row, 10) && allowed(field) { setEv(it, row, ev + 10) }) { screen.edit { setEv(it, row, ev + 10) } }
            val haveVit = vitamin?.let { screen.haveCount(it) } ?: 0
            val haveFea = feather?.let { screen.haveCount(it) } ?: 0
            val maxBtn = screen.button(
                gui("button.max"),
                x + 63,
                y,
                15,
                9,
                gui("hint.ev_max", haveVit, haveFea),
                active = maxGoal > ev && allowed(field) { setEv(it, row, maxGoal) },
            ) {
                screen.edit { setEv(it, row, maxGoal) }
            }
            if (haveVit == 0 && haveFea == 0 && maxGoal <= original.evs[row]) maxBtn.labelColor = GuiKit.RED
        }
    }

    /** Highest EV reachable for [row] with the vitamins + feathers you have, limited to 252 per stat and 510 in total. */
    private fun maxEvGoal(row: Int, vitamin: Item?, feather: Item?): Int {
        val current = draft.evs[row]
        val base = original.evs[row]
        val otherSum = draft.evs.sum() - current
        val capLimit = minOf(PokemonEditing.MAX_EV, PokemonEditing.MAX_EV_TOTAL - otherSum)
        if (capLimit <= current) return current
        val maxDiff = maxOf(0, capLimit - base)
        val haveVit = vitamin?.let { screen.haveCount(it) } ?: 0
        val haveFea = feather?.let { screen.haveCount(it) } ?: 0
        val useVit = minOf(maxDiff / SurvivalCosts.EV_PER_VITAMIN, haveVit)
        val rem = maxDiff - useVit * SurvivalCosts.EV_PER_VITAMIN
        val bestDiff = useVit * SurvivalCosts.EV_PER_VITAMIN + minOf(rem, haveFea)
        return minOf(capLimit, base + bestDiff)
    }

    /** Above the original value: removes part of the planned raise. At or below the original value: each berry lowers it by 10. */
    private fun evDown(row: Int): Int {
        val current = draft.evs[row]
        val base = original.evs[row]
        return if (current > base) maxOf(base, current - step) else maxOf(0, current - SurvivalCosts.EV_PER_BERRY)
    }

    private fun canRaise(row: Int, amount: Int) =
        draft.evs[row] + amount <= PokemonEditing.MAX_EV && draft.evs.sum() + amount <= PokemonEditing.MAX_EV_TOTAL

    private fun setEv(data: PokemonEditData, row: Int, value: Int) =
        data.copy(evs = data.evs.toMutableList().also { it[row] = value })

    // ---------- Drawing ----------

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        drawStatLabels(context)
        EDITOR_STATS.indices.forEach { row ->
            if (showEvs) {
                val color = if (screen.hasProblem("ev.$row")) GuiKit.RED else changed(draft.evs[row], original.evs[row])
                drawValue(context, row, cellX + 10, 22, draft.evs[row].toString(), color, draft.evs[row], PokemonEditing.MAX_EV)
            } else {
                val problem = screen.hasProblem("iv.$row") || ((draft.useGoldCap || draft.useGoldWithered) && screen.hasProblem("iv.gold"))
                val effective = draft.effectiveIv(row)
                val color = if (problem) GuiKit.RED else changed(effective, original.effectiveIv(row))
                drawValue(context, row, cellX + 12, 26, effective.toString(), color, effective, MAX_IV)
                hoverTooltip(context, mouseX, mouseY, cellX + 12, rowY(row), 26, 11, gui("iv_detail", effective, draft.ivs[row]))
            }
        }
        if (showEvs) drawEvMeter(context, zoneY + 6)
    }

    private fun changed(value: Int, before: Int) = if (value != before) GuiKit.GOLD else GuiKit.WHITE
}

class SurvivalMovesTab(screen: EditorScreen) : MovesTab(screen) {
    private fun isFree(move: String): Boolean {
        val slot = screen.selectedSlot ?: return false
        return move in slot.options.freeMoves || move in slot.data.moves
    }

    /** The TM that would teach [move]: a Cobblemon TM first, then a TMCraft TM (SurvivalCosts.tmFor). */
    private fun tm(move: String): CostItem? {
        val slot = screen.selectedSlot ?: return null
        return SurvivalCosts.tmFor(move, slot.options) { screen.haveCount(it) }
    }

    private fun source(move: String): MoveSource {
        val slot = screen.selectedSlot ?: return MoveSource.NONE
        if (isFree(move)) return MoveSource.FREE
        return SurvivalCosts.moveSource(move, slot.options)
    }

    /** A move not learned yet: can only be picked if an installed TM can teach it, or it is an egg move (Type Gems). */
    override fun isSelectable(move: String): Boolean = source(move) != MoveSource.NONE

    /** "Free" / "TM x2" (you have 2 of the TM that would be used) / "Gem x5" (Type Gems you have) / "No TM". */
    override fun rowTag(move: String): Pair<Text, Int>? {
        if (move.isEmpty()) return null
        return when (source(move)) {
            MoveSource.FREE -> Pair(gui("tag.free"), GuiKit.GREEN)
            MoveSource.TM -> {
                val tm = tm(move) ?: return Pair(gui("tag.no_tm"), GuiKit.RED)
                val have = screen.haveCount(tm)
                Pair(gui("tag.tm", have), if (have > 0) GuiKit.GOLD else GuiKit.RED)
            }
            MoveSource.GEM -> {
                val have = SurvivalCosts.gemFor(move)?.let { screen.haveCount(it) } ?: 0
                Pair(gui("tag.gem", have), if (have >= screen.rules.eggMoveGemCost) GuiKit.GOLD else GuiKit.RED)
            }
            MoveSource.NONE -> Pair(gui("tag.no_tm"), GuiKit.RED)
        }
    }

    /** "Cobblemon TM (have N)", "TMCraft TM (have N)" or "Water Gem x3 (have N)": what Apply will use. */
    override fun moveCostInfo(move: String): Pair<Text, Int> {
        return when (source(move)) {
            MoveSource.FREE -> Pair(gui("tag.free"), GuiKit.GREEN)
            MoveSource.TM -> {
                val tm = tm(move) ?: return Pair(gui("tag.no_tm"), GuiKit.RED)
                val have = screen.haveCount(tm)
                val key = when (SurvivalCosts.tmSource(tm)) {
                    TmSource.COBBLEMON -> if (screen.rules.infiniteTms) "move.needs_tm.cobblemon_kept" else "move.needs_tm.cobblemon"
                    TmSource.TMCRAFT -> "move.needs_tm.tmcraft"
                }
                Pair(gui(key, have), if (have > 0) GuiKit.GOLD else GuiKit.RED)
            }
            MoveSource.GEM -> {
                val gem = SurvivalCosts.gemFor(move) ?: return Pair(gui("tag.no_tm"), GuiKit.RED)
                val have = screen.haveCount(gem)
                val cost = screen.rules.eggMoveGemCost
                Pair(gui("move.needs_gems", gem.name, cost, have), if (have >= cost) GuiKit.GOLD else GuiKit.RED)
            }
            MoveSource.NONE -> Pair(gui("tag.no_tm"), GuiKit.RED)
        }
    }

    override fun pickerNote(): Text = gui("picker_note.survival")
}

class SurvivalNatureTab(screen: EditorScreen) : NatureTab(screen) {
    private val original get() = screen.selectedSlot!!.data

    override fun chosen(): String = draft.effectiveNature

    /** Another nature needs the matching Mint. */
    override fun isSelectable(nature: String): Boolean = nature == original.effectiveNature || mint(nature) != null

    /** "Requires Timid Mint (have 1)", red if you have none. */
    override fun costLine(): Pair<Text, Int>? {
        val nature = chosen()
        if (nature == original.effectiveNature) return null
        val mint = mint(nature) ?: return Pair(gui("nature.no_mint", GuiKit.natureName(nature)), GuiKit.RED)
        val have = screen.haveCount(mint)
        return Pair(gui("nature.requires", mint.name, have), if (have > 0) GuiKit.GOLD else GuiKit.RED)
    }

    override fun choose(nature: String) = screen.edit {
        if (nature == original.effectiveNature) it.copy(mintedNature = original.mintedNature) else it.copy(mintedNature = nature)
    }

    private fun mint(nature: String) = SurvivalCosts.mintFor(nature)
}

class SurvivalOtherTab(screen: EditorScreen) : OtherTab(screen) {
    private val original get() = screen.selectedSlot!!.data
    private val options get() = screen.selectedSlot!!.options
    private val player get() = MinecraftClient.getInstance().player

    /** Shiny can only be turned on (costs 1 Shiny Berry), not off. */
    override fun initShiny(x: Int, y: Int, w: Int) {
        val berry = SurvivalCosts.itemById("trainers_workshop:shiny_berry")
        val have = berry?.let { screen.haveCount(it) } ?: 0
        val tipText = Text.literal("${berry?.name?.string ?: "Shiny Berry"}\n${gui("hint.shiny_berry").string}\nHave: $have")
        shinyButton(x, y, w, tipText, active = !original.shiny) {
            screen.edit { it.copy(shiny = !it.shiny) }
        }
    }

    override val levelEditable: Boolean get() = true

    /**
     * Level row: "+1" (up 1 level) and "Max" (up to min(cap, current level + Rare Candies owned)).
     * Each level costs 1 Rare Candy. Only up, never down. Buttons are locked at the cap or without enough candies.
     */
    override fun initLevel(x: Int, y: Int) {
        val inventory = player?.inventory ?: return
        val rare = SurvivalCosts.rareCandy()
        val haveRare = rare?.let { inventory.count(it) } ?: 0
        val cap = screen.levelCap
        val atCap = draft.level >= cap
        val next = draft.level + 1
        val canNext = !atCap && next - original.level <= haveRare
        val maxLevel = SurvivalCosts.maxAffordableLevel(original, cap) { inventory.count(it) }
        val canMax = !atCap && maxLevel > draft.level

        val tip = if (atCap) gui("hint.level_cap_reached", cap) else gui("hint.level_rare_candy", haveRare)
        screen.button(Text.literal("+1"), x + 2, y, 14, 9, tip, canNext) {
            screen.edit { it.copy(level = next) }
        }.also { if (!canNext && !atCap) it.labelColor = GuiKit.RED }
        screen.button(gui("button.max"), x + 61, y, 17, 9, tip, canMax) {
            screen.edit { it.copy(level = maxLevel) }
        }.also { if (!canMax && !atCap) it.labelColor = GuiKit.RED }
    }

    override fun initAbility(x: Int, y: Int, w: Int, options: PokemonEditOptions) {
        val choices = SurvivalCosts.survivalAbilities(original, options)
        val tooltip = abilityTooltip(options, choices)
        screen.button(abilityLabel(draft.ability), x, y, w, 9, tooltip, active = choices.size > 1) {
            screen.edit { it.copy(ability = cycle(choices, it.ability)) }
        }
    }

    private fun abilityTooltip(options: PokemonEditOptions, choices: List<String>): Text {
        val capsule = SurvivalCosts.abilityCapsule()
        val patch = SurvivalCosts.abilityPatch()
        val capsuleName = capsule?.name ?: Text.literal("Ability Capsule")
        val patchName = patch?.name ?: Text.literal("Ability Patch")
        val haveCapsule = capsule?.let { screen.haveCount(it) } ?: 0
        val havePatch = patch?.let { screen.haveCount(it) } ?: 0

        if (choices.size <= 1) {
            return if (!original.abilityForced && original.ability in options.hiddenAbilities && original.ability !in options.commonAbilities) {
                gui("hint.ability_locked_hidden")
            } else {
                gui("hint.ability_locked_single")
            }
        }
        if (draft.ability != original.ability) {
            return if (draft.ability in options.commonAbilities) {
                gui("hint.ability_requires", capsuleName, haveCapsule)
            } else {
                gui("hint.ability_requires", patchName, havePatch)
            }
        }
        val canCapsule = options.commonAbilities.size > 1
        val canPatch = options.hiddenAbilities.any { it != original.ability }
        return when {
            canCapsule && canPatch -> gui("hint.ability_both", capsuleName, haveCapsule, patchName, havePatch)
            canCapsule -> gui("hint.ability_requires", capsuleName, haveCapsule)
            else -> gui("hint.ability_requires", patchName, havePatch)
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        val font = screen.font
        // "Lv. current → Lv. target" between the two buttons, and "Cap: Lv. X" below.
        val changed = draft.level != original.level
        val levelText = if (changed) gui("level_change", original.level, draft.level) else gui("level", draft.level)
        val levelColor = when {
            screen.hasProblem("level") -> GuiKit.RED
            changed -> GuiKit.GOLD
            else -> GuiKit.WHITE
        }
        GuiKit.textCentered(context, font, levelText, cellX + 38.5f, rowY(3) + 1, levelColor, maxWidth = 43, minScale = 0.45f)
        GuiKit.text(context, font, gui("level_cap", screen.levelCap), screen.panelX + 8, screen.panelY + 80, GuiKit.GRAY, maxWidth = 118)
        if (screen.hasProblem("shiny")) {
            GuiKit.text(context, font, gui("missing_shiny_berry"), screen.panelX + 8, screen.panelY + 114, GuiKit.RED, maxWidth = 118)
        }
    }
}
