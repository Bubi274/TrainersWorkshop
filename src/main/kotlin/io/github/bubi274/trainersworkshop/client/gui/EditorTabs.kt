package io.github.bubi274.trainersworkshop.client.gui

import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.client.gui.MoveCategoryIcon
import com.cobblemon.mod.common.client.gui.TypeIcon
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.editor.EDITOR_STATS
import io.github.bubi274.trainersworkshop.editor.EditorMode
import io.github.bubi274.trainersworkshop.editor.MOVE_SLOTS
import io.github.bubi274.trainersworkshop.editor.PokemonEditData
import io.github.bubi274.trainersworkshop.editor.PokemonEditOptions
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.max
import kotlin.math.min

private const val MOD = TrainersWorkshop.MOD_ID
internal const val MAX_IV = 31
internal const val MAX_EV = 252
internal const val MAX_EV_TOTAL = 510

fun gui(key: String, vararg args: Any): Text = Text.translatable("gui.$MOD.$key", *args)

/** Một tab ở panel giữa. Các nút được tạo lại mỗi khi bản nháp thay đổi (xem EditorScreen.rebuild). */
abstract class EditorTab(protected val screen: EditorScreen) {
    abstract val title: Text
    /** Icon 17x17 của tab (SummaryTab vẽ ở cỡ 0.5, kèm tooltip là [title]). */
    open val icon: Identifier = GuiKit.TAB_INFO
    /** Tab chỉ hiện khi có Pokémon đang chọn (tab đồ rơi thì không cần). */
    open val needsPokemon = true
    open fun init() {}
    /** Thoát khỏi danh sách chọn đang mở (khi đổi tab hoặc đổi Pokémon). */
    open fun reset() {}
    /** Nền panel giữa, vẽ trước các nút. */
    open fun renderBackdrop(context: DrawContext) {
        GuiKit.panel(context, screen.panelX, screen.panelY, GuiKit.PANEL_W, GuiKit.PANEL_H)
    }
    open fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {}
    open fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = false
    open fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean = false

    protected val draft: PokemonEditData get() = screen.draft!!
    protected val creative: Boolean get() = screen.mode != EditorMode.SURVIVAL
    protected val step: Int get() = if (Screen.hasShiftDown()) 10 else 1

    protected fun drawTexturePanel(context: DrawContext, texture: Identifier) {
        context.drawTexture(texture, screen.panelX, screen.panelY, 0f, 0f, GuiKit.PANEL_W, GuiKit.PANEL_H, GuiKit.PANEL_W, GuiKit.PANEL_H)
    }

    protected fun hoverTooltip(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, text: Text) {
        if (GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), x, y, w, h)) screen.deferTooltip(text)
    }
}

/** Danh sách cuộn đơn giản: mỗi hàng cao [rowHeight], cuộn bằng con lăn chuột. */
class RowList(val rows: Int, val rowHeight: Int = 11) {
    var scroll = 0
        private set

    fun reset() {
        scroll = 0
    }

    fun clamp(size: Int) {
        scroll = scroll.coerceIn(0, max(0, size - rows))
    }

    fun scrollBy(amount: Double, size: Int) {
        scroll -= if (amount > 0) 1 else if (amount < 0) -1 else 0
        clamp(size)
    }

    fun visible(size: Int): IntRange {
        clamp(size)
        return scroll until min(size, scroll + rows)
    }

    /** Hàng (chỉ số trong danh sách) dưới chuột, hoặc -1. */
    fun hit(mouseX: Double, mouseY: Double, x: Int, y: Int, width: Int, size: Int): Int {
        if (!GuiKit.inside(mouseX, mouseY, x, y, width, rows * rowHeight)) return -1
        val index = scroll + ((mouseY - y) / rowHeight).toInt()
        return if (index in 0 until size) index else -1
    }

    fun drawScrollbar(context: DrawContext, x: Int, y: Int, size: Int) {
        if (size <= rows) return
        val height = rows * rowHeight
        val barHeight = max(8, height * rows / size)
        val barY = y + (height - barHeight) * scroll / max(1, size - rows)
        context.fill(x, y, x + 2, y + height, GuiKit.DARK)
        context.fill(x, barY, x + 2, barY + barHeight, GuiKit.EDGE)
    }
}

/**
 * Nền chung của các tab dạng "ô nhãn tối + ô giá trị sáng" (summary_info_base.png, 134x148):
 * 6 hàng cao 11 px, cách nhau 15 px, bắt đầu ở y=5; ô nhãn x=5..49, ô giá trị x=50..128.
 */
