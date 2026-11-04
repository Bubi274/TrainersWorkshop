package io.github.bubi274.trainersworkshop.client.gui

import io.github.bubi274.trainersworkshop.editor.EDITOR_STATS
import io.github.bubi274.trainersworkshop.editor.NO_HYPER
import io.github.bubi274.trainersworkshop.editor.PokemonEditData
import io.github.bubi274.trainersworkshop.editor.PokemonEditing
import io.github.bubi274.trainersworkshop.editor.SurvivalCosts
import net.minecraft.item.Item
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

/*
 * Các tab của PokeEditor bản survival: cùng giao diện với bản creative,
 * nhưng mỗi thay đổi cần vật phẩm và ô thiếu vật phẩm hiện màu đỏ.
 */

/**
 * Tab IV / EV bản survival, có hai trang (IV và EV) vì mỗi nút cần hiện số vật phẩm đang có.
 *
 * IV gồm IV gốc và IV có hiệu lực, giống Cobblemon:
 * - "+"/"-": kẹo IV của Cobblemon (Health Candy... / Sickly Candy...), ±1 IV có hiệu lực (hyper train).
 * - "Max"/"0": Bottle Cap / Withered Bottle Cap (Only Bottle Caps), đặt IV gốc thành 31 / 0.
 * Nút bị khoá nếu thiếu vật phẩm hoặc vượt khoảng 0..31.
 */
// TODO(ui): bố cục hai trang IV/EV và vị trí số lượng cạnh nút chưa xem thử trong game.
class SurvivalStatsTab(screen: EditorScreen) : EditorTab(screen) {
    override val title: Text = gui("tab.stats")

    private var showEvs = false
    private val original get() = screen.selectedSlot!!.data
    private val options get() = screen.selectedSlot!!.options
    private val player get() = MinecraftClient.getInstance().player
    private val free get() = player?.abilities?.creativeMode == true

    /** Huy hiệu số lượng vẽ cạnh nút: vị trí, vật phẩm. */
    private val badges = mutableListOf<Triple<Int, Int, Item?>>()

    private fun rowY(row: Int) = screen.contentY + 24 + row * 17

    /** Thử một thay đổi: hợp lệ và đủ vật phẩm cho phần [field] thì mới mở nút. */
    private fun allowed(field: String, change: (PokemonEditData) -> PokemonEditData): Boolean {
        val inventory = player?.inventory ?: return false
        val result = SurvivalCosts.compute(original, change(draft), options, { inventory.count(it) }, free)
        val goldProblem = field.startsWith("iv.") && "iv.gold" in result.problems
        return result.error == null && field !in result.problems && !goldProblem
    }

    override fun init() {
        badges.clear()
        val x = screen.contentX
        val y = screen.contentY
        screen.button(gui("button.view_iv"), x + 68, y, 29, 12, active = showEvs) { showEvs = false; screen.rebuild() }
        screen.button(gui("button.view_ev"), x + 99, y, 29, 12, active = !showEvs) { showEvs = true; screen.rebuild() }
        if (showEvs) initEvs(x) else initIvs(x)
    }

    // ---------- IV ----------

    private fun initIvs(x: Int) {
        val gold = draft.useGoldCap || draft.useGoldWithered
        EDITOR_STATS.forEachIndexed { row, stat ->
            val y = rowY(row)
            val field = "iv.$row"
            val effective = draft.effectiveIv(row)
            val up = SurvivalCosts.candy(stat, up = true)
            val down = SurvivalCosts.candy(stat, up = false)
            ivButton(Text.literal("-"), x + 34, y, 12, down, gui("hint.candy_down"),
                effective > SurvivalCosts.MIN_IV && allowed(field) { withEffective(it, row, effective - 1) }) {
                screen.edit { withEffective(it, row, effective - 1) }
            }
            ivButton(Text.literal("+"), x + 56, y, 12, up, gui("hint.candy_up"),
                effective < SurvivalCosts.MAX_IV && allowed(field) { withEffective(it, row, effective + 1) }) {
                screen.edit { withEffective(it, row, effective + 1) }
            }
            baseButton(row, x + 78, y, 20, SurvivalCosts.MAX_IV, "button.max", "hint.bottle_cap", SurvivalCosts.bottleCapId(stat), gold)
            baseButton(row, x + 104, y, 14, SurvivalCosts.MIN_IV, "button.zero", "hint.withered_cap", SurvivalCosts.witheredCapId(stat), gold)
        }
        val footerY = screen.contentY + 128
        goldButton(x, footerY, SurvivalCosts.MAX_IV, draft.useGoldCap, "button.gold_cap", SurvivalCosts.GOLD_CAP_ID, "hint.gold_cap", draft.useGoldWithered)
        goldButton(x + 66, footerY, SurvivalCosts.MIN_IV, draft.useGoldWithered, "button.gold_withered", SurvivalCosts.GOLD_WITHERED_CAP_ID, "hint.gold_withered", draft.useGoldCap)
    }

