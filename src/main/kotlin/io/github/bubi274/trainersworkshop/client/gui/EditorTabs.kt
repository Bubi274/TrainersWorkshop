package io.github.bubi274.trainersworkshop.client.gui

import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.pokemon.Natures
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.editor.EDITOR_STATS
import io.github.bubi274.trainersworkshop.editor.EditorMode
import io.github.bubi274.trainersworkshop.editor.MOVE_SLOTS
import io.github.bubi274.trainersworkshop.editor.PokemonEditData
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import kotlin.math.max
import kotlin.math.min

private const val MOD = TrainersWorkshop.MOD_ID
private const val MAX_IV = 31
private const val MAX_EV = 252
private const val MAX_EV_TOTAL = 510

fun gui(key: String, vararg args: Any): Text = Text.translatable("gui.$MOD.$key", *args)

/** Một tab ở panel giữa. Các nút được tạo lại mỗi khi bản nháp thay đổi (xem EditorScreen.rebuild). */
abstract class EditorTab(protected val screen: EditorScreen) {
    abstract val title: Text
    open fun init() {}
    open fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {}
    open fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = false
    open fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean = false

    protected val draft: PokemonEditData get() = screen.draft!!
    protected val creative: Boolean get() = screen.mode != EditorMode.SURVIVAL
    protected val step: Int get() = if (Screen.hasShiftDown()) 10 else 1
}

/** Danh sách cuộn đơn giản: mỗi hàng cao [rowHeight], cuộn bằng con lăn chuột. */
class RowList(val rows: Int, val rowHeight: Int = 11) {
    var scroll = 0
        private set

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
        context.fill(x, y, x + 2, y + height, 0x40000000)
        context.fill(x, barY, x + 2, barY + barHeight, 0xFFAAAAAA.toInt())
    }
}

/** IV và EV của 6 chỉ số. Creative: bấm để ±1, giữ Shift để ±10. */
open class StatsTab(screen: EditorScreen) : EditorTab(screen) {
    override val title: Text = gui("tab.stats")

    protected fun rowY(row: Int) = screen.contentY + 14 + row * 18

    override fun init() {
        val x = screen.contentX
        EDITOR_STATS.indices.forEach { row ->
            val y = rowY(row)
            initIvButtons(row, x, y)
            initEvButtons(row, x, y)
        }
        initFooter(x, screen.contentY + 124)
    }

    protected open fun initIvButtons(row: Int, x: Int, y: Int) {
        val iv = draft.ivs[row]
        screen.button(Text.literal("-"), x + 26, y, 10, 12, gui("hint.step"), active = iv > 0) { changeIv(row, -step) }
        screen.button(Text.literal("+"), x + 50, y, 10, 12, gui("hint.step"), active = iv < MAX_IV) { changeIv(row, step) }
    }

    protected open fun initEvButtons(row: Int, x: Int, y: Int) {
        val ev = draft.evs[row]
        screen.button(Text.literal("-"), x + 70, y, 10, 12, gui("hint.step"), active = ev > 0) { changeEv(row, -step) }
        screen.button(Text.literal("+"), x + 108, y, 10, 12, gui("hint.step"), active = ev < MAX_EV && draft.evs.sum() < MAX_EV_TOTAL) { changeEv(row, step) }
    }

    protected open fun initFooter(x: Int, y: Int) {
        screen.button(gui("button.max_ivs"), x, y, 62, 12) { screen.edit { it.copy(ivs = List(6) { MAX_IV }) } }
        screen.button(gui("button.clear_evs"), x + 66, y, 62, 12) { screen.edit { it.copy(evs = List(6) { 0 }) } }
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
        val x = screen.contentX
        val font = screen.font
        GuiKit.text(context, font, gui("stat"), x, screen.contentY + 2, GuiKit.GRAY, 0.75f)
        GuiKit.centeredText(context, font, Text.literal("IV"), x + 43, screen.contentY + 2, GuiKit.GRAY, 0.75f)
        GuiKit.centeredText(context, font, Text.literal("EV"), x + 94, screen.contentY + 2, GuiKit.GRAY, 0.75f)
        EDITOR_STATS.forEachIndexed { row, stat ->
            val y = rowY(row) + 3
            GuiKit.text(context, font, Text.literal(GuiKit.shortStat(stat)), x, y, scale = 0.75f)
            GuiKit.centeredText(context, font, Text.literal(draft.ivs[row].toString()), ivValueX(x), y, ivColor(row), 0.75f)
            GuiKit.centeredText(context, font, Text.literal(draft.evs[row].toString()), x + 94, y, evColor(row), 0.75f)
        }
        GuiKit.text(context, font, gui("ev_total", draft.evs.sum(), MAX_EV_TOTAL), x, screen.contentY + 117, GuiKit.GRAY, 0.5f)
    }

