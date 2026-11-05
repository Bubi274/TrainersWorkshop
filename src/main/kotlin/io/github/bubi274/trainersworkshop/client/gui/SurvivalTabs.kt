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
 * nhưng mỗi thay đổi cần vật phẩm và nút thiếu vật phẩm hiện chữ đỏ.
 * Số vật phẩm đang có hiện trong tooltip của nút và trong phần Cost ở chân màn hình.
 */

/**
 * Tab IV / EV bản survival, có hai trang (IV và EV) như Stats của Summary.
 *
 * IV gồm IV gốc và IV có hiệu lực, giống Cobblemon:
 * - "+"/"-": kẹo IV của Cobblemon (Health Candy... / Sickly Candy...), ±1 IV có hiệu lực (hyper train).
 * - "Max"/"0": Bottle Cap / Withered Bottle Cap (Only Bottle Caps), đặt IV gốc thành 31 / 0.
 * Nút bị khoá nếu thiếu vật phẩm hoặc vượt khoảng 0..31.
 */
class SurvivalStatsTab(screen: EditorScreen) : StatPageTab(screen) {
    private val original get() = screen.selectedSlot!!.data
    private val options get() = screen.selectedSlot!!.options
    private val player get() = MinecraftClient.getInstance().player

    /** Thử một thay đổi: hợp lệ và đủ vật phẩm cho phần [field] thì mới mở nút. */
    private fun allowed(field: String, change: (PokemonEditData) -> PokemonEditData): Boolean {
        val inventory = player?.inventory ?: return false
        val result = SurvivalCosts.compute(original, change(draft), options) { inventory.count(it) }
        val goldProblem = field.startsWith("iv.") && "iv.gold" in result.problems
        return result.error == null && field !in result.problems && !goldProblem
    }

    /** Mô tả nút kèm số vật phẩm đang có. */
    private fun tip(key: String, item: Item?): Text {
        val hint = gui(key).string
        return Text.literal(
            when {
                item == null -> "$hint (item not installed)"
                else -> "$hint Have: ${screen.haveCount(item)}."
            },
        )
    }