abstract class InfoRowsTab(screen: EditorScreen) : EditorTab(screen) {
    protected fun rowY(row: Int) = screen.panelY + 5 + row * 15
    protected val labelX get() = screen.panelX + 5
    protected val cellX get() = screen.panelX + 50
    protected val cellW = 79
    /** Vùng sáng phía dưới 6 hàng (y=91..112): nút phụ, tổng EV, ghi chú. */
    protected val zoneY get() = screen.panelY + 91

    override fun renderBackdrop(context: DrawContext) = drawTexturePanel(context, GuiKit.INFO_BASE)

    protected fun drawLabel(context: DrawContext, row: Int, text: Text, color: Int = GuiKit.WHITE) {
        GuiKit.text(context, screen.font, text, labelX + 3, rowY(row) + 1, color, maxWidth = 39, minScale = GuiKit.MEDIUM)
    }
}

/** IV và EV của 6 chỉ số, giống tab Stats của Summary: 6 hàng + thanh chọn trang "IVs | EVs" ở dưới cùng. */
abstract class StatPageTab(screen: EditorScreen) : InfoRowsTab(screen) {
    override val title: Text = gui("tab.stats")
    override val icon: Identifier = GuiKit.TAB_STATS

    protected var showEvs = false

    protected fun initPageButtons() {
        val y = screen.panelY + 123
        screen.button(gui("button.view_iv"), screen.panelX + 5, y, 61, 20) { showEvs = false; screen.rebuild() }.selected = !showEvs
        screen.button(gui("button.view_ev"), screen.panelX + 70, y, 59, 20) { showEvs = true; screen.rebuild() }.selected = showEvs
    }

    /** Đỏ nếu nature làm tăng chỉ số này, xanh nếu làm giảm (như Summary). */
    protected fun statColor(row: Int): Int {
        val (up, down) = GuiKit.natureStats(draft.effectiveNature) ?: return GuiKit.WHITE
        return when (EDITOR_STATS[row]) {
            up -> GuiKit.STAT_UP
            down -> GuiKit.STAT_DOWN
            else -> GuiKit.WHITE
        }
    }

    protected fun drawStatLabels(context: DrawContext) {
        EDITOR_STATS.forEachIndexed { row, stat ->
            drawLabel(context, row, Text.literal(GuiKit.longStat(stat)), statColor(row))
            val pair = GuiKit.natureStats(draft.effectiveNature)
            val icon = when (stat) {
                pair?.first -> GuiKit.STAT_UP_ICON
                pair?.second -> GuiKit.STAT_DOWN_ICON
                else -> null
            }
            if (icon != null) context.drawTexture(icon, labelX + 36, rowY(row) + 2, 0f, 0f, 8, 6, 8, 6)
        }
    }

    /** Số ở trên, thanh giá trị mảnh ở dưới, trong vùng [x, x+w) của ô giá trị. */
    protected fun drawValue(context: DrawContext, row: Int, x: Int, w: Int, text: String, color: Int, value: Int, max: Int, barColor: Int = 0x55AA55) {
        GuiKit.textCentered(context, screen.font, Text.literal(text), x + w / 2f, rowY(row) + 1, color, maxWidth = w, minScale = GuiKit.MEDIUM)
        GuiKit.bar(context, x + 1, rowY(row) + 9, w - 2, 2, value, max, barColor)
    }

    /** Thanh tổng EV "EV total: 14 / 510" ở vùng sáng dưới 6 hàng. */
    protected fun drawEvMeter(context: DrawContext, y: Int) {
        val x = screen.panelX + 7
        val total = draft.evs.sum()
        GuiKit.bar(context, x, y, 120, 10, total, MAX_EV_TOTAL, if (total >= MAX_EV_TOTAL) GuiKit.GOLD else 0x3F8F3F)
        context.drawBorder(x, y, 120, 10, GuiKit.EDGE)
        GuiKit.textCentered(context, screen.font, gui("ev_total", total, MAX_EV_TOTAL), x + 60, y + 1, maxWidth = 116, minScale = GuiKit.MEDIUM)
    }

    protected fun changedColor(value: Int, original: Int?) = if (original != null && value != original) GuiKit.GOLD else GuiKit.WHITE
}