    protected open fun ivValueX(x: Int) = x + 43
    protected open fun ivColor(row: Int) = changedColor(draft.ivs[row], screen.selectedSlot?.data?.ivs?.get(row))
    protected open fun evColor(row: Int) = changedColor(draft.evs[row], screen.selectedSlot?.data?.evs?.get(row))

    protected fun changedColor(value: Int, original: Int?) = if (original != null && value != original) GuiKit.GOLD else GuiKit.WHITE
}

/** 4 ô chiêu; bấm vào một ô để chọn chiêu khác từ danh sách. */
open class MovesTab(screen: EditorScreen) : EditorTab(screen) {
    override val title: Text = gui("tab.moves")

    protected var pickingSlot = -1
    protected val list = RowList(rows = 10)

    private val listX get() = screen.contentX
    private val listY get() = screen.contentY + 12

    /** Chiêu có thể chọn cho ô đang chọn (bỏ các chiêu đang nằm ở ô khác). */
    protected open fun candidates(): List<String> {
        val options = screen.selectedSlot?.options ?: return emptyList()
        val others = draft.moves.filterIndexed { i, m -> i != pickingSlot && m.isNotEmpty() }
        return options.legalMoves.filter { it !in others }
    }

    /** Có được chọn chiêu này không (survival kiểm tra TM). */
    protected open fun isSelectable(move: String) = true

    /** Nhãn phụ bên phải mỗi hàng (survival: free/TM). */
    protected open fun rowTag(move: String): Pair<Text, Int>? = null

