package io.github.bubi274.trainersworkshop.client.gui

import io.github.bubi274.trainersworkshop.editor.EDITOR_STATS
import io.github.bubi274.trainersworkshop.editor.SurvivalCosts
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

/*
 * Các tab của PokeEditor bản survival: cùng giao diện với bản creative,
 * nhưng mỗi thay đổi cần vật phẩm và ô thiếu vật phẩm hiện màu đỏ.
 */

class SurvivalStatsTab(screen: EditorScreen) : StatsTab(screen) {
    private val original get() = screen.selectedSlot!!.data

    /** IV: nút "Max" đưa chỉ số lên 31 bằng Bottle Cap (bấm lần nữa để bỏ). */
    override fun initIvButtons(row: Int, x: Int, y: Int) {
        val maxed = draft.ivs[row] == SurvivalCosts.MAX_IV
        val canChange = original.ivs[row] < SurvivalCosts.MAX_IV && !draft.useGoldCap
        screen.button(gui(if (maxed && canChange) "button.undo" else "button.max"), x + 26, y, 28, 12, gui("hint.bottle_cap"), active = canChange) {
            screen.edit { data ->
                val value = if (data.ivs[row] == SurvivalCosts.MAX_IV) original.ivs[row] else SurvivalCosts.MAX_IV
                data.copy(ivs = data.ivs.toMutableList().also { it[row] = value })
            }
        }
    }

    /** EV: "+" dùng feather (+1) hoặc vitamin khi giữ Shift (+10); "-" dùng berry giảm EV (-10). */
    override fun initEvButtons(row: Int, x: Int, y: Int) {
        val ev = draft.evs[row]
        screen.button(Text.literal("-"), x + 70, y, 10, 12, gui("hint.ev_down"), active = ev > 0) {
            screen.edit { data ->
                val current = data.evs[row]
                val base = original.evs[row]
                // Trên mức gốc: bỏ bớt phần định tăng. Từ mức gốc trở xuống: mỗi berry giảm 10.
                val value = if (current > base) maxOf(base, current - step) else maxOf(0, current - SurvivalCosts.EV_PER_BERRY)
                data.copy(evs = data.evs.toMutableList().also { it[row] = value })
            }
        }
        screen.button(Text.literal("+"), x + 108, y, 10, 12, gui("hint.ev_up"), active = ev < 252 && draft.evs.sum() < 510) {
            changeEv(row, step)
        }
    }

    /** Gold Bottle Cap: cả 6 IV lên 31. */
    override fun initFooter(x: Int, y: Int) {
        val allMax = original.ivs.all { it == SurvivalCosts.MAX_IV }
        val label = gui(if (draft.useGoldCap) "button.gold_cap_undo" else "button.gold_cap")
        screen.button(label, x, y, 128, 12, gui("hint.gold_cap"), active = !allMax) {
            screen.edit { data ->
                if (data.useGoldCap) data.copy(useGoldCap = false, ivs = original.ivs)
                else data.copy(useGoldCap = true, ivs = List(6) { SurvivalCosts.MAX_IV })
            }
        }
    }

    override fun ivValueX(x: Int) = x + 16
    override fun ivColor(row: Int) =
        if (screen.hasProblem("iv.$row") || (draft.useGoldCap && screen.hasProblem("iv.gold"))) GuiKit.RED else super.ivColor(row)
    override fun evColor(row: Int) = if (screen.hasProblem("ev.$row")) GuiKit.RED else super.evColor(row)

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        EDITOR_STATS.indices.filter { screen.hasProblem("iv.$it") || screen.hasProblem("ev.$it") }.firstOrNull()?.let {
            GuiKit.text(context, screen.font, gui("missing_items"), screen.contentX, screen.contentY + 138, GuiKit.RED, 0.5f)
        }
    }
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
