package io.github.bubi274.trainersworkshop.client.gui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.SummaryTab
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.client.ClientEditor
import io.github.bubi274.trainersworkshop.editor.EditorActions
import io.github.bubi274.trainersworkshop.editor.EditorMode
import io.github.bubi274.trainersworkshop.editor.EditorSlot
import io.github.bubi274.trainersworkshop.editor.EditorState
import io.github.bubi274.trainersworkshop.editor.PokemonEditData
import io.github.bubi274.trainersworkshop.editor.SurvivalCosts
import io.github.bubi274.trainersworkshop.editor.TrainerEditing
import io.github.bubi274.trainersworkshop.editor.readStringList
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.max

/**
 * Màn hình chỉnh sửa dùng chung cho PokeEditor (creative/survival) và Trainer Editor.
 * Bố cục theo màn hình Summary của Cobblemon: trái = Pokémon đang chọn, giữa = các tab icon, phải = đội hình + nút.
 * Dưới khung Summary có thêm một dải phụ (Cost / thông báo / ô nhập loài của Trainer Editor).
 */
class EditorScreen(state: EditorState) : Screen(Text.translatable("screen.${TrainersWorkshop.MOD_ID}.editor")) {
    var state: EditorState = state
        private set

    /** Ô đội hình đang chọn (chỉ số 0..5). */
    var selectedIndex: Int = state.slots.firstOrNull()?.index ?: -1
        private set

    /** Bản nháp đang chỉnh của Pokémon đang chọn; chỉ gửi lên server khi bấm Apply. */
    var draft: PokemonEditData? = state.slot(selectedIndex)?.data
        private set

    private var status: NbtCompound? = null
    private val tabs: List<EditorTab> = createTabs()
    private var activeTab: EditorTab = tabs.first()
    private var modelWidget: ModelWidget? = null
    private var modelKey: String = ""

    /** Model nhỏ trong từng ô party (giữ lại giữa các lần rebuild để animation không bị giật). */
    private val slotModels = HashMap<Int, Pair<String, ModelWidget>>()

    val mode: EditorMode get() = state.mode
    val selectedSlot: EditorSlot? get() = state.slot(selectedIndex)
    val left: Int get() = (width - GuiKit.BASE_WIDTH) / 2
    val top: Int get() = max(4, (height - GuiKit.TOTAL_HEIGHT) / 2)

    /** Panel giữa của Summary. */
    val panelX: Int get() = left + GuiKit.PANEL_X
    val panelY: Int get() = top + GuiKit.PANEL_Y

    /** Vùng nội dung tự do của tab (trong khung panel, chừa viền 5 px). */
    val contentX: Int get() = panelX + 5
    val contentY: Int get() = panelY + 5
    val contentWidth: Int = 124
    val contentHeight: Int = 138

    val font: TextRenderer get() = textRenderer

    val isDirty: Boolean get() = draft != null && draft != selectedSlot?.data

    private fun createTabs(): List<EditorTab> = when (state.mode) {
        EditorMode.SURVIVAL -> listOf(SurvivalStatsTab(this), SurvivalMovesTab(this), SurvivalNatureTab(this), SurvivalOtherTab(this))
        EditorMode.TRAINER -> listOf(StatsTab(this), MovesTab(this), NatureTab(this), OtherTab(this), LootTab(this))
        EditorMode.CREATIVE -> listOf(StatsTab(this), MovesTab(this), NatureTab(this), OtherTab(this))
    }

    /** Trainer Editor: thông tin trainer và đồ rơi (giải mã một lần mỗi khi server gửi trạng thái). */
    var trainer: TrainerView? = null
        private set

    /** Ô nhập tên loài (Trainer Editor), giữ lại khi các nút được tạo lại. */
    private var speciesInput = ""

    private fun decodeTrainer(): TrainerView? {
        val nbt = state.trainer ?: return null
        val registries = client?.world?.registryManager ?: return null
        return TrainerView.from(nbt, registries)
    }