    override fun init() {
        if (pickingSlot >= 0) {
            screen.button(gui("button.back"), screen.contentX, screen.contentY + 126, 40, 12) {
                pickingSlot = -1
                screen.rebuild()
            }
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (pickingSlot >= 0) renderPicker(context, mouseX, mouseY) else renderSlots(context, mouseX, mouseY)
    }

    private fun slotY(i: Int) = screen.contentY + 6 + i * 26

    private fun renderSlots(context: DrawContext, mouseX: Int, mouseY: Int) {
        val x = screen.contentX + 10
        val original = screen.selectedSlot?.data?.moves
        for (i in 0 until MOVE_SLOTS) {
            val y = slotY(i)
            val move = draft.moves[i]
            val hovered = GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), x, y, GuiKit.MOVE_WIDTH, GuiKit.MOVE_HEIGHT)
            GuiKit.moveSlot(context, x, y, if (move.isEmpty()) 0x606060 else GuiKit.moveHue(move), hovered)
            val name = if (move.isEmpty()) gui("empty") else GuiKit.moveName(move)
            val color = if (original != null && original[i] != move) GuiKit.GOLD else GuiKit.WHITE
            GuiKit.text(context, screen.font, name, x + 6, y + 7, color, 0.75f)
            rowTag(move)?.takeIf { original != null && original[i] != move }?.let { (tag, tagColor) ->
                GuiKit.text(context, screen.font, tag, x + 80, y + 8, tagColor, 0.5f)
            }
        }
        GuiKit.text(context, screen.font, gui("hint.moves"), screen.contentX, screen.contentY + 112, GuiKit.GRAY, 0.5f)
    }

    private fun renderPicker(context: DrawContext, mouseX: Int, mouseY: Int) {
        val moves = candidates()
        GuiKit.text(context, screen.font, gui("choose_move", pickingSlot + 1), listX, screen.contentY + 2, GuiKit.GRAY, 0.75f)
        for (index in list.visible(moves.size)) {
            val move = moves[index]
            val y = listY + (index - list.scroll) * list.rowHeight
            if (GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), listX, y, screen.contentWidth - 4, list.rowHeight)) {
                context.fill(listX, y, listX + screen.contentWidth - 4, y + list.rowHeight, GuiKit.ROW_HIGHLIGHT)
            }
            context.fill(listX + 1, y + 2, listX + 7, y + 8, 0xFF000000.toInt() or GuiKit.moveHue(move))
            val color = when {
                move == draft.moves[pickingSlot] -> GuiKit.GOLD
                isSelectable(move) -> GuiKit.WHITE
                else -> GuiKit.RED
            }
            GuiKit.text(context, screen.font, GuiKit.moveName(move), listX + 10, y + 2, color, 0.75f)
            rowTag(move)?.let { (tag, tagColor) -> GuiKit.text(context, screen.font, tag, listX + 100, y + 3, tagColor, 0.5f) }
        }
        list.drawScrollbar(context, listX + screen.contentWidth - 3, listY, moves.size)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (pickingSlot < 0) {
            val x = screen.contentX + 10
            for (i in 0 until MOVE_SLOTS) {
                if (GuiKit.inside(mouseX, mouseY, x, slotY(i), GuiKit.MOVE_WIDTH, GuiKit.MOVE_HEIGHT)) {
                    pickingSlot = i
                    screen.rebuild()
                    return true
                }
            }
            return false
        }
        val moves = candidates()
        val index = list.hit(mouseX, mouseY, listX, listY, screen.contentWidth - 4, moves.size)
        if (index < 0 || !isSelectable(moves[index])) return false
        val slot = pickingSlot
        pickingSlot = -1
        screen.edit { data -> data.copy(moves = data.moves.toMutableList().also { it[slot] = moves[index] }) }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (pickingSlot < 0) return false
        list.scrollBy(amount, candidates().size)
        return true
    }
}

/** Danh sách 25 nature. */
open class NatureTab(screen: EditorScreen) : EditorTab(screen) {
    override val title: Text = gui("tab.nature")

    protected val list = RowList(rows = 11)
    protected val natures: List<String> by lazy {
        Natures.all().map { it.name.toString() }.sortedBy { GuiKit.natureName(it).string }
    }

    private val listX get() = screen.contentX
    private val listY get() = screen.contentY + 12

    /** Nature đang được chọn trong bản nháp. */
    protected open fun chosen(): String = draft.nature

    protected open fun isSelectable(nature: String) = true
    protected open fun rowTag(nature: String): Pair<Text, Int>? = null
    protected open fun choose(nature: String) = screen.edit { it.copy(nature = nature, mintedNature = "") }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        GuiKit.text(context, screen.font, gui("current_nature", GuiKit.natureName(screen.selectedSlot?.data?.effectiveNature ?: "")), listX, screen.contentY + 2, GuiKit.GRAY, 0.75f)
        for (index in list.visible(natures.size)) {
            val nature = natures[index]
            val y = listY + (index - list.scroll) * list.rowHeight
            if (GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), listX, y, screen.contentWidth - 4, list.rowHeight)) {
                context.fill(listX, y, listX + screen.contentWidth - 4, y + list.rowHeight, GuiKit.ROW_HIGHLIGHT)
            }
            val color = when {
                nature == chosen() -> GuiKit.GOLD
                isSelectable(nature) -> GuiKit.WHITE
                else -> GuiKit.RED
            }
            GuiKit.text(context, screen.font, GuiKit.natureName(nature), listX + 2, y + 2, color, 0.75f)
            GuiKit.text(context, screen.font, Text.literal(GuiKit.natureEffect(nature)), listX + 60, y + 3, GuiKit.GRAY, 0.5f)
            rowTag(nature)?.let { (tag, tagColor) -> GuiKit.text(context, screen.font, tag, listX + 100, y + 3, tagColor, 0.5f) }
        }
        list.drawScrollbar(context, listX + screen.contentWidth - 3, listY, natures.size)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val index = list.hit(mouseX, mouseY, listX, listY, screen.contentWidth - 4, natures.size)
        if (index < 0 || !isSelectable(natures[index])) return false
        choose(natures[index])
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        list.scrollBy(amount, natures.size)
        return true
    }
}