/** Bản creative / Trainer Editor: bấm để ±1, giữ Shift để ±10. */
open class StatsTab(screen: EditorScreen) : StatPageTab(screen) {
    override fun init() {
        initPageButtons()
        EDITOR_STATS.indices.forEach { row -> if (showEvs) initEvButtons(row) else initIvButtons(row) }
        if (showEvs) {
            screen.button(gui("button.clear_evs"), screen.panelX + 37, zoneY + 12, 60, 9) { screen.edit { it.copy(evs = List(6) { 0 }) } }
        } else {
            screen.button(gui("button.max_ivs"), screen.panelX + 7, zoneY + 4, 120, 14) { screen.edit { it.copy(ivs = List(6) { MAX_IV }) } }
        }
    }

    private fun initIvButtons(row: Int) {
        val iv = draft.ivs[row]
        val y = rowY(row) + 1
        screen.button(Text.literal("-"), cellX + 2, y, 9, 9, gui("hint.step"), active = iv > 0) { changeIv(row, -step) }
        screen.button(Text.literal("+"), cellX + 68, y, 9, 9, gui("hint.step"), active = iv < MAX_IV) { changeIv(row, step) }
    }

    private fun initEvButtons(row: Int) {
        val ev = draft.evs[row]
        val y = rowY(row) + 1
        screen.button(Text.literal("-"), cellX + 2, y, 9, 9, gui("hint.step"), active = ev > 0) { changeEv(row, -step) }
        screen.button(Text.literal("+"), cellX + 68, y, 9, 9, gui("hint.step"), active = ev < MAX_EV && draft.evs.sum() < MAX_EV_TOTAL) { changeEv(row, step) }
    }

    protected fun changeIv(row: Int, delta: Int) = screen.edit { data ->
        data.copy(ivs = data.ivs.toMutableList().also { it[row] = (it[row] + delta).coerceIn(0, MAX_IV) })
    }

    protected fun changeEv(row: Int, delta: Int) = screen.edit { data ->
        val current = data.evs[row]
        val room = MAX_EV_TOTAL - (data.evs.sum() - current)
        val value = (current + delta).coerceIn(0, min(MAX_EV, room))
        data.copy(evs = data.evs.toMutableList().also { it[row] = value })
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        drawStatLabels(context)
        val original = screen.selectedSlot?.data
        EDITOR_STATS.indices.forEach { row ->
            if (showEvs) {
                drawValue(context, row, cellX + 12, 55, "${draft.evs[row]} / $MAX_EV", changedColor(draft.evs[row], original?.evs?.get(row)), draft.evs[row], MAX_EV)
            } else {
                drawValue(context, row, cellX + 12, 55, "${draft.ivs[row]} / $MAX_IV", changedColor(draft.ivs[row], original?.ivs?.get(row)), draft.ivs[row], MAX_IV)
            }
        }
        if (showEvs) drawEvMeter(context, zoneY + 1)
    }
}

/** 4 ô chiêu (kiểu MoveSlotWidget của Cobblemon); bấm vào một ô để chọn chiêu khác từ danh sách có ô tìm kiếm. */
open class MovesTab(screen: EditorScreen) : EditorTab(screen) {
    override val title: Text = gui("tab.moves")
    override val icon: Identifier = GuiKit.TAB_MOVES

    protected var pickingSlot = -1
    protected var search = ""
    protected var lastHoveredMove = ""
    private var focusSearch = false
    protected val list = RowList(rows = 7, rowHeight = 12)

    private val listX get() = screen.panelX + 7
    private val listY get() = screen.panelY + 22
    private val listWidth = 117

    /** Chiêu có thể chọn cho ô đang chọn (bỏ các chiêu đang nằm ở ô khác). */
    protected open fun candidates(): List<String> {
        val options = screen.selectedSlot?.options ?: return emptyList()
        val others = draft.moves.filterIndexed { i, m -> i != pickingSlot && m.isNotEmpty() }
        return options.legalMoves.filter { it !in others }
    }

    /** Danh sách sau khi lọc theo ô tìm kiếm (lọc lúc vẽ, không cần dựng lại nút). */
    private fun filtered(): List<String> {
        val query = search.trim().lowercase()
        val all = candidates()
        if (query.isEmpty()) return all
        return all.filter { GuiKit.moveName(it).string.lowercase().contains(query) || it.lowercase().contains(query) }
    }

    /** Có được chọn chiêu này không (survival kiểm tra TM). */
    protected open fun isSelectable(move: String) = true

    /** Nhãn phụ bên phải mỗi hàng (survival: miễn phí / TM). */
    protected open fun rowTag(move: String): Pair<Text, Int>? = null

    /** Dòng thứ 3 ở 3 ô thông tin góc trái dưới ("Free" hoặc "Needs TM (have N)"). */
    protected open fun moveCostInfo(move: String): Pair<Text, Int> = Pair(gui("tag.free"), GuiKit.GREEN)