    private fun ivButton(label: Text, x: Int, y: Int, w: Int, item: Item?, hint: Text, active: Boolean, onPress: () -> Unit) {
        screen.button(label, x, y, w, 12, hint, active) { onPress() }
        badges += Triple(x + w + 1, y + 4, item)
    }

    /** Nút "Max" (Bottle Cap) hoặc "0" (Withered Bottle Cap) cho IV gốc; bấm lần nữa để bỏ. */
    private fun baseButton(row: Int, x: Int, y: Int, w: Int, value: Int, labelKey: String, hintKey: String, itemId: String, gold: Boolean) {
        val planned = draft.ivs[row] == value && original.ivs[row] != value
        // Only Bottle Caps không cho dùng nắp khi IV gốc đã đúng giá trị đó.
        val active = !gold && original.ivs[row] != value && (planned || allowed("iv.$row") { withBase(it, row, value) })
        val label = if (planned) gui("button.undo_short") else gui(labelKey)
        screen.button(label, x, y, w, 12, gui(hintKey), active) {
            screen.edit { withBase(it, row, if (planned) original.ivs[row] else value) }
        }
        badges += Triple(x + w + 1, y + 4, SurvivalCosts.itemById(itemId))
    }

    private fun goldButton(x: Int, y: Int, value: Int, on: Boolean, labelKey: String, itemId: String, hintKey: String, otherOn: Boolean) {
        val item = SurvivalCosts.itemById(itemId)
        val have = item?.let { screen.haveCount(it) } ?: 0
        val active = !otherOn && original.ivs.any { it != value } && (on || allowed("iv.gold") { withGold(it, value) })
        val label = if (on) gui("button.undo") else gui(labelKey, have)
        screen.button(label, x, y, 62, 12, gui(hintKey), active) {
            screen.edit { if (on) withoutGold(it) else withGold(it, value) }
        }
    }

    /**
     * Ghi IV hyper cho đúng cách Cobblemon lưu: hyper bằng IV gốc thì không lưu (NO_HYPER),
     * và nếu không cần kẹo nào thì giữ nguyên giá trị cũ.
     */
    private fun resolveHyper(row: Int, base: Int, effective: Int): Int {
        val originalHyper = original.hyperIvs[row]
        val withoutCandy = if (originalHyper >= 0) originalHyper else base
        return when (effective) {
            withoutCandy -> originalHyper
            base -> NO_HYPER
            else -> effective
        }
    }

    private fun withEffective(data: PokemonEditData, row: Int, effective: Int): PokemonEditData =
        data.copy(hyperIvs = data.hyperIvs.toMutableList().also { it[row] = resolveHyper(row, data.ivs[row], effective) })

    /** Đổi IV gốc; IV có hiệu lực đi theo IV gốc nếu chưa hyper train. */
    private fun withBase(data: PokemonEditData, row: Int, base: Int): PokemonEditData {
        val effective = data.hyperIvs[row].takeIf { it >= 0 } ?: base
        val ivs = data.ivs.toMutableList().also { it[row] = base }
        return data.copy(ivs = ivs, hyperIvs = data.hyperIvs.toMutableList().also { it[row] = resolveHyper(row, base, effective) })
    }

