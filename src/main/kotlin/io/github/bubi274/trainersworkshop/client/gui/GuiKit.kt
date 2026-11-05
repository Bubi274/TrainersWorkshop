package io.github.bubi274.trainersworkshop.client.gui

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.client.CobblemonResources
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.text.MutableText
import net.minecraft.text.OrderedText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier

/**
 * Màu, texture và hàm vẽ dùng chung, theo màn hình Summary của Cobblemon 1.7.3.
 *
 * Dùng lại của Cobblemon: texture trong textures/gui/summary, font CobblemonResources.DEFAULT_LARGE
 * (chữ đậm vẽ ở tỉ lệ 0.5 như Summary), các icon TypeIcon / MoveCategoryIcon, SummaryTab.
 * Vẽ lại: nút (cắt 9 mảnh từ summary_evolve_button), ô party, khung phụ ở chân màn hình.
 */
object GuiKit {
    const val WHITE = 0xFFFFFF
    const val GRAY = 0xAAAAAA
    const val DIM = 0x8A8A8A
    const val RED = 0xFF5555
    const val GREEN = 0x55FF55
    const val GOLD = 0xFFAA00
    /** Màu chỉ số tăng / giảm theo nature (đỏ / xanh). */
    const val STAT_UP = 0xFF6B6B
    const val STAT_DOWN = 0x6BB5FF
    const val MALE = 0x32CAFF
    const val FEMALE = 0xFC5454
    const val CREATIVE_TAG = 0xC77DFF
    const val SURVIVAL_TAG = 0x55FF55

    /** Bảng xám của Cobblemon (đo từ summary_info_base.png). */
    const val DARK = 0xFF2F2F2F.toInt()
    const val LIGHT = 0xFF4B4B4B.toInt()
    const val FRAME = 0xFF676767.toInt()
    const val EDGE = 0xFF8D8D8D.toInt()
    const val SLOT_HOVER_OVERLAY = 0x30FFFFFF
    const val ROW_HIGHLIGHT = 0x40FFFFFF
    const val SELECTED_FILL = 0xFF6E6E6E.toInt()

    const val BASE_WIDTH = 331
    const val BASE_HEIGHT = 161
    /** Dải phụ vẽ dưới khung Summary: danh sách vật phẩm (Cost) và thông báo. */
    const val FOOTER_HEIGHT = 38
    const val TOTAL_HEIGHT = BASE_HEIGHT + FOOTER_HEIGHT

    /** Panel giữa của Summary (InfoWidget / MovesWidget / StatWidget đều đặt ở (77, 12), cỡ 134x148). */
    const val PANEL_X = 77
    const val PANEL_Y = 12
    const val PANEL_W = 134
    const val PANEL_H = 148

    const val MOVE_WIDTH = 108
    const val MOVE_HEIGHT = 22

    const val PARTY_SLOT_W = 46
    const val PARTY_SLOT_H = 27

    /**
     * Hai tầng chữ như Summary: chữ chính dùng font DEFAULT_LARGE đậm ở scale 1.0
     * (drawScaledText của Cobblemon để mặc định scale = 1), chữ phụ dùng font vanilla ở scale 0.5.
     */
    const val TEXT = 1.0f
    const val MEDIUM = 0.75f
    const val SMALL = 0.5f

    private fun cobblemon(path: String): Identifier = Identifier.of("cobblemon", "textures/gui/$path")
    private fun own(path: String): Identifier = Identifier.of("trainers_workshop", "textures/gui/$path")

    val SUMMARY_BASE = cobblemon("summary/summary_base.png")
    val PORTRAIT_BACKGROUND = cobblemon("summary/portrait_background.png")
    val INFO_BASE = cobblemon("summary/summary_info_base.png")
    val MOVES_BASE = cobblemon("summary/summary_moves_base.png")
    val MOVE_SLOT = cobblemon("summary/summary_move.png")
    val MOVE_OVERLAY = cobblemon("summary/summary_move_overlay.png")
    val PARTY_SLOT = cobblemon("summary/summary_party_slot.png")
    val PARTY_SLOT_EMPTY = cobblemon("summary/summary_party_slot_empty.png")
    val PARTY_GENDER_MALE = cobblemon("party/party_gender_male.png")
    val PARTY_GENDER_FEMALE = cobblemon("party/party_gender_female.png")
    val BUTTON = cobblemon("summary/summary_evolve_button.png")
    val TYPE_SPACER = cobblemon("summary/type_spacer.png")
    val TYPE_SPACER_DOUBLE = cobblemon("summary/type_spacer_double.png")
    val SHINY_ICON = cobblemon("summary/icon_shiny.png")
    val STAT_UP_ICON = cobblemon("summary/summary_stats_icon_increase.png")
    val STAT_DOWN_ICON = cobblemon("summary/summary_stats_icon_decrease.png")
    val TAB_STATS = cobblemon("summary/summary_tab_icon_stats.png")
    val TAB_MOVES = cobblemon("summary/summary_tab_icon_moves.png")
    val TAB_INFO = cobblemon("summary/summary_tab_icon_info.png")
    /** Icon tab tự vẽ 17x17 cùng phong cách (Cobblemon không có sẵn). */
    val TAB_NATURE = own("tab_icon_nature.png")
    val TAB_OTHER = own("tab_icon_other.png")