    /** Gửi một hành động của Trainer Editor; gửi trước các chỉnh sửa Pokémon chưa lưu vào bản nháp. */
    fun trainerAction(action: String, fill: NbtCompound.() -> Unit) {
        pushPendingEdit()
        ClientEditor.send(action, fill)
    }

    private fun pushPendingEdit() {
        val data = draft
        if (mode != EditorMode.TRAINER || data == null || !isDirty) return
        ClientEditor.send(TrainerEditing.EDIT) {
            putInt("index", selectedIndex)
            put("data", data.toNbt())
        }
    }

    /** Survival: vật phẩm cần cho bản nháp hiện tại, tính bằng túi đồ phía client (server tính lại khi Apply). */
    fun survivalCost(): SurvivalCosts.Result? {
        if (mode != EditorMode.SURVIVAL) return null
        val slot = selectedSlot ?: return null
        val data = draft ?: return null
        val player = client?.player ?: return null
        return SurvivalCosts.compute(slot.data, data, slot.options) { player.inventory.count(it) }
    }

    fun hasProblem(field: String): Boolean = survivalCost()?.problems?.contains(field) == true

    fun haveCount(item: net.minecraft.item.Item): Int = client?.player?.inventory?.count(item) ?: 0

    /** Server gửi trạng thái mới (sau Apply hoặc khi đội hình thay đổi). */
    fun update(newState: EditorState, newStatus: NbtCompound?) {
        state = newState
        status = newStatus
        trainer = decodeTrainer()
        if (state.slot(selectedIndex) == null) selectedIndex = state.slots.firstOrNull()?.index ?: -1
        draft = state.slot(selectedIndex)?.data
        rebuild()
    }

    fun select(index: Int) {
        if (state.slot(index) == null || index == selectedIndex) return
        // PokeEditor: thay đổi chưa Apply của Pokémon cũ bị bỏ. Trainer Editor: đưa vào bản nháp của server.
        pushPendingEdit()
        tabs.forEach { it.reset() }
        selectedIndex = index
        draft = state.slot(index)?.data
        status = null
        rebuild()
    }

    /** Sửa bản nháp rồi vẽ lại các nút. */
    fun edit(change: (PokemonEditData) -> PokemonEditData) {
        val current = draft ?: return
        draft = change(current)
        rebuild()
    }

    fun switchTab(tab: EditorTab) {
        activeTab.reset()
        activeTab = tab
        rebuild()
    }

    fun <T : ClickableWidget> addWidget(widget: T): T = addDrawableChild(widget)

    /** Đưa focus bàn phím vào một ô nhập (ví dụ ô tìm chiêu). */
    fun focusWidget(widget: ClickableWidget) {
        focused = widget
    }

    fun button(label: Text, x: Int, y: Int, w: Int, h: Int, tooltip: Text? = null, active: Boolean = true, onPress: () -> Unit): CobbleButton {
        val button = CobbleButton(x, y, w, h, label, onPress)
        if (tooltip != null) button.setTooltip(Tooltip.of(tooltip))
        button.active = active
        return addWidget(button)
    }

    override fun init() {
        rebuild()
    }

    fun rebuild() {
        // Chưa init (chưa biết kích thước màn hình): init() sẽ gọi lại.
        if (client == null) return
        clearChildren()
        initTabs()
        if (draft != null || !activeTab.needsPokemon) activeTab.init()
        if (mode == EditorMode.TRAINER) initTrainerPanel() else initRightPanel()
        updateModel()
        updateSlotModels()
    }

    /** 4 tab icon như Summary: tab đầu x=78, mỗi tab cách 31 px (nhiều tab hơn thì xếp sát lại cho vừa 132 px). */
    private fun initTabs() {
        val step = if (tabs.size <= 1) 0 else minOf(31, (132 - 39) / (tabs.size - 1))
        tabs.forEachIndexed { i, tab ->
            val widget = SummaryTab(left + 78 + i * step, top - 1, tab.title.copy(), tab.icon, ButtonWidget.PressAction { switchTab(tab) })
            widget.toggleTab(tab == activeTab)
            addDrawableChild(widget)
        }
    }