    private fun withGold(data: PokemonEditData, value: Int): PokemonEditData {
        var result = data
        EDITOR_STATS.indices.forEach { result = withBase(result, it, value) }
        return result.copy(useGoldCap = value == SurvivalCosts.MAX_IV, useGoldWithered = value == SurvivalCosts.MIN_IV)
    }

    private fun withoutGold(data: PokemonEditData): PokemonEditData {
        var result = data
        EDITOR_STATS.indices.forEach { result = withBase(result, it, original.ivs[it]) }
        return result.copy(useGoldCap = false, useGoldWithered = false)
    }

    // ---------- EV ----------

    /** EV: "-" berry giảm EV (-10), "+1" feather, "+10" vitamin (thiếu vitamin thì bù bằng feather). */
    private fun initEvs(x: Int) {
        EDITOR_STATS.forEachIndexed { row, stat ->
            val y = rowY(row)
            val field = "ev.$row"
            val ev = draft.evs[row]
            val minus = evDown(row)
            ivButton(Text.literal("-"), x + 48, y, 12, SurvivalCosts.evBerry(stat), gui("hint.ev_down"),
                ev > 0 && allowed(field) { setEv(it, row, minus) }) { screen.edit { setEv(it, row, minus) } }
            ivButton(Text.literal("+1"), x + 72, y, 16, SurvivalCosts.feather(stat), gui("hint.ev_feather"),
                canRaise(row, 1) && allowed(field) { setEv(it, row, ev + 1) }) { screen.edit { setEv(it, row, ev + 1) } }
            ivButton(Text.literal("+10"), x + 98, y, 20, SurvivalCosts.vitamin(stat), gui("hint.ev_vitamin"),
                canRaise(row, 10) && allowed(field) { setEv(it, row, ev + 10) }) { screen.edit { setEv(it, row, ev + 10) } }
        }
    }

    /** Trên mức gốc: bỏ bớt phần định tăng. Từ mức gốc trở xuống: mỗi berry giảm 10. */
    private fun evDown(row: Int): Int {
        val current = draft.evs[row]
        val base = original.evs[row]
        return if (current > base) maxOf(base, current - step) else maxOf(0, current - SurvivalCosts.EV_PER_BERRY)
    }

    private fun canRaise(row: Int, amount: Int) =
        draft.evs[row] + amount <= PokemonEditing.MAX_EV && draft.evs.sum() + amount <= PokemonEditing.MAX_EV_TOTAL

    private fun setEv(data: PokemonEditData, row: Int, value: Int) =
        data.copy(evs = data.evs.toMutableList().also { it[row] = value })

    // ---------- Vẽ ----------

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val x = screen.contentX
        val font = screen.font
        val cost = screen.survivalCost()
        GuiKit.text(context, font, gui(if (showEvs) "header.ev" else "header.iv"), x, screen.contentY + 14, GuiKit.GRAY, 0.5f)
        EDITOR_STATS.forEachIndexed { row, stat ->
            val y = rowY(row)
            GuiKit.text(context, font, Text.literal(GuiKit.shortStat(stat)), x, y + 3, scale = 0.75f)
            if (showEvs) {
                val color = if (screen.hasProblem("ev.$row")) GuiKit.RED else changed(draft.evs[row], original.evs[row])
                GuiKit.centeredText(context, font, Text.literal(draft.evs[row].toString()), x + 34, y + 3, color, 0.75f)
            } else {
                val problem = screen.hasProblem("iv.$row") || ((draft.useGoldCap || draft.useGoldWithered) && screen.hasProblem("iv.gold"))
                val effective = draft.effectiveIv(row)
                val color = if (problem) GuiKit.RED else changed(effective, original.effectiveIv(row))
                GuiKit.centeredText(context, font, Text.literal(effective.toString()), x + 24, y + 1, color, 0.75f)
                if (draft.hyperIvs[row] >= 0 || draft.ivs[row] != original.ivs[row]) {
                    GuiKit.centeredText(context, font, gui("base_iv", draft.ivs[row]), x + 24, y + 8, changed(draft.ivs[row], original.ivs[row]), 0.5f)
                }
            }
        }
        // Số vật phẩm đang có cạnh mỗi nút: đỏ nếu ít hơn số sẽ dùng.
        badges.forEach { (bx, by, item) ->
            val have = item?.let { screen.haveCount(it) } ?: 0
            val need = item?.let { cost?.needed?.get(it) } ?: 0
            val color = when {
                item == null -> GuiKit.RED
                !free && have < need -> GuiKit.RED
                have == 0 && !free -> GuiKit.RED
                else -> GuiKit.GRAY
            }
            val label = if (item == null) "-" else have.toString()
            GuiKit.text(context, font, Text.literal(label), bx, by, color, 0.5f)
        }
        if (showEvs) {
            GuiKit.text(context, font, gui("ev_total", draft.evs.sum(), PokemonEditing.MAX_EV_TOTAL), x, screen.contentY + 128, GuiKit.GRAY, 0.5f)
        }
    }

    private fun changed(value: Int, before: Int) = if (value != before) GuiKit.GOLD else GuiKit.WHITE
}