    /** Dòng chú thích dưới danh sách chọn chiêu (survival giải thích các nhãn). */
    protected open fun pickerNote(): Text? = null

    override fun reset() {
        pickingSlot = -1
        search = ""
        lastHoveredMove = ""
    }

    private fun beginPicking(slot: Int) {
        pickingSlot = slot
        search = ""
        lastHoveredMove = draft.moves.getOrNull(slot).orEmpty()
        focusSearch = true
        list.reset()
        screen.rebuild()
    }

    private fun endPicking() {
        pickingSlot = -1
        screen.rebuild()
    }

    override fun init() {
        if (pickingSlot < 0) return
        screen.button(gui("button.back"), screen.panelX + 72, screen.panelY + 128, 54, 14) { endPicking() }
        val field = TextFieldWidget(screen.font, screen.panelX + 7, screen.panelY + 6, listWidth + 3, 12, gui("search"))
        field.setMaxLength(32)
        field.setPlaceholder(gui("search"))
        field.text = search
        field.setChangedListener {
            search = it
            list.reset()
        }
        screen.addWidget(field)
        if (focusSearch) {
            screen.focusWidget(field)
            focusSearch = false
        }
    }

    override fun renderBackdrop(context: DrawContext) {
        drawTexturePanel(context, GuiKit.MOVES_BASE)
        // Danh sách chọn chiêu che vùng 4 ô chiêu bằng nền sáng của panel.
        if (pickingSlot >= 0) context.fill(screen.panelX + 5, screen.panelY + 5, screen.panelX + 129, screen.panelY + 112, GuiKit.LIGHT)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (pickingSlot >= 0) renderPicker(context, mouseX, mouseY) else renderSlots(context, mouseX, mouseY)
    }

    private fun slotX() = screen.panelX + 13
    private fun slotY(i: Int) = screen.panelY + 6 + i * 25