    /** Nút dùng vật phẩm: chữ đỏ nếu không có vật phẩm (hoặc mod chứa nó chưa cài). */
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
            itemButton(Text.literal("-"), x + 2, y, 9, down, "hint.candy_down",
                effective > SurvivalCosts.MIN_IV && allowed(field) { withEffective(it, row, effective - 1) }) {
                screen.edit { withEffective(it, row, effective - 1) }
            }
            itemButton(Text.literal("+"), x + 39, y, 9, up, "hint.candy_up",
                effective < SurvivalCosts.MAX_IV && allowed(field) { withEffective(it, row, effective + 1) }) {
                screen.edit { withEffective(it, row, effective + 1) }
            }
            baseButton(row, x + 50, y, 17, SurvivalCosts.MAX_IV, "button.max", "hint.bottle_cap", SurvivalCosts.bottleCapId(stat), gold)
            baseButton(row, x + 68, y, 9, SurvivalCosts.MIN_IV, "button.zero", "hint.withered_cap", SurvivalCosts.witheredCapId(stat), gold)
        }
        val goldY = zoneY + 1
        goldButton(screen.panelX + 7, goldY, SurvivalCosts.MAX_IV, draft.useGoldCap, "button.gold_cap", SurvivalCosts.GOLD_CAP_ID, "hint.gold_cap", draft.useGoldWithered)
        goldButton(screen.panelX + 7, goldY + 10, SurvivalCosts.MIN_IV, draft.useGoldWithered, "button.gold_withered", SurvivalCosts.GOLD_WITHERED_CAP_ID, "hint.gold_withered", draft.useGoldCap)
    }

    /** Nút "Max" (Bottle Cap) hoặc "0" (Withered Bottle Cap) cho IV gốc; bấm lần nữa để bỏ. */
    private fun baseButton(row: Int, x: Int, y: Int, w: Int, value: Int, labelKey: String, hintKey: String, itemId: String, gold: Boolean) {
        val planned = draft.ivs[row] == value && original.ivs[row] != value
        // Only Bottle Caps không cho dùng nắp khi IV gốc đã đúng giá trị đó.
        val active = !gold && original.ivs[row] != value && (planned || allowed("iv.$row") { withBase(it, row, value) })
        val label = if (planned) gui("button.undo_short") else gui(labelKey)
        itemButton(label, x, y, w, SurvivalCosts.itemById(itemId), hintKey, active) {
            screen.edit { withBase(it, row, if (planned) original.ivs[row] else value) }
        }
    }

    private fun goldButton(x: Int, y: Int, value: Int, on: Boolean, labelKey: String, itemId: String, hintKey: String, otherOn: Boolean) {
        val item = SurvivalCosts.itemById(itemId)
        val have = item?.let { screen.haveCount(it) } ?: 0
        val active = !otherOn && original.ivs.any { it != value } && (on || allowed("iv.gold") { withGold(it, value) })
        val label = if (on) gui("button.undo") else gui(labelKey, have)
        itemButton(label, x, y, 120, item, hintKey, active) {
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
            val y = rowY(row) + 1
            val field = "ev.$row"
            val ev = draft.evs[row]
            val minus = evDown(row)
            itemButton(Text.literal("-"), x + 2, y, 9, SurvivalCosts.evBerry(stat), "hint.ev_down",
                ev > 0 && allowed(field) { setEv(it, row, minus) }) { screen.edit { setEv(it, row, minus) } }
            itemButton(Text.literal("+1"), x + 46, y, 14, SurvivalCosts.feather(stat), "hint.ev_feather",
                canRaise(row, 1) && allowed(field) { setEv(it, row, ev + 1) }) { screen.edit { setEv(it, row, ev + 1) } }
            itemButton(Text.literal("+10"), x + 61, y, 16, SurvivalCosts.vitamin(stat), "hint.ev_vitamin",
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
        drawStatLabels(context)
        EDITOR_STATS.indices.forEach { row ->
            if (showEvs) {
                val color = if (screen.hasProblem("ev.$row")) GuiKit.RED else changed(draft.evs[row], original.evs[row])
                drawValue(context, row, cellX + 12, 33, draft.evs[row].toString(), color, draft.evs[row], PokemonEditing.MAX_EV)
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

    private fun tmItem(move: String) = SurvivalCosts.itemById(SurvivalCosts.tmId(move))

    /** Chiêu chưa học: chỉ chọn được nếu TM tồn tại (TMCraft) và chiêu dạy được bằng TM. */
    override fun isSelectable(move: String): Boolean {
        if (isFree(move)) return true
        val slot = screen.selectedSlot ?: return false
        return move in slot.options.tmMoves && tmItem(move) != null
    }

    /** "Free" / "TM x2" (có 2 TM) / "No TM" (không dạy được bằng TM). */
    override fun rowTag(move: String): Pair<Text, Int>? {
        if (move.isEmpty()) return null
        if (isFree(move)) return Pair(gui("tag.free"), GuiKit.GREEN)
        val slot = screen.selectedSlot ?: return null
        val tm = tmItem(move)
        if (move !in slot.options.tmMoves || tm == null) return Pair(gui("tag.no_tm"), GuiKit.RED)
        val have = screen.haveCount(tm)
        return Pair(gui("tag.tm_count", have), if (have > 0) GuiKit.GOLD else GuiKit.RED)
    }

    override fun pickerNote(): Text = gui("picker_note.survival")
}

class SurvivalNatureTab(screen: EditorScreen) : NatureTab(screen) {
    private val original get() = screen.selectedSlot!!.data

    override fun chosen(): String = draft.effectiveNature

    /** Nature khác cần Mint đúng loại. */
    override fun isSelectable(nature: String): Boolean = nature == original.effectiveNature || mint(nature) != null

    /** "Requires Timid Mint (have 1)", đỏ nếu chưa có. */
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

    /** Chỉ được bật shiny (tốn 1 Shiny Berry), không tắt được. */
    override fun initShiny(x: Int, y: Int, w: Int) {
        shinyButton(x, y, w, gui("hint.shiny_berry"), active = !original.shiny) {
            screen.edit { it.copy(shiny = !it.shiny) }
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        if (screen.hasProblem("shiny")) {
            GuiKit.text(context, screen.font, gui("missing_shiny_berry"), screen.panelX + 8, zoneY + 15, GuiKit.RED, maxWidth = 118)
        }
    }
}
