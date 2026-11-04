package io.github.bubi274.trainersworkshop.client.gui

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/** Màu chữ và các hàm vẽ nhỏ dùng chung, theo phong cách màn hình Summary của Cobblemon. */
object GuiKit {
    const val WHITE = 0xFFFFFF
    const val GRAY = 0xAAAAAA
    const val RED = 0xFF5555
    const val GREEN = 0x55FF55
    const val GOLD = 0xFFAA00

    /** Màu nền ô trong bảng màu xám của Cobblemon. */
    const val SLOT_FILL = 0xFF3D3D3D.toInt()
    const val SLOT_HOVER = 0xFF4B4B4B.toInt()
    const val SELECTED_BORDER = 0xFFFFFFFF.toInt()
    const val ROW_HIGHLIGHT = 0x40FFFFFF

    private fun cobblemon(path: String): Identifier = Identifier.of("cobblemon", "textures/gui/$path")

    val SUMMARY_BASE = cobblemon("summary/summary_base.png")
    val PORTRAIT_BACKGROUND = cobblemon("summary/portrait_background.png")
    val MOVE_SLOT = cobblemon("summary/summary_move.png")
    val SHINY_ICON = cobblemon("summary/icon_shiny.png")

    const val BASE_WIDTH = 331
    const val BASE_HEIGHT = 161
    const val MOVE_WIDTH = 108
    const val MOVE_HEIGHT = 22

    fun text(ctx: DrawContext, renderer: TextRenderer, text: Text, x: Number, y: Number, color: Int = WHITE, scale: Float = 1f) {
        val matrices = ctx.matrices
        matrices.push()
        matrices.translate(x.toDouble(), y.toDouble(), 0.0)
        matrices.scale(scale, scale, 1f)
        ctx.drawText(renderer, text, 0, 0, color, true)
        matrices.pop()
    }

    fun centeredText(ctx: DrawContext, renderer: TextRenderer, text: Text, centerX: Number, y: Number, color: Int = WHITE, scale: Float = 1f) {
        val width = renderer.getWidth(text) * scale
        text(ctx, renderer, text, centerX.toFloat() - width / 2f, y, color, scale)
    }

    // TODO(ui): giả định summary_move.png (108x44) gồm 2 ô 108x22 (thường / khi rê chuột).
    /** Ô chiêu của Cobblemon, tô màu theo hệ. */
    fun moveSlot(ctx: DrawContext, x: Int, y: Int, hue: Int, hovered: Boolean) {
        val r = ((hue shr 16) and 0xFF) / 255f
        val g = ((hue shr 8) and 0xFF) / 255f
        val b = (hue and 0xFF) / 255f
        ctx.setShaderColor(r, g, b, 1f)
        ctx.drawTexture(MOVE_SLOT, x, y, 0f, if (hovered) MOVE_HEIGHT.toFloat() else 0f, MOVE_WIDTH, MOVE_HEIGHT, MOVE_WIDTH, MOVE_HEIGHT * 2)
        ctx.setShaderColor(1f, 1f, 1f, 1f)
    }

    fun moveName(name: String): Text = Moves.getByName(name)?.displayName ?: Text.literal(name)

    fun moveHue(name: String): Int = Moves.getByName(name)?.elementalType?.hue ?: 0x808080

    fun natureName(id: String): Text {
        val nature = Natures.getNature(Identifier.of(id)) ?: return Text.literal(id)
        return Text.translatable(nature.displayName)
    }

    /** Ví dụ "+Atk -SpA", rỗng với nature trung tính. */
    fun natureEffect(id: String): String {
        val nature = Natures.getNature(Identifier.of(id)) ?: return ""
        val up = nature.increasedStat ?: return ""
        val down = nature.decreasedStat ?: return ""
        if (up == down) return ""
        return "+${shortStat(up)} -${shortStat(down)}"
    }

    fun shortStat(stat: Stat): String = when (stat) {
        Stats.HP -> "HP"
        Stats.ATTACK -> "Atk"
        Stats.DEFENCE -> "Def"
        Stats.SPECIAL_ATTACK -> "SpA"
        Stats.SPECIAL_DEFENCE -> "SpD"
        Stats.SPEED -> "Spe"
        else -> "?"
    }

    fun ballName(id: String): Text {
        val parsed = Identifier.tryParse(id) ?: return Text.literal(id)
        return Text.translatable("item.${parsed.namespace}.${parsed.path}")
    }

    fun inside(mouseX: Double, mouseY: Double, x: Int, y: Int, width: Int, height: Int): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
}