    private fun initRightPanel() {
        val y = top + 143
        button(Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cancel"), left + 222, y, 46, 14) { cancel() }
        button(Text.translatable("gui.${TrainersWorkshop.MOD_ID}.apply"), left + 273, y, 46, 14, active = canApply()) { apply() }
    }

    /** Trainer Editor: ô nhập loài + Set/Add/Remove ở dải chân, Reset / Close / Save ở góc phải. */
    private fun initTrainerPanel() {
        if (trainer == null) trainer = decodeTrainer()
        val footerY = top + GuiKit.BASE_HEIGHT + 6
        val field = TextFieldWidget(textRenderer, left + 8, footerY, 112, 12, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.species"))
        field.setMaxLength(64)
        field.text = speciesInput
        field.setPlaceholder(Text.translatable("gui.${TrainersWorkshop.MOD_ID}.species.placeholder"))
        field.setChangedListener { speciesInput = it }
        addWidget(field)
        val gui = { key: String -> Text.translatable("gui.${TrainersWorkshop.MOD_ID}.$key") }
        button(gui("button.set_species"), left + 124, footerY, 30, 12, gui("hint.set_species"), active = selectedSlot != null) {
            trainerAction(TrainerEditing.SET_SPECIES) {
                putInt("index", selectedIndex)
                putString("species", speciesInput)
            }
        }
        button(gui("button.add"), left + 156, footerY, 30, 12, gui("hint.add_pokemon"), active = state.slots.size < 6) {
            trainerAction(TrainerEditing.ADD) { putString("species", speciesInput) }
        }
        button(gui("button.remove"), left + 188, footerY, 36, 12, gui("hint.remove_pokemon"), active = state.slots.size > 1 && selectedSlot != null) {
            trainerAction(TrainerEditing.REMOVE) { putInt("index", selectedIndex) }
        }
        val y = top + 143
        button(gui("button.reset"), left + 222, y, 32, 14, gui("hint.reset")) { trainerAction(TrainerEditing.RESET) {} }
        button(gui("button.close"), left + 257, y, 32, 14) { close() }
        button(gui("button.save"), left + 292, y, 32, 14, gui("hint.save")) { trainerAction(TrainerEditing.SAVE) {} }
    }

    fun canApply(): Boolean = isDirty && (mode != EditorMode.SURVIVAL || survivalCost()?.ok == true)

    private fun cancel() {
        if (isDirty) {
            draft = selectedSlot?.data
            status = null
            rebuild()
        } else {
            close()
        }
    }

    private fun apply() {
        val data = draft ?: return
        ClientEditor.send(EditorActions.APPLY) {
            putInt("index", selectedIndex)
            put("data", data.toNbt())
        }
    }

    // ---------- Model Pokémon ----------

    private fun renderable(data: PokemonEditData): RenderablePokemon? {
        val species = PokemonSpecies.getByIdentifier(Identifier.of(data.species)) ?: return null
        val aspects = data.aspects.toMutableSet()
        // TODO(api): giả định aspect shiny của Cobblemon tên là "shiny" (để xem trước model shiny).
        if (data.shiny) aspects.add("shiny") else aspects.remove("shiny")
        return RenderablePokemon(species, aspects, ItemStack.EMPTY)
    }

    private fun modelKey(data: PokemonEditData): String = "${data.uuid}|${data.species}|${(data.aspects + if (data.shiny) listOf("shiny") else listOf()).sorted()}"

    private fun updateModel() {
        val data = draft
        val pokemon = data?.let { renderable(it) }
        if (data == null || pokemon == null) {
            modelWidget = null
            modelKey = ""
            return
        }
        val key = modelKey(data)
        if (key != modelKey || modelWidget == null) {
            // Cùng thông số với Summary: ModelWidget(6, 32, 66, 66, pokemon, 2.0, 325.0, -10.0).
            modelWidget = ModelWidget(
                pX = left + 6, pY = top + 32, pWidth = 66, pHeight = 66,
                pokemon = pokemon,
                baseScale = 2f, rotationY = 325f, offsetY = -10.0,
            )
            modelKey = key
        }
        modelWidget?.let { addDrawableChild(it) }
    }

    // TODO(ui): baseScale / offsetY của model nhỏ trong ô party suy ra từ Summary (66 px -> 18 px), cần xem trong game.
    private fun updateSlotModels() {
        for (position in 0 until 6) {
            val slot = state.slot(position)
            val data = if (position == selectedIndex) draft else slot?.data
            val pokemon = data?.let { renderable(it) }
            if (data == null || pokemon == null) {
                slotModels.remove(position)
                continue
            }
            val (x, y) = partySlotPos(position)
            val key = modelKey(data)
            val cached = slotModels[position]
            val widget = if (cached != null && cached.first == key) cached.second else {
                ModelWidget(
                    pX = x + 1, pY = y + 8, pWidth = 18, pHeight = 18,
                    pokemon = pokemon,
                    baseScale = 0.55f, rotationY = 325f, offsetY = -3.0,
                    playCryOnClick = false, shouldFollowCursor = false,
                ).also { slotModels[position] = Pair(key, it) }
            }
            addDrawableChild(widget)
        }
    }

    // ---------- Vẽ ----------

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderBackground(context, mouseX, mouseY, delta)
        // Thứ tự như Summary: nền ảnh Pokémon, rồi khung chính.
        context.drawTexture(GuiKit.PORTRAIT_BACKGROUND, left + 6, top + 32, 0f, 0f, 66, 66, 66, 66)
        context.drawTexture(GuiKit.SUMMARY_BASE, left, top, 0f, 0f, GuiKit.BASE_WIDTH, GuiKit.BASE_HEIGHT, GuiKit.BASE_WIDTH, GuiKit.BASE_HEIGHT)
        if (draft != null || !activeTab.needsPokemon) activeTab.renderBackdrop(context) else GuiKit.panel(context, panelX, panelY, GuiKit.PANEL_W, GuiKit.PANEL_H)
        renderPartySlots(context, mouseX, mouseY, backgroundPass = true)
        renderFooterFrame(context)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Texture ô party vẽ ở renderBackground (dưới model); ở đây vẽ các lớp chữ trên widget.
        super.render(context, mouseX, mouseY, delta)
        renderLeftPanel(context)
        renderPartySlots(context, mouseX, mouseY, backgroundPass = false)
        renderModeLabel(context)
        renderFooter(context, mouseX, mouseY)
        if (draft != null || !activeTab.needsPokemon) {
            activeTab.render(context, mouseX, mouseY, delta)
        } else {
            GuiKit.textCentered(context, font, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.no_pokemon"), panelX + GuiKit.PANEL_W / 2, panelY + 70, GuiKit.GRAY)
        }
        renderPartyTooltip(context, mouseX, mouseY)
    }

    /** Khung trái giống Summary: tab "Lv.xx", dòng tên (bóng + tên + giới tính), dấu shiny, icon hệ. */
    private fun renderLeftPanel(context: DrawContext) {
        val data = draft ?: return
        val species = PokemonSpecies.getByIdentifier(Identifier.of(data.species)) ?: return
        val levelText = Text.translatable("gui.${TrainersWorkshop.MOD_ID}.level", data.level)
        GuiKit.text(context, font, levelText, left + 6, top + 4.5f, maxWidth = 48, minScale = GuiKit.MEDIUM)
        GuiKit.itemIcon(context, data.ball, left + 4, top + 15, 8)
        GuiKit.text(context, font, species.translatedName, left + 13, top + 14.5f, maxWidth = 54, minScale = GuiKit.MEDIUM)
        genderSymbol(data.gender)?.let { (symbol, color) ->
            GuiKit.text(context, font, Text.literal(symbol), left + 69, top + 14.5f, color)
        }
        if (data.shiny) {
            context.drawTexture(GuiKit.SHINY_ICON, left + 63, top + 34, 8, 8, 0f, 0f, 16, 16, 16, 16)
        }
        val form = species.getForm(data.aspects.toSet())
        val spacer = if (form.secondaryType != null) GuiKit.TYPE_SPACER_DOUBLE else GuiKit.TYPE_SPACER
        context.drawTexture(spacer, left + 6, top + 126, 66, 12, 0f, 0f, 132, 24, 132, 24)
        TypeIcon(x = left + 39, y = top + 123, type = form.primaryType, secondaryType = form.secondaryType, centeredX = true).render(context)
    }

    private fun genderSymbol(gender: String): Pair<String, Int>? = when (gender.uppercase()) {
        "MALE" -> Pair("♂", GuiKit.MALE)
        "FEMALE" -> Pair("♀", GuiKit.FEMALE)
        else -> null
    }

    // ---------- Ô party ----------

    // Toạ độ lấy từ PartyWidget của Cobblemon: widget ở (216, 24); ô đầu ở (+6, +7); cột phải lệch (+51, +8); hàng cách 32.
    private fun partySlotPos(position: Int): Pair<Int, Int> {
        val odd = position % 2 == 1
        return Pair(left + 222 + if (odd) 51 else 0, top + 31 + (position / 2) * 32 + if (odd) 8 else 0)
    }

    private fun slotData(position: Int): PokemonEditData? = if (position == selectedIndex) draft else state.slot(position)?.data

    private fun renderPartySlots(context: DrawContext, mouseX: Int, mouseY: Int, backgroundPass: Boolean) {
        for (position in 0 until 6) {
            val (x, y) = partySlotPos(position)
            val data = slotData(position)
            if (backgroundPass) {
                if (data == null) {
                    context.drawTexture(GuiKit.PARTY_SLOT_EMPTY, x, y, 0f, 0f, GuiKit.PARTY_SLOT_W, GuiKit.PARTY_SLOT_H, GuiKit.PARTY_SLOT_W, 54)
                } else {
                    val v = if (position == selectedIndex) GuiKit.PARTY_SLOT_H.toFloat() else 0f
                    context.drawTexture(GuiKit.PARTY_SLOT, x, y, 0f, v, GuiKit.PARTY_SLOT_W, GuiKit.PARTY_SLOT_H, GuiKit.PARTY_SLOT_W, 54)
                    if (position != selectedIndex && GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), x, y, GuiKit.PARTY_SLOT_W, GuiKit.PARTY_SLOT_H)) {
                        context.fill(x + 1, y + 1, x + GuiKit.PARTY_SLOT_W - 1, y + GuiKit.PARTY_SLOT_H - 1, GuiKit.SLOT_HOVER_OVERLAY)
                    }
                }
                continue
            }
            if (data == null) continue
            renderSlotInfo(context, x, y, data)
        }
    }