    private fun renderSlots(context: DrawContext, mouseX: Int, mouseY: Int) {
        val font = screen.font
        val original = screen.selectedSlot?.data?.moves
        var hoveredMove: String? = null
        for (i in 0 until MOVE_SLOTS) {
            val x = slotX()
            val y = slotY(i)
            val move = draft.moves[i]
            val hovered = GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), x, y, GuiKit.MOVE_WIDTH, GuiKit.MOVE_HEIGHT)
            if (hovered && move.isNotEmpty()) hoveredMove = move
            val template = if (move.isEmpty()) null else Moves.getByName(move)
            GuiKit.moveSlot(context, x, y, if (move.isEmpty()) 0x606060 else GuiKit.moveHue(move), hovered)
            val changed = original != null && original[i] != move
            val nameColor = if (changed) GuiKit.GOLD else GuiKit.WHITE
            if (template == null) {
                GuiKit.text(context, font, if (move.isEmpty()) gui("empty") else Text.literal(move), x + 28, y + 2, if (move.isEmpty()) GuiKit.GRAY else nameColor, maxWidth = 76, minScale = GuiKit.MEDIUM)
                continue
            }
            val tag = rowTag(move)?.takeIf { changed }
            val tagWidth = tag?.let { GuiKit.textWidth(font, it.first, GuiKit.SMALL, small = true).toInt() + 3 } ?: 0
            // Cùng toạ độ MoveSlotWidget: icon hệ (+2,+2), tên (+28,+2), icon loại đòn (+66,+13.5), PP (+93,+13).
            TypeIcon(x = x + 2, y = y + 2, type = template.elementalType, secondaryType = null).render(context)
            GuiKit.text(context, font, GuiKit.moveName(move), x + 28, y + 2, nameColor, maxWidth = 76 - tagWidth, minScale = GuiKit.MEDIUM)
            val power = template.power.toInt()
            GuiKit.text(context, font, gui("power", if (power > 0) power.toString() else "-"), x + 28, y + 13, GuiKit.GRAY, maxWidth = 36, minScale = GuiKit.MEDIUM)
            MoveCategoryIcon(x = x + 66, y = y + 13.5f, category = template.damageCategory).render(context)
            GuiKit.textRight(context, font, gui("pp", template.pp), x + 105, y + 13, GuiKit.GRAY, maxWidth = 24, minScale = GuiKit.MEDIUM)
            if (tag != null) GuiKit.textRight(context, font, tag.first, x + 105, y + 4, tag.second, maxWidth = tagWidth, small = true)
        }
        if (hoveredMove != null) lastHoveredMove = hoveredMove
        val detailMove = hoveredMove
            ?: lastHoveredMove.takeIf { it in draft.moves && it.isNotEmpty() }
            ?: draft.moves.firstOrNull { it.isNotEmpty() }
        renderMoveDetails(context, detailMove)
        GuiKit.textWrapped(context, font, gui("hint.moves"), screen.panelX + 72, screen.panelY + 116, 54, 4, GuiKit.GRAY)
    }

    /** Ba ô nhỏ ở góc trái dưới panel: Category, Accuracy, và "Free" / "Needs TM (have N)" của chiêu đang chọn/rê chuột. */
    private fun renderMoveDetails(context: DrawContext, move: String?) {
        val template = move?.let { Moves.getByName(it) } ?: return
        val accuracy = template.accuracy
        val (costText, costColor) = moveCostInfo(move)
        MoveCategoryIcon(x = screen.panelX + 9, y = screen.panelY + 114.5f, category = template.damageCategory).render(context)
        GuiKit.text(context, screen.font, template.damageCategory.displayName, screen.panelX + 24, screen.panelY + 114f, scale = GuiKit.MEDIUM, maxWidth = 39, minScale = 0.55f)
        GuiKit.text(context, screen.font, gui("accuracy", if (accuracy > 0 && accuracy <= 100) "${accuracy.toInt()}%" else "-"), screen.panelX + 9, screen.panelY + 125f, scale = GuiKit.MEDIUM, maxWidth = 54, minScale = 0.55f)
        GuiKit.text(context, screen.font, costText, screen.panelX + 9, screen.panelY + 136f, costColor, scale = GuiKit.MEDIUM, maxWidth = 54, minScale = 0.55f)
    }

    private fun renderPicker(context: DrawContext, mouseX: Int, mouseY: Int) {
        val font = screen.font
        val moves = filtered()
        val all = candidates().size
        var hoveredMove: String? = null
        for (index in list.visible(moves.size)) {
            val move = moves[index]
            val y = listY + (index - list.scroll) * list.rowHeight
            val hovered = GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), listX, y, listWidth, list.rowHeight)
            if (hovered) hoveredMove = move
            context.fill(listX, y, listX + listWidth, y + list.rowHeight - 1, if (hovered) GuiKit.FRAME else GuiKit.DARK)
            val template = Moves.getByName(move)
            if (template != null) {
                TypeIcon(x = listX + 1, y = y + 1, type = template.elementalType, secondaryType = null, centeredX = false, small = true).render(context)
                MoveCategoryIcon(x = listX + listWidth - 13, y = y + 2f, category = template.damageCategory).render(context)
            }
            val tag = rowTag(move)
            val tagWidth = tag?.let { GuiKit.textWidth(font, it.first, GuiKit.SMALL, small = true).toInt() + 3 } ?: 0
            val color = when {
                move == draft.moves[pickingSlot] -> GuiKit.GOLD
                isSelectable(move) -> GuiKit.WHITE
                else -> GuiKit.RED
            }
            GuiKit.text(context, font, GuiKit.moveName(move), listX + 13, y + 1.5f, color, maxWidth = listWidth - 13 - 14 - tagWidth, minScale = GuiKit.MEDIUM)
            if (tag != null) GuiKit.textRight(context, font, tag.first, listX + listWidth - 15, y + 4, tag.second, maxWidth = tagWidth, small = true)
        }
        if (hoveredMove != null) lastHoveredMove = hoveredMove
        val detailMove = hoveredMove
            ?: lastHoveredMove.takeIf { it.isNotEmpty() }
            ?: draft.moves.getOrNull(pickingSlot)?.takeIf { it.isNotEmpty() }
            ?: moves.firstOrNull()
        renderMoveDetails(context, detailMove)
        if (moves.isEmpty()) GuiKit.textCentered(context, font, gui("no_results"), listX + listWidth / 2, listY + 8, GuiKit.GRAY, maxWidth = listWidth)
        list.drawScrollbar(context, listX + listWidth + 1, listY, moves.size)
        GuiKit.text(context, font, gui("moves_count", moves.size, all), screen.panelX + 72, screen.panelY + 116, GuiKit.GRAY, maxWidth = 54, small = true)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (pickingSlot < 0) {
            for (i in 0 until MOVE_SLOTS) {
                if (GuiKit.inside(mouseX, mouseY, slotX(), slotY(i), GuiKit.MOVE_WIDTH, GuiKit.MOVE_HEIGHT)) {
                    beginPicking(i)
                    return true
                }
            }
            return false
        }
        val moves = filtered()
        val index = list.hit(mouseX, mouseY, listX, listY, listWidth, moves.size)
        if (index < 0 || !isSelectable(moves[index])) return false
        val slot = pickingSlot
        pickingSlot = -1
        screen.edit { data -> data.copy(moves = data.moves.toMutableList().also { it[slot] = moves[index] }) }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (pickingSlot < 0) return false
        list.scrollBy(amount, filtered().size)
        return true
    }
}

