package io.github.bubi274.trainersworkshop.client.gui

import io.github.bubi274.trainersworkshop.editor.LootEntry
import io.github.bubi274.trainersworkshop.editor.LootMode
import io.github.bubi274.trainersworkshop.editor.TrainerEditing
import io.github.bubi274.trainersworkshop.editor.enumValueOrNull
import io.github.bubi274.trainersworkshop.editor.readCompoundList
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryWrapper
import net.minecraft.text.Text
import kotlin.math.roundToInt

/** Thông tin trainer server gửi xuống (tên, đồ rơi...). */
data class TrainerView(
    val name: String,
    val trainerId: String,
    val defaultLootTable: String,
    val customized: Boolean,
    val lootMode: LootMode,
    val keepDefaultLoot: Boolean,
    val loot: List<LootEntry>,
) {
    companion object {
        fun from(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup) = TrainerView(
            name = nbt.getString("name"),
            trainerId = nbt.getString("trainerId"),
            defaultLootTable = nbt.getString("defaultLootTable"),
            customized = nbt.getBoolean("customized"),
            lootMode = enumValueOrNull<LootMode>(nbt.getString("lootMode")) ?: LootMode.EVERY_WIN,
            keepDefaultLoot = nbt.getBoolean("keepDefaultLoot"),
            loot = readCompoundList(nbt, "loot").mapNotNull { LootEntry.fromNbt(registries, it) },
        )
    }
}

/** Tab đồ rơi của Trainer Editor: vật phẩm, số lượng, tỉ lệ; rơi mỗi lần thắng hay chỉ lần đầu. */
class LootTab(screen: EditorScreen) : EditorTab(screen) {
    override val title: Text = gui("tab.loot")
    override val needsPokemon = false

    private val rows = 5
    private val rowHeight = 16
    private var first = 0

    private val trainer get() = screen.trainer
    private fun rowY(i: Int) = screen.contentY + 40 + i * rowHeight

    override fun init() {
        val view = trainer ?: return
        val x = screen.contentX
        val y = screen.contentY
        val modeKey = if (view.lootMode == LootMode.EVERY_WIN) "loot.mode.every_win" else "loot.mode.first_win"
        screen.button(gui(modeKey), x, y, screen.contentWidth, 12, gui("hint.loot_mode")) {
            val next = if (view.lootMode == LootMode.EVERY_WIN) LootMode.FIRST_WIN_PER_PLAYER else LootMode.EVERY_WIN
            screen.trainerAction(TrainerEditing.LOOT_MODE) { putString("mode", next.name) }
        }
        screen.button(gui(if (view.keepDefaultLoot) "loot.keep_default" else "loot.replace_default"), x, y + 14, screen.contentWidth, 12, gui("hint.loot_default")) {
            screen.trainerAction(TrainerEditing.LOOT_KEEP_DEFAULT) { putBoolean("keep", !view.keepDefaultLoot) }
        }

        first = first.coerceIn(0, maxOf(0, view.loot.size - rows))
        for (i in 0 until rows) {
            val index = first + i
            val entry = view.loot.getOrNull(index) ?: break
            val ry = rowY(i) + 2
            val chance = (entry.chance * 100).roundToInt()
            screen.button(Text.literal("-"), x + 30, ry, 10, 12, gui("hint.step"), active = entry.stack.count > 1) {
                setEntry(index, entry.stack.count - step, chance)
            }
            screen.button(Text.literal("+"), x + 42, ry, 10, 12, gui("hint.step"), active = entry.stack.count < entry.stack.maxCount) {
                setEntry(index, entry.stack.count + step, chance)
            }
            screen.button(Text.literal("-"), x + 80, ry, 10, 12, gui("hint.chance_step"), active = chance > 1) {
                setEntry(index, entry.stack.count, chance - chanceStep())
            }
            screen.button(Text.literal("+"), x + 92, ry, 10, 12, gui("hint.chance_step"), active = chance < 100) {
                setEntry(index, entry.stack.count, chance + chanceStep())
            }
            screen.button(Text.literal("x"), x + 108, ry, 14, 12, gui("hint.loot_remove")) {
                screen.trainerAction(TrainerEditing.LOOT_REMOVE) { putInt("index", index) }
            }
        }
        screen.button(gui("button.add_hand_item"), x, y + 124, screen.contentWidth, 12, gui("hint.add_hand_item"), active = view.loot.size < LootEntry.MAX_ENTRIES) {
            screen.trainerAction(TrainerEditing.LOOT_ADD_HAND) {}
        }
    }

    /** Tỉ lệ: bấm ±5%, giữ Shift ±1%. */
    private fun chanceStep() = if (Screen.hasShiftDown()) 1 else 5

    private fun setEntry(index: Int, count: Int, chancePercent: Int) {
        screen.trainerAction(TrainerEditing.LOOT_SET) {
            putInt("index", index)
            putInt("count", count)
            putFloat("chance", chancePercent.coerceIn(1, 100) / 100f)
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val view = trainer ?: return
        val x = screen.contentX
        val font = screen.font
        val defaultTable = view.defaultLootTable.ifEmpty { "-" }
        GuiKit.text(context, font, gui("loot.default_table", defaultTable), x, screen.contentY + 29, GuiKit.GRAY, maxWidth = screen.contentWidth, small = true)
        if (view.loot.isEmpty()) {
            GuiKit.text(context, font, gui("loot.empty"), x, rowY(0) + 4, GuiKit.GRAY, maxWidth = screen.contentWidth)
            return
        }
        for (i in 0 until rows) {
            val entry = view.loot.getOrNull(first + i) ?: break
            val y = rowY(i)
            context.drawItem(entry.stack, x, y)
            GuiKit.text(context, font, Text.literal("x${entry.stack.count}"), x + 17, y + 4, scale = GuiKit.MEDIUM, maxWidth = 12, minScale = 0.5f)
            GuiKit.textCentered(context, font, Text.literal("${(entry.chance * 100).roundToInt()}%"), x + 67, y + 4, maxWidth = 26)
            if (GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), x, y, 16, 16)) {
                screen.deferTooltip(entry.stack.name)
            }
        }
        if (view.loot.size > rows) {
            GuiKit.text(context, font, gui("loot.scroll", first + 1, minOf(first + rows, view.loot.size), view.loot.size), x, rowY(rows) + 1, GuiKit.GRAY, maxWidth = screen.contentWidth, small = true)
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val size = trainer?.loot?.size ?: return false
        if (size <= rows) return false
        first = (first - if (amount > 0) 1 else -1).coerceIn(0, size - rows)
        screen.rebuild()
        return true
    }
}