    /** Tên + giới tính (trên), icon bóng (góc model), level + thanh HP (dưới phải). */
    private fun renderSlotInfo(context: DrawContext, x: Int, y: Int, data: PokemonEditData) {
        val species = PokemonSpecies.getByIdentifier(Identifier.of(data.species))
        val name = species?.translatedName ?: Text.literal(data.species)
        GuiKit.text(context, font, name, x + 4, y + 3, maxWidth = 33, minScale = 0.4f, small = true)
        genderSymbol(data.gender)?.let { (symbol, color) -> GuiKit.text(context, font, Text.literal(symbol), x + 38, y + 3, color, small = true) }
        GuiKit.itemIcon(context, data.ball, x + 1, y + 18, 8)
        if (data.shiny) context.drawTexture(GuiKit.SHINY_ICON, x + 12, y + 18, 8, 8, 0f, 0f, 16, 16, 16, 16)
        GuiKit.text(context, font, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.level", data.level), x + 21, y + 11, maxWidth = 24, minScale = 0.4f, small = true)
        val hp = clientPokemon(data)
        if (hp != null) {
            val max = hp.maxHealth.coerceAtLeast(1)
            val current = hp.currentHealth.coerceIn(0, max)
            val ratio = current / max.toFloat()
            val color = when {
                ratio > 0.5f -> 0x55D955
                ratio > 0.2f -> 0xE6C533
                else -> 0xE64545
            }
            GuiKit.bar(context, x + 21, y + 21, 23, 3, current, max, color)
        }
    }

    /** Pokémon cùng UUID trong đội hình phía client (để lấy HP); null với Trainer Editor. */
    private fun clientPokemon(data: PokemonEditData) =
        if (mode == EditorMode.TRAINER) null else CobblemonClient.storage.party.findByUUID(data.uuid)

    private fun renderPartyTooltip(context: DrawContext, mouseX: Int, mouseY: Int) {
        for (position in 0 until 6) {
            val (x, y) = partySlotPos(position)
            if (!GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), x, y, GuiKit.PARTY_SLOT_W, GuiKit.PARTY_SLOT_H)) continue
            val data = slotData(position) ?: return
            val species = PokemonSpecies.getByIdentifier(Identifier.of(data.species))
            val lines = mutableListOf<Text>(species?.translatedName ?: Text.literal(data.species))
            lines += Text.translatable("gui.${TrainersWorkshop.MOD_ID}.level", data.level)
            clientPokemon(data)?.let { lines += Text.literal("HP ${it.currentHealth} / ${it.maxHealth}") }
            context.drawTooltip(font, lines, mouseX, mouseY)
            return
        }
    }

    fun partyTitle(): Text {
        val view = trainer ?: return Text.translatable("gui.${TrainersWorkshop.MOD_ID}.party")
        val key = if (view.customized) "trainer.edited" else "trainer.default"
        return Text.translatable("gui.${TrainersWorkshop.MOD_ID}.$key", view.name)
    }

    /** Nhãn chế độ ở đầu khung phải: CREATIVE (tím) / SURVIVAL (xanh); Trainer Editor hiện tên trainer. */
    private fun renderModeLabel(context: DrawContext) {
        val (text, color) = when (mode) {
            EditorMode.CREATIVE -> Pair(Text.translatable("gui.${TrainersWorkshop.MOD_ID}.mode.creative"), GuiKit.CREATIVE_TAG)
            EditorMode.SURVIVAL -> Pair(Text.translatable("gui.${TrainersWorkshop.MOD_ID}.mode.survival"), GuiKit.SURVIVAL_TAG)
            EditorMode.TRAINER -> Pair(partyTitle(), GuiKit.GOLD)
        }
        val x = left + 222
        val y = top + 13
        val width = GuiKit.fit(font, text, 94f, scale = GuiKit.MEDIUM, minScale = 0.6f).width.toInt()
        context.fill(x - 2, y - 2, x + width + 3, y + 8, GuiKit.DARK)
        context.drawBorder(x - 2, y - 2, width + 5, 10, 0xFF000000.toInt() or color)
        GuiKit.text(context, font, text, x, y, color, scale = GuiKit.MEDIUM, maxWidth = 94, minScale = 0.6f)
    }

    // ---------- Dải chân: Cost + thông báo ----------

    private val footerTop: Int get() = top + GuiKit.BASE_HEIGHT

    private fun renderFooterFrame(context: DrawContext) {
        GuiKit.panel(context, left, footerTop, GuiKit.BASE_WIDTH, GuiKit.FOOTER_HEIGHT)
        GuiKit.inset(context, left + 4, footerTop + 4, 224, GuiKit.FOOTER_HEIGHT - 8)
        GuiKit.inset(context, left + 232, footerTop + 4, GuiKit.BASE_WIDTH - 236, GuiKit.FOOTER_HEIGHT - 8)
    }

    private fun renderFooter(context: DrawContext, mouseX: Int, mouseY: Int) {
        renderStatus(context)
        if (mode == EditorMode.SURVIVAL) renderCost(context, mouseX, mouseY)
    }

    /** Thông báo kết quả từ server, bên phải dải chân (tối đa 3 dòng, dòng dài bị cắt). */
    private fun renderStatus(context: DrawContext) {
        val x = left + 236
        val y = footerTop + 7
        val width = GuiKit.BASE_WIDTH - 244
        val statusNbt = status
        if (statusNbt == null) {
            if (mode == EditorMode.TRAINER) return
            val hint = Text.translatable("gui.${TrainersWorkshop.MOD_ID}.footer.hint.${if (isDirty) "dirty" else "clean"}")
            GuiKit.textWrapped(context, font, hint, x, y, width, 3, GuiKit.DIM)
            return
        }
        val args = readStringList(statusNbt, "args").map { Text.literal(it) }.toTypedArray<Any>()
        val message = Text.translatable(statusNbt.getString("key"), *args)
        val color = if (statusNbt.getBoolean("error")) GuiKit.RED else GuiKit.GREEN
        GuiKit.textWrapped(context, font, message, x, y, width, 3, color)
    }

    /** Survival: vật phẩm sẽ bị trừ, đỏ nếu thiếu. Tối đa 2 cột x 4 dòng; phần dư xem trong tooltip. */
    private fun renderCost(context: DrawContext, mouseX: Int, mouseY: Int) {
        val cost = survivalCost() ?: return
        val x = left + 8
        val y = footerTop + 6
        GuiKit.text(context, font, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.title"), x, y, GuiKit.GOLD, scale = GuiKit.MEDIUM, maxWidth = 40, minScale = 0.6f)
        val listX = left + 50
        val colWidth = 87
        if (cost.isEmpty) {
            GuiKit.text(context, font, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.none"), listX, y, GuiKit.GRAY, maxWidth = 2 * colWidth, small = true)
            return
        }
        val entries = cost.needed.entries.toList()
        val lines = mutableListOf<Pair<String, Int>>()
        for ((item, count) in entries) {
            val have = haveCount(item)
            val color = if (have >= count) GuiKit.WHITE else GuiKit.RED
            lines += Pair(Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.line", count, item.name, have).string, color)
        }
        // Vật phẩm của mod tuỳ chọn chưa cài (Only Bottle Caps, TMCraft).
        cost.lines.filter { it.item == null }.map { it.missingItemId ?: it.field }.distinct().forEach { id ->
            lines += Pair(Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.unavailable", id).string, GuiKit.RED)
        }
        cost.error?.let { lines += Pair(Text.translatable("message.${TrainersWorkshop.MOD_ID}.editor.$it").string, GuiKit.RED) }

        val capacity = 8
        val shown = if (lines.size > capacity) lines.take(capacity - 1) else lines
        shown.forEachIndexed { i, (line, color) ->
            val column = i / 4
            val row = i % 4
            GuiKit.text(context, font, Text.literal(line), listX + column * colWidth, y + row * 7, color, maxWidth = colWidth - 4, minScale = 0.4f, small = true)
        }
        val hidden = lines.size - shown.size
        if (hidden > 0) {
            val i = shown.size
            GuiKit.text(context, font, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.more", hidden), listX + (i / 4) * colWidth, y + (i % 4) * 7, GuiKit.GRAY, maxWidth = colWidth - 4, small = true)
            if (GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), left + 4, footerTop + 4, 224, GuiKit.FOOTER_HEIGHT - 8)) {
                context.drawTooltip(font, lines.map { Text.literal(it.first) }, mouseX, mouseY)
            }
        }
    }

    // ---------- Chuột ----------

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        for (position in 0 until 6) {
            val (x, y) = partySlotPos(position)
            if (GuiKit.inside(mouseX, mouseY, x, y, GuiKit.PARTY_SLOT_W, GuiKit.PARTY_SLOT_H) && state.slot(position) != null) {
                select(position)
                return true
            }
        }
        if ((draft != null || !activeTab.needsPokemon) && activeTab.mouseClicked(mouseX, mouseY, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if ((draft != null || !activeTab.needsPokemon) && activeTab.mouseScrolled(mouseX, mouseY, verticalAmount)) return true
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun shouldPause(): Boolean = false

    override fun removed() {
        ClientEditor.send(EditorActions.CLOSE) {}
        super.removed()
    }
}