    // ---------- Chữ ----------

    private fun styled(s: String, bold: Boolean, small: Boolean): MutableText =
        if (small) Text.literal(s) else Text.literal(s).setStyle(Style.EMPTY.withFont(CobblemonResources.DEFAULT_LARGE).withBold(bold))

    /** Chữ đã vừa khung: nội dung, tỉ lệ vẽ và bề rộng (px màn hình). */
    class Fitted(val text: Text, val scale: Float, val width: Float)

    /**
     * Làm chữ vừa [maxWidth] px: thu nhỏ dần tới [minScale] rồi mới cắt bằng "…".
     * Đo bằng đúng font và độ đậm sẽ vẽ nên không bao giờ tràn.
     * [small] = chữ phụ (font vanilla, scale 0.5); mặc định là chữ chính (DEFAULT_LARGE đậm, scale 1.0).
     */
    fun fit(
        font: TextRenderer, raw: Text, maxWidth: Float, scale: Float = Float.NaN, bold: Boolean = true,
        minScale: Float = Float.NaN, small: Boolean = false,
    ): Fitted {
        val base = if (scale.isNaN()) (if (small) SMALL else TEXT) else scale
        val floor = if (minScale.isNaN()) base else minScale
        val s = raw.string
        var current = base
        while (true) {
            val t = styled(s, bold, small)
            val w = font.getWidth(t) * current
            if (w <= maxWidth) return Fitted(t, current, w)
            if (current - 0.05f >= floor - 0.001f) current -= 0.05f else break
        }
        val limit = maxWidth / current
        var n = s.length
        while (n > 0) {
            n--
            val candidate = styled(s.substring(0, n).trimEnd() + "…", bold, small)
            val w = font.getWidth(candidate)
            if (w <= limit) return Fitted(candidate, current, w * current)
        }
        return Fitted(styled("", bold, small), current, 0f)
    }

    private fun draw(ctx: DrawContext, font: TextRenderer, fitted: Fitted, x: Float, y: Float, color: Int, shadow: Boolean) {
        if (fitted.width <= 0f) return
        val matrices = ctx.matrices
        matrices.push()
        matrices.translate(x.toDouble(), y.toDouble(), 0.0)
        matrices.scale(fitted.scale, fitted.scale, 1f)
        ctx.drawText(font, fitted.text, 0, 0, color, shadow)
        matrices.pop()
    }

    /** Chữ căn trái; không vượt [maxWidth]. Trả về bề rộng đã vẽ. */
    fun text(
        ctx: DrawContext, font: TextRenderer, text: Text, x: Number, y: Number, color: Int = WHITE,
        scale: Float = Float.NaN, maxWidth: Int = Int.MAX_VALUE, bold: Boolean = true, shadow: Boolean = true,
        minScale: Float = Float.NaN, small: Boolean = false,
    ): Float {
        val fitted = fit(font, text, maxWidth.toFloat(), scale, bold, minScale, small)
        draw(ctx, font, fitted, x.toFloat(), y.toFloat(), color, shadow)
        return fitted.width
    }

    fun textCentered(
        ctx: DrawContext, font: TextRenderer, text: Text, centerX: Number, y: Number, color: Int = WHITE,
        scale: Float = Float.NaN, maxWidth: Int = Int.MAX_VALUE, bold: Boolean = true, shadow: Boolean = true,
        minScale: Float = Float.NaN, small: Boolean = false,
    ) {
        val fitted = fit(font, text, maxWidth.toFloat(), scale, bold, minScale, small)
        draw(ctx, font, fitted, centerX.toFloat() - fitted.width / 2f, y.toFloat(), color, shadow)
    }

    fun textRight(
        ctx: DrawContext, font: TextRenderer, text: Text, rightX: Number, y: Number, color: Int = WHITE,
        scale: Float = Float.NaN, maxWidth: Int = Int.MAX_VALUE, bold: Boolean = true, shadow: Boolean = true,
        minScale: Float = Float.NaN, small: Boolean = false,
    ) {
        val fitted = fit(font, text, maxWidth.toFloat(), scale, bold, minScale, small)
        draw(ctx, font, fitted, rightX.toFloat() - fitted.width, y.toFloat(), color, shadow)
    }

    fun textWidth(font: TextRenderer, text: Text, scale: Float = TEXT, bold: Boolean = true, small: Boolean = false): Float =
        font.getWidth(styled(text.string, bold, small)) * scale

