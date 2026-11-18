package io.github.bubi274.trainersworkshop.client.gui

import com.cobblemon.mod.common.api.types.tera.TeraTypes
import io.github.bubi274.trainersworkshop.editor.TrainerEditing
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Trainer Editor tab for battle gimmicks of the selected Pokémon: Tera type, Dynamax, Gigantamax and held item
 * (Mega Evolution comes from a held Mega Stone with Mega Showdown). Each button is a server action, so the
 * server keeps RCT's gimmick data and the Pokémon in sync.
 */
class BattleTab(screen: EditorScreen) : InfoRowsTab(screen) {
    override val title: Text = gui("tab.battle")
    override val icon: Identifier = GuiKit.TAB_BATTLE

    private val slot get() = screen.trainer?.slots?.getOrNull(screen.selectedIndex)

    private fun send(action: String, fill: net.minecraft.nbt.NbtCompound.() -> Unit) {
        val index = screen.selectedIndex
        screen.trainerAction(action) {
            putInt("index", index)
            fill()
        }
    }

    override fun init() {
        val view = slot ?: return
        val gimmicks = view.gimmicks
        val teraChoices = listOf("") + (screen.trainer?.teraTypes ?: emptyList())

        // Tera: Off, or the type this Pokémon Terastallizes into. Click for the next type, Shift-click for the previous one.
        screen.button(teraName(gimmicks.tera), cellX + 2, rowY(0) + 1, cellW - 4, 9, gui("hint.tera")) {
            val index = teraChoices.indexOf(gimmicks.tera).coerceAtLeast(0)
            val next = if (Screen.hasShiftDown()) index - 1 else index + 1
            val tera = teraChoices[Math.floorMod(next, teraChoices.size)]
            send(TrainerEditing.SET_TERA) { putString("tera", tera) }
        }
        screen.button(yesNo(gimmicks.dynamax), cellX + 2, rowY(1) + 1, cellW - 4, 9, gui("hint.dynamax")) {
            send(TrainerEditing.SET_DYNAMAX) { putBoolean("value", !gimmicks.dynamax) }
        }
        val gmaxTip = if (view.canGmax) gui("hint.gmax") else gui("hint.gmax_unavailable")
        screen.button(yesNo(gimmicks.gmax), cellX + 2, rowY(2) + 1, cellW - 4, 9, gmaxTip, active = view.canGmax || gimmicks.gmax) {
            send(TrainerEditing.SET_GMAX) { putBoolean("value", !gimmicks.gmax) }
        }
        screen.button(gui("button.held_from_hand"), cellX + 2, rowY(4) + 1, 46, 9, gui("hint.held_from_hand")) {
            send(TrainerEditing.HELD_FROM_HAND) {}
        }
        screen.button(gui("button.held_clear"), cellX + 50, rowY(4) + 1, 27, 9, gui("hint.held_clear"), active = !view.heldItem.isEmpty) {
            send(TrainerEditing.HELD_CLEAR) {}
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val view = slot ?: return
        val font = screen.font
        drawLabel(context, 0, gui("field.tera"))
        drawLabel(context, 1, gui("field.dynamax"))
        drawLabel(context, 2, gui("field.gmax"), if (view.canGmax) GuiKit.WHITE else GuiKit.DIM)
        drawLabel(context, 4, gui("field.held"))

        // Row 5: the held item (icon + name), then notes in the light area below.
        val y = rowY(5) + 1
        if (view.heldItem.isEmpty) {
            GuiKit.text(context, font, gui("held_none"), cellX + 4, y, GuiKit.DIM, maxWidth = cellW - 8, minScale = GuiKit.MEDIUM)
        } else {
            val matrices = context.matrices
            matrices.push()
            matrices.translate((cellX + 3).toDouble(), y.toDouble() - 0.5, 0.0)
            matrices.scale(0.5f, 0.5f, 1f)
            context.drawItem(view.heldItem, 0, 0)
            matrices.pop()
            GuiKit.text(context, font, view.heldItem.name, cellX + 13, y, GuiKit.WHITE, maxWidth = cellW - 16, minScale = GuiKit.SMALL)
        }
        GuiKit.textWrapped(context, font, gui("battle_note"), screen.panelX + 8, zoneY + 2, 118, 6, GuiKit.GRAY)
    }

    private fun teraName(tera: String): Text {
        if (tera.isEmpty()) return gui("tera.off")
        return TeraTypes.get(tera)?.displayName ?: Text.literal(tera)
    }

    private fun yesNo(value: Boolean): Text = gui(if (value) "yes" else "no")
}