/**
 * Lưới 25 nature, 2 cột x 13 hàng (không cần cuộn). Mỗi ô: tên + ▲chỉ số tăng (đỏ) / ▼chỉ số giảm (xanh).
 * Nature hiện tại của Pokémon có viền vàng, nature đang chọn có nền sáng.
 */
open class NatureTab(screen: EditorScreen) : EditorTab(screen) {
    override val title: Text = gui("tab.nature")
    override val icon: Identifier = GuiKit.TAB_NATURE

    protected val natures: List<String> by lazy {
        Natures.all().map { it.name.toString() }.sortedBy { GuiKit.natureName(it).string }
    }

    private val rows = 13
    private val cellW = 61
    private val cellH = 8
    private fun cellX(index: Int) = screen.panelX + 5 + (index / rows) * (cellW + 1)
    private fun cellY(index: Int) = screen.panelY + 5 + (index % rows) * (cellH + 1)

    /** Nature đang được chọn trong bản nháp. */
    protected open fun chosen(): String = draft.effectiveNature

    protected open fun isSelectable(nature: String) = true

    /** Dòng chi phí dưới lưới (survival: "Requires Timid Mint (have 1)"). */
    protected open fun costLine(): Pair<Text, Int>? = null

    protected open fun choose(nature: String) = screen.edit { it.copy(nature = nature, mintedNature = "") }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val font = screen.font
        val current = screen.selectedSlot?.data?.effectiveNature ?: ""
        natures.forEachIndexed { index, nature ->
            val x = cellX(index)
            val y = cellY(index)
            val hovered = GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), x, y, cellW, cellH)
            val isChosen = nature == chosen()
            context.fill(x, y, x + cellW, y + cellH, if (isChosen) GuiKit.SELECTED_FILL else if (hovered && isSelectable(nature)) GuiKit.FRAME else GuiKit.DARK)
            if (nature == current) context.drawBorder(x, y, cellW, cellH, 0xFF000000.toInt() or GuiKit.GOLD)
            val nameColor = when {
                nature == current -> GuiKit.GOLD
                isSelectable(nature) -> GuiKit.WHITE
                else -> GuiKit.RED
            }
            GuiKit.text(context, font, GuiKit.natureName(nature), x + 2, y + 1, nameColor, scale = GuiKit.MEDIUM, maxWidth = 27, minScale = 0.6f)
            val stats = GuiKit.natureStats(nature)
            if (stats == null) {
                GuiKit.text(context, font, Text.literal("-"), x + 36, y + 1, GuiKit.DIM, scale = GuiKit.MEDIUM)
            } else {
                GuiKit.text(context, font, Text.literal("▲" + GuiKit.shortStat(stats.first)), x + 29, y + 1, GuiKit.STAT_UP, scale = 0.65f, maxWidth = 16, minScale = 0.5f)
                GuiKit.text(context, font, Text.literal("▼" + GuiKit.shortStat(stats.second)), x + 45, y + 1, GuiKit.STAT_DOWN, scale = 0.65f, maxWidth = 15, minScale = 0.5f)
            }
        }
        val textX = screen.panelX + 7
        val textY = screen.panelY + 123
        GuiKit.text(context, font, gui("current_nature", GuiKit.natureName(current)), textX, textY, GuiKit.GRAY, scale = GuiKit.MEDIUM, maxWidth = 120, minScale = 0.6f)
        if (chosen() != current) {
            GuiKit.text(context, font, gui("selected_nature", GuiKit.natureName(chosen()), GuiKit.natureEffect(chosen())), textX, textY + 8, scale = GuiKit.MEDIUM, maxWidth = 120, minScale = 0.6f)
        }
        costLine()?.let { (line, color) -> GuiKit.text(context, font, line, textX, textY + 16, color, scale = GuiKit.MEDIUM, maxWidth = 120, minScale = 0.6f) }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        natures.forEachIndexed { index, nature ->
            if (GuiKit.inside(mouseX, mouseY, cellX(index), cellY(index), cellW, cellH)) {
                if (!isSelectable(nature)) return false
                choose(nature)
                return true
            }
        }
        return false
    }
}