/** Shiny, ability, giới tính, level, loại bóng. */
open class OtherTab(screen: EditorScreen) : EditorTab(screen) {
    override val title: Text = gui("tab.other")

    protected fun rowY(row: Int) = screen.contentY + 6 + row * 20

    override fun init() {
        val x = screen.contentX + 58
        val options = screen.selectedSlot?.options ?: return
        initShiny(x, rowY(0))
        if (!creative) return

        screen.button(abilityLabel(draft.ability), x, rowY(1), 70, 14, gui("hint.cycle")) {
            screen.edit { it.copy(ability = cycle(options.abilities, it.ability)) }
        }
        screen.button(gui("gender.${draft.gender.lowercase()}"), x, rowY(2), 70, 14, gui("hint.cycle"), active = options.genders.size > 1) {
            screen.edit { it.copy(gender = cycle(options.genders, it.gender)) }
        }
        screen.button(Text.literal("-"), x, rowY(3), 14, 14, gui("hint.step"), active = draft.level > 1) {
            screen.edit { it.copy(level = (it.level - step).coerceIn(1, screen.state.maxLevel)) }
        }
        screen.button(Text.literal("+"), x + 56, rowY(3), 14, 14, gui("hint.step"), active = draft.level < screen.state.maxLevel) {
            screen.edit { it.copy(level = (it.level + step).coerceIn(1, screen.state.maxLevel)) }
        }
        screen.button(GuiKit.ballName(draft.ball), x, rowY(4), 70, 14, gui("hint.cycle")) {
            screen.edit { it.copy(ball = cycle(screen.state.balls, it.ball)) }
        }
    }

    protected open fun initShiny(x: Int, y: Int) {
        screen.button(yesNo(draft.shiny), x, y, 70, 14) { screen.edit { it.copy(shiny = !it.shiny) } }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val x = screen.contentX
        val labels = listOf("shiny", "ability", "gender", "level", "ball")
        labels.forEachIndexed { row, key ->
            GuiKit.text(context, screen.font, gui("field.$key"), x, rowY(row) + 4, scale = 0.75f)
        }
        if (creative) {
            GuiKit.centeredText(context, screen.font, Text.literal(draft.level.toString()), screen.contentX + 93, rowY(3) + 4, scale = 0.75f)
        } else {
            renderReadOnly(context, x + 58)
        }
    }

    /** Survival: ability, giới tính, level, bóng chỉ hiển thị. */
    private fun renderReadOnly(context: DrawContext, x: Int) {
        val font = screen.font
        GuiKit.text(context, font, abilityLabel(draft.ability), x, rowY(1) + 4, GuiKit.GRAY, 0.75f)
        GuiKit.text(context, font, gui("gender.${draft.gender.lowercase()}"), x, rowY(2) + 4, GuiKit.GRAY, 0.75f)
        GuiKit.text(context, font, Text.literal(draft.level.toString()), x, rowY(3) + 4, GuiKit.GRAY, 0.75f)
        GuiKit.text(context, font, GuiKit.ballName(draft.ball), x, rowY(4) + 4, GuiKit.GRAY, 0.75f)
        GuiKit.text(context, font, gui("survival_locked"), screen.contentX, rowY(5) + 4, GuiKit.GRAY, 0.5f)
    }

    protected fun yesNo(value: Boolean): Text = gui(if (value) "yes" else "no")

    private fun abilityLabel(name: String): Text {
        val template = Abilities.get(name) ?: return Text.literal(name)
        return Text.translatable(template.displayName)
    }

    /** Lấy phần tử kế tiếp (Shift: phần tử trước) trong danh sách. */
    private fun cycle(values: List<String>, current: String): String {
        if (values.isEmpty()) return current
        val index = values.indexOf(current)
        val next = if (Screen.hasShiftDown()) index - 1 else index + 1
        return values[Math.floorMod(next, values.size)]
    }
}