class SurvivalMovesTab(screen: EditorScreen) : MovesTab(screen) {
    private fun isFree(move: String): Boolean {
        val slot = screen.selectedSlot ?: return false
        return move in slot.options.freeMoves || move in slot.data.moves
    }

    private fun tmItem(move: String) = SurvivalCosts.itemById(SurvivalCosts.tmId(move))

    /** Chiêu chưa học: chỉ chọn được nếu TM tồn tại (TMCraft) và chiêu dạy được bằng TM. */
    override fun isSelectable(move: String): Boolean {
        if (isFree(move)) return true
        val slot = screen.selectedSlot ?: return false
        return move in slot.options.tmMoves && tmItem(move) != null
    }

    override fun rowTag(move: String): Pair<Text, Int>? {
        if (move.isEmpty()) return null
        if (isFree(move)) return Pair(gui("tag.free"), GuiKit.GREEN)
        val slot = screen.selectedSlot ?: return null
        val tm = tmItem(move)
        if (move !in slot.options.tmMoves || tm == null) return Pair(gui("tag.no_tm"), GuiKit.RED)
        return Pair(gui("tag.tm"), if (screen.haveCount(tm) > 0) GuiKit.GOLD else GuiKit.RED)
    }
}

class SurvivalNatureTab(screen: EditorScreen) : NatureTab(screen) {
    private val original get() = screen.selectedSlot!!.data

    override fun chosen(): String = draft.effectiveNature

    /** Nature khác cần Mint đúng loại. */
    override fun isSelectable(nature: String): Boolean = nature == original.effectiveNature || mint(nature) != null

    override fun rowTag(nature: String): Pair<Text, Int>? {
        if (nature == original.effectiveNature) return null
        val mint = mint(nature) ?: return Pair(gui("tag.no_mint"), GuiKit.RED)
        return Pair(gui("tag.mint"), if (screen.haveCount(mint) > 0) GuiKit.GOLD else GuiKit.RED)
    }

    override fun choose(nature: String) = screen.edit {
        if (nature == original.effectiveNature) it.copy(mintedNature = original.mintedNature) else it.copy(mintedNature = nature)
    }

    private fun mint(nature: String) = SurvivalCosts.mintFor(nature)
}

class SurvivalOtherTab(screen: EditorScreen) : OtherTab(screen) {
    private val original get() = screen.selectedSlot!!.data

    /** Chỉ được bật shiny (tốn 1 Shiny Berry), không tắt được. */
    override fun initShiny(x: Int, y: Int) {
        screen.button(yesNo(draft.shiny), x, y, 70, 14, gui("hint.shiny_berry"), active = !original.shiny) {
            screen.edit { it.copy(shiny = !it.shiny) }
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        if (screen.hasProblem("shiny")) {
            GuiKit.text(context, screen.font, gui("missing_shiny_berry"), screen.contentX, rowY(0) + 16, GuiKit.RED, 0.5f)
        }
    }
}