/** Shiny, ability, giới tính, level, loại bóng: mỗi hàng là ô nhãn tối + ô giá trị sáng. */
open class OtherTab(screen: EditorScreen) : InfoRowsTab(screen) {
    override val title: Text = gui("tab.other")
    override val icon: Identifier = GuiKit.TAB_OTHER

    private var pickingBall = false

    override fun reset() {
        pickingBall = false
    }
    private val ballList = RowList(rows = 8, rowHeight = 12)
    private val ballListX get() = screen.panelX + 7
    private val ballListY get() = screen.panelY + 22

    override fun init() {
        if (pickingBall) {
            screen.button(gui("button.back"), screen.panelX + 7, screen.panelY + 122, 56, 16) { pickingBall = false; screen.rebuild() }
            return
        }
        val options = screen.selectedSlot?.options ?: return
        initShiny(cellX + 2, rowY(0) + 1, cellW - 4)
        initAbility(cellX + 2, rowY(1) + 1, cellW - 4, options)
        if (!creative) {
            if (levelEditable) initLevel(cellX, rowY(3) + 1)
            return
        }

        val genderButton = screen.button(gui("gender.${draft.gender.lowercase()}"), cellX + 2, rowY(2) + 1, cellW - 4, 9, gui("hint.cycle"), active = options.genders.size > 1) {
            screen.edit { it.copy(gender = cycle(options.genders, it.gender)) }
        }
        genderSymbol(draft.gender)?.let { (symbol, color) ->
            genderButton.icon = { ctx, x, y -> GuiKit.text(ctx, screen.font, Text.literal(symbol), x, y - 0.5f, color) }
        }
        screen.button(Text.literal("-"), cellX + 2, rowY(3) + 1, 9, 9, gui("hint.step"), active = draft.level > 1) {
            screen.edit { it.copy(level = (it.level - step).coerceIn(1, screen.state.maxLevel)) }
        }
        screen.button(Text.literal("+"), cellX + 68, rowY(3) + 1, 9, 9, gui("hint.step"), active = draft.level < screen.state.maxLevel) {
            screen.edit { it.copy(level = (it.level + step).coerceIn(1, screen.state.maxLevel)) }
        }
        val ball = draft.ball
        screen.button(GuiKit.ballName(ball), cellX + 2, rowY(4) + 1, cellW - 4, 9, gui("hint.pick_ball")) {
            pickingBall = true
            ballList.reset()
            screen.rebuild()
        }.icon = { ctx, x, y -> GuiKit.itemIcon(ctx, ball, x, y, 8) }
    }

    /** Survival cho tăng level bằng kẹo (hàng Level không bị làm mờ). Bản Creative tự chỉnh level nên không dùng. */
    protected open val levelEditable: Boolean get() = false

    /** Các nút của hàng Level ở Survival; [x] là góc trái của ô giá trị, [y] là mép trên nút. */
    protected open fun initLevel(x: Int, y: Int) {}

    protected open fun initShiny(x: Int, y: Int, w: Int) {
        shinyButton(x, y, w, null) { screen.edit { it.copy(shiny = !it.shiny) } }
    }

    protected open fun initAbility(x: Int, y: Int, w: Int, options: PokemonEditOptions) {
        screen.button(abilityLabel(draft.ability), x, y, w, 9, gui("hint.cycle")) {
            screen.edit { it.copy(ability = cycle(options.abilities, it.ability)) }
        }
    }

    protected fun shinyButton(x: Int, y: Int, w: Int, tooltip: Text?, active: Boolean = true, onPress: () -> Unit) {
        screen.button(yesNo(draft.shiny), x, y, w, 9, tooltip, active, onPress).icon = { ctx, ix, iy ->
            ctx.drawTexture(GuiKit.SHINY_ICON, ix, iy, 8, 8, 0f, 0f, 16, 16, 16, 16)
        }
    }