    /** Chữ phụ nhiều dòng trong [maxWidth] px, tối đa [maxLines] dòng (font vanilla, scale 0.5). Trả về số dòng đã vẽ. */
    fun textWrapped(
        ctx: DrawContext, font: TextRenderer, text: Text, x: Int, y: Int, maxWidth: Int, maxLines: Int,
        color: Int = GRAY, scale: Float = SMALL, lineHeight: Int = 6,
    ): Int {
        val lines: List<OrderedText> = font.wrapLines(styled(text.string, false, true), (maxWidth / scale).toInt())
        lines.take(maxLines).forEachIndexed { i, line ->
            val matrices = ctx.matrices
            matrices.push()
            matrices.translate(x.toDouble(), (y + i * lineHeight).toDouble(), 0.0)
            matrices.scale(scale, scale, 1f)
            ctx.drawText(font, line, 0, 0, color, true)
            matrices.pop()
        }
        return minOf(lines.size, maxLines)
    }

    // ---------- Khung, thanh, ô ----------

    /** Khung nổi của Cobblemon (viền sáng, thân xám, lõi tối) cho các vùng không có texture riêng. */
    fun panel(ctx: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        ctx.fill(x, y, x + w, y + h, EDGE)
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, FRAME)
        ctx.fill(x + 4, y + 4, x + w - 4, y + h - 4, LIGHT)
    }

    /** Ô lõm tối (ô nhãn, thanh nền). */
    fun inset(ctx: DrawContext, x: Int, y: Int, w: Int, h: Int, fill: Int = DARK) {
        ctx.fill(x, y, x + w, y + h, fill)
    }

    fun bar(ctx: DrawContext, x: Int, y: Int, w: Int, h: Int, value: Int, max: Int, color: Int) {
        ctx.fill(x, y, x + w, y + h, DARK)
        if (max <= 0 || value <= 0) return
        val filled = (w * value.coerceAtMost(max) / max.toFloat()).toInt().coerceAtLeast(1)
        ctx.fill(x, y, x + filled, y + h, 0xFF000000.toInt() or color)
    }

    /** Vẽ một hình chữ nhật của texture, kéo giãn kiểu 9 mảnh (viền [cap] px giữ nguyên). */
    fun nineSlice(ctx: DrawContext, tex: Identifier, x: Int, y: Int, w: Int, h: Int, v: Int, texW: Int, texH: Int, frameW: Int, frameH: Int, cap: Int) {
        val cx = minOf(cap, w / 2)
        val cy = minOf(cap, h / 2)
        val srcX = intArrayOf(0, cx, frameW - cx)
        val srcW = intArrayOf(cx, frameW - 2 * cx, cx)
        val dstX = intArrayOf(x, x + cx, x + w - cx)
        val dstW = intArrayOf(cx, w - 2 * cx, cx)
        val srcY = intArrayOf(0, cy, frameH - cy)
        val srcH = intArrayOf(cy, frameH - 2 * cy, cy)
        val dstY = intArrayOf(y, y + cy, y + h - cy)
        val dstH = intArrayOf(cy, h - 2 * cy, cy)
        for (row in 0..2) {
            if (dstH[row] <= 0 || srcH[row] <= 0) continue
            for (col in 0..2) {
                if (dstW[col] <= 0 || srcW[col] <= 0) continue
                ctx.drawTexture(tex, dstX[col], dstY[row], dstW[col], dstH[row], srcX[col].toFloat(), (v + srcY[row]).toFloat(), srcW[col], srcH[row], texW, texH)
            }
        }
    }

    // TODO(ui): giả định summary_move.png (108x44) gồm 2 ô 108x22 (thường / khi rê chuột); đã khớp kích thước, cần xem trong game.
    /** Ô chiêu của Cobblemon, tô màu theo hệ (đúng cách MoveSlotWidget làm), phủ summary_move_overlay lên trên. */
    fun moveSlot(ctx: DrawContext, x: Int, y: Int, hue: Int, hovered: Boolean) {
        val r = ((hue shr 16) and 0xFF) / 255f
        val g = ((hue shr 8) and 0xFF) / 255f
        val b = (hue and 0xFF) / 255f
        ctx.setShaderColor(r, g, b, 1f)
        ctx.drawTexture(MOVE_SLOT, x, y, 0f, if (hovered) MOVE_HEIGHT.toFloat() else 0f, MOVE_WIDTH, MOVE_HEIGHT, MOVE_WIDTH, MOVE_HEIGHT * 2)
        ctx.setShaderColor(1f, 1f, 1f, 1f)
        ctx.drawTexture(MOVE_OVERLAY, x, y, 0f, 0f, MOVE_WIDTH, MOVE_HEIGHT, MOVE_WIDTH, MOVE_HEIGHT)
    }

    private val itemCache = HashMap<String, ItemStack>()

    /** Vẽ icon vật phẩm (ví dụ Poké Ball) thu nhỏ về [size] px. */
    fun itemIcon(ctx: DrawContext, id: String, x: Int, y: Int, size: Int = 8) {
        val stack = itemCache.getOrPut(id) {
            val parsed = Identifier.tryParse(id)
            val item: Item = if (parsed == null) net.minecraft.item.Items.AIR else Registries.ITEM.get(parsed)
            ItemStack(item)
        }
        if (stack.isEmpty) return
        val matrices = ctx.matrices
        matrices.push()
        matrices.translate(x.toDouble(), y.toDouble(), 0.0)
        val s = size / 16f
        matrices.scale(s, s, 1f)
        ctx.drawItem(stack, 0, 0)
        matrices.pop()
    }

    // ---------- Dữ liệu ----------

    fun moveName(name: String): Text = Moves.getByName(name)?.displayName ?: Text.literal(name)

    fun moveHue(name: String): Int = Moves.getByName(name)?.elementalType?.hue ?: 0x808080

    fun natureName(id: String): Text {
        val nature = Natures.getNature(Identifier.of(id)) ?: return Text.literal(id)
        return Text.translatable(nature.displayName)
    }

    /** Chỉ số tăng / giảm của nature; null nếu trung tính. */
    fun natureStats(id: String): Pair<Stat, Stat>? {
        val nature = Natures.getNature(Identifier.of(id)) ?: return null
        val up = nature.increasedStat ?: return null
        val down = nature.decreasedStat ?: return null
        if (up == down) return null
        return Pair(up, down)
    }

    /** Ví dụ "+Atk -SpA", rỗng với nature trung tính. */
    fun natureEffect(id: String): String {
        val (up, down) = natureStats(id) ?: return ""
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

    fun longStat(stat: Stat): String = when (stat) {
        Stats.HP -> "HP"
        Stats.ATTACK -> "Attack"
        Stats.DEFENCE -> "Defense"
        Stats.SPECIAL_ATTACK -> "Sp. Atk"
        Stats.SPECIAL_DEFENCE -> "Sp. Def"
        Stats.SPEED -> "Speed"
        else -> "?"
    }

    fun ballName(id: String): Text {
        val parsed = Identifier.tryParse(id) ?: return Text.literal(id)
        return Text.translatable("item.${parsed.namespace}.${parsed.path}")
    }

    /** Texture bóng ở góc ô party: "textures/gui/ball/<tên bóng>.png" (cỡ 18x44, vẽ nửa trên 18x22). */
    fun ballGuiTexture(id: String): Identifier {
        val parsed = Identifier.tryParse(id) ?: Identifier.of("cobblemon", "poke_ball")
        return Identifier.of(parsed.namespace, "textures/gui/ball/${parsed.path}.png")
    }

    /**
     * Định dạng tooltip chuẩn vanilla (font mặc định, scale 1, tự xuống dòng ở 170 px):
     * dòng đầu màu trắng (tên vật phẩm hoặc hành động), các dòng sau màu xám (ví dụ "Have: 15").
     */
    fun wrapTooltip(font: TextRenderer, raw: Text, maxWidth: Int = 170): List<OrderedText> {
        val s = raw.string.trim()
        if (s.isEmpty()) return emptyList()
        val paragraphs = if ('\n' in s) {
            s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            val dot = s.indexOf(". ")
            if (dot >= 0) listOf(s.substring(0, dot + 1).trim(), s.substring(dot + 2).trim()).filter { it.isNotEmpty() }
            else listOf(s)
        }
        val out = mutableListOf<OrderedText>()
        paragraphs.forEachIndexed { idx, part ->
            val style = Style.EMPTY
                .withFont(Style.DEFAULT_FONT_ID)
                .withBold(false)
                .withColor(if (idx == 0) Formatting.WHITE else Formatting.GRAY)
            out += font.wrapLines(Text.literal(part).setStyle(style), maxWidth)
        }
        return out
    }

    fun wrapTooltipLines(font: TextRenderer, lines: List<Text>, maxWidth: Int = 170): List<OrderedText> {
        val out = mutableListOf<OrderedText>()
        lines.forEachIndexed { idx, line ->
            val baseColor = line.style.color
            val style = Style.EMPTY
                .withFont(Style.DEFAULT_FONT_ID)
                .withBold(false)
                .let { if (baseColor != null) it.withColor(baseColor) else it.withColor(if (idx == 0) Formatting.WHITE else Formatting.GRAY) }
            out += font.wrapLines(Text.literal(line.string).setStyle(style), maxWidth)
        }
        return out
    }

    fun inside(mouseX: Double, mouseY: Double, x: Int, y: Int, width: Int, height: Int): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
}
