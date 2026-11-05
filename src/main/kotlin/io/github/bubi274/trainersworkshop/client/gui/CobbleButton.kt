package io.github.bubi274.trainersworkshop.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text

/**
 * Nút kiểu Cobblemon: dùng texture summary_evolve_button (54x30: trên = thường, dưới = rê chuột / đang chọn),
 * cắt 9 mảnh để co giãn theo mọi kích thước. Vẽ lại vì SummaryButton của Cobblemon chỉ vẽ ở cỡ cố định.
 */
class CobbleButton(
    x: Int, y: Int, w: Int, h: Int,
    private val label: Text,
    private val onPress: () -> Unit,
) : ClickableWidget(x, y, w, h, label) {
    /** Nút đang được chọn (ví dụ trang IV / EV hiện tại): vẽ khung nhấn. */
    var selected = false
    /** Màu chữ riêng (ví dụ đỏ khi thiếu vật phẩm); null = tự động theo trạng thái. */
    var labelColor: Int? = null
    /** Vẽ icon 8x8 ở bên trái nhãn (toạ độ góc trên trái của icon). */
    var icon: ((DrawContext, Int, Int) -> Unit)? = null
    /** Tooltip của nút; được EditorScreen gom và vẽ ở cuối hàm render() để nằm đè lên mọi model/chữ. */
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
        // Nút cao từ 11 px: chữ chính scale 1.0 như Summary; nút thấp hơn: scale 0.75 cho vừa chiều cao.
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
