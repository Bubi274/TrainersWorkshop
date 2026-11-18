package io.github.bubi274.trainersworkshop.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text

/**
 * A Cobblemon-style button: uses the summary_evolve_button texture (54x30: top = normal, bottom = hovered / selected),
 * cut into 9 slices so it stretches to any size. Drawn by hand because Cobblemon's SummaryButton only draws at a fixed size.
 */
class CobbleButton(
    x: Int, y: Int, w: Int, h: Int,
    private val label: Text,
    private val onPress: () -> Unit,
) : ClickableWidget(x, y, w, h, label) {
    /** The button is selected (for example the current IV / EV page): draws the pressed frame. */
    var selected = false
    /** Custom label color (for example red when an item is missing); null = automatic, based on the state. */
    var labelColor: Int? = null
    /** Draws an 8x8 icon to the left of the label (top-left corner coordinates of the icon). */
    var icon: ((DrawContext, Int, Int) -> Unit)? = null
    /** The button's tooltip; EditorScreen collects it and draws it at the end of render() so it sits on top of every model/text. */
    var tooltipText: Text? = null

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val pressedLook = selected || (active && isHovered)
        if (!active) context.setShaderColor(0.55f, 0.55f, 0.55f, 1f)
        GuiKit.nineSlice(context, GuiKit.BUTTON, x, y, width, height, if (pressedLook) 15 else 0, 54, 30, 54, 15, 4)
        context.setShaderColor(1f, 1f, 1f, 1f)

        val font = MinecraftClient.getInstance().textRenderer
        val iconSpace = if (icon != null) 10 else 0
        val color = labelColor ?: when {
            !active -> 0x9A9A9A
            else -> GuiKit.WHITE
        }
        // Buttons 11 px or taller: main text at scale 1.0 like Summary; shorter buttons: scale 0.75 to fit the height.
        val base = if (height >= 11) GuiKit.TEXT else GuiKit.MEDIUM
        val room = (width - (if (width <= 12) 2 else 4) - iconSpace).toFloat()
        val fitted = GuiKit.fit(font, label, room, scale = base, minScale = base * 0.75f)
        val textY = y + (height - 8 * fitted.scale) / 2f
        val centerX = x + (width + iconSpace) / 2f
        GuiKit.textCentered(context, font, label, centerX, textY, color, scale = base, maxWidth = room.toInt(), minScale = base * 0.75f)
        icon?.let { draw ->
            draw(context, (centerX - fitted.width / 2f).toInt() - 10, y + (height - 8) / 2)
        }
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        appendDefaultNarrations(builder)
    }

    override fun onClick(mouseX: Double, mouseY: Double) {
        onPress()
    }
}