    override fun renderBackdrop(context: DrawContext) {
        if (pickingBall) {
            GuiKit.panel(context, screen.panelX, screen.panelY, GuiKit.PANEL_W, GuiKit.PANEL_H)
        } else {
            super.renderBackdrop(context)
            // Xoá hàng thứ 6 (y=80..91) và các ô/thanh trống phía dưới (y=92..143) của summary_info_base.png vì tab Other chỉ dùng 5 hàng (0..4).
            context.fill(screen.panelX + 4, screen.panelY + 77, screen.panelX + GuiKit.PANEL_W - 4, screen.panelY + GuiKit.PANEL_H - 4, GuiKit.LIGHT)
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (pickingBall) {
            renderBallPicker(context, mouseX, mouseY)
            return
        }
        val font = screen.font
        val locked = !creative
        val labels = listOf("shiny", "ability", "gender", "level", "ball")
        labels.forEachIndexed { row, key ->
            val color = when {
                screen.hasProblem(key) -> GuiKit.RED
                locked && row > 1 && !(row == 3 && levelEditable) -> GuiKit.DIM
                else -> GuiKit.WHITE
            }
            drawLabel(context, row, gui("field.$key"), color)
        }
        if (creative) {
            GuiKit.textCentered(context, font, gui("level_value", draft.level, screen.state.maxLevel), cellX + 39.5f, rowY(3) + 1, maxWidth = 52, minScale = GuiKit.MEDIUM)
            return
        }
        renderReadOnly(context)
    }

    /** Survival: giới tính và bóng chỉ hiển thị (mờ) và ghi rõ "Creative only"; level do SurvivalOtherTab vẽ. */
    private fun renderReadOnly(context: DrawContext) {
        val font = screen.font
        val x = cellX + 4
        val w = cellW - 8
        val gender = genderSymbol(draft.gender)
        GuiKit.text(context, font, gui("gender.${draft.gender.lowercase()}"), x + if (gender != null) 9 else 0, rowY(2) + 1, GuiKit.DIM, maxWidth = w - 9, minScale = GuiKit.MEDIUM)
        if (gender != null) GuiKit.text(context, font, Text.literal(gender.first), x, rowY(2) + 1, gender.second)
        if (!levelEditable) GuiKit.text(context, font, Text.literal(draft.level.toString()), x, rowY(3) + 1, GuiKit.DIM, maxWidth = w)
        GuiKit.itemIcon(context, draft.ball, x, rowY(4) + 1, 8)
        GuiKit.text(context, font, GuiKit.ballName(draft.ball), x + 10, rowY(4) + 1, GuiKit.DIM, maxWidth = w - 10, minScale = GuiKit.MEDIUM)
        GuiKit.textWrapped(context, font, gui("creative_only"), screen.panelX + 8, screen.panelY + if (levelEditable) 91 else 83, 118, 2, GuiKit.GRAY)
    }

    private fun renderBallPicker(context: DrawContext, mouseX: Int, mouseY: Int) {
        val font = screen.font
        val balls = screen.state.balls
        GuiKit.text(context, font, gui("choose_ball"), screen.panelX + 8, screen.panelY + 9, GuiKit.WHITE, maxWidth = 118)
        for (index in ballList.visible(balls.size)) {
            val ball = balls[index]
            val y = ballListY + (index - ballList.scroll) * ballList.rowHeight
            val hovered = GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), ballListX, y, 117, ballList.rowHeight)
            context.fill(ballListX, y, ballListX + 117, y + ballList.rowHeight - 1, if (hovered) GuiKit.FRAME else GuiKit.DARK)
            GuiKit.itemIcon(context, ball, ballListX + 2, y + 1, 9)
            GuiKit.text(context, font, GuiKit.ballName(ball), ballListX + 14, y + 1.5f, if (ball == draft.ball) GuiKit.GOLD else GuiKit.WHITE, maxWidth = 100, minScale = GuiKit.MEDIUM)
        }
        ballList.drawScrollbar(context, ballListX + 118, ballListY, balls.size)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!pickingBall) return false
        val balls = screen.state.balls
        val index = ballList.hit(mouseX, mouseY, ballListX, ballListY, 117, balls.size)
        if (index < 0) return false
        pickingBall = false
        screen.edit { it.copy(ball = balls[index]) }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (!pickingBall) return false
        ballList.scrollBy(amount, screen.state.balls.size)
        return true
    }

    protected fun yesNo(value: Boolean): Text = gui(if (value) "yes" else "no")

    private fun genderSymbol(gender: String): Pair<String, Int>? = when (gender.uppercase()) {
        "MALE" -> Pair("♂", GuiKit.MALE)
        "FEMALE" -> Pair("♀", GuiKit.FEMALE)
        else -> null
    }

    protected fun abilityLabel(name: String): Text {
        val template = Abilities.get(name) ?: return Text.literal(name)
        return Text.translatable(template.displayName)
    }

    /** Lấy phần tử kế tiếp (Shift: phần tử trước) trong danh sách. */
    protected fun cycle(values: List<String>, current: String): String {
        if (values.isEmpty()) return current
        val index = values.indexOf(current)
        val next = if (Screen.hasShiftDown()) index - 1 else index + 1
        return values[Math.floorMod(next, values.size)]
    }
}
