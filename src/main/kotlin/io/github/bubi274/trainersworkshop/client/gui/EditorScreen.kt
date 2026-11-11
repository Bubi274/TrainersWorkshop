package io.github.bubi274.trainersworkshop.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.SummaryTab
import com.cobblemon.mod.common.client.render.getDepletableRedGreen
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.client.ClientEditor
import io.github.bubi274.trainersworkshop.compat.rct.RctCompat
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
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.joml.Quaternionf
import org.joml.Vector3f
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

    /** FloatingState cho từng ô party (giữ lại giữa các lần rebuild để animation không bị giật, giống PartySlotWidget). */
    private val slotStates = HashMap<Int, Pair<String, FloatingState>>()
    private val buttons = mutableListOf<CobbleButton>()
    private val tabWidgets = mutableListOf<Pair<SummaryTab, Text>>()
    private var pendingTooltip: List<OrderedText>? = null
    private var lastInventoryHash: Int = 0

    /**
     * Survival: level tối đa được lên = nhỏ hơn giữa level cap RCT của người chơi và level tối đa của Cobblemon.
     * Cap RCT đọc ở client bằng PlayerState (chỉ để hiển thị và khoá nút; server luôn tính lại bằng TrainerManager).
     */
    var levelCap: Int = state.maxLevel
        private set

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
        return SurvivalCosts.compute(slot.data, data, slot.options, levelCap) { player.inventory.count(it) }
    }

    fun hasProblem(field: String): Boolean = survivalCost()?.problems?.contains(field) == true

    fun haveCount(item: net.minecraft.item.Item): Int = client?.player?.inventory?.count(item) ?: 0

    /** Server gửi trạng thái mới (sau Apply hoặc khi đội hình thay đổi). */
    fun update(newState: EditorState, newStatus: NbtCompound?) {
        state = newState
        refreshLevelCap()
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
        button.tooltipText = tooltip
        button.active = active
        buttons += button
        return addWidget(button)
    }

    /** Đưa một tooltip vào hàng chờ để vẽ ở cuối hàm render() (mỗi lần render chỉ vẽ tối đa 1 tooltip). */
    fun deferTooltip(text: Text) {
        if (pendingTooltip == null) pendingTooltip = GuiKit.wrapTooltip(font, text)
    }

    fun deferTooltipLines(lines: List<Text>) {
        if (pendingTooltip == null && lines.isNotEmpty()) pendingTooltip = GuiKit.wrapTooltipLines(font, lines)
    }

    private fun computeInventoryHash(): Int {
        val player = client?.player ?: return 0
        return SurvivalCosts.inventoryHash(selectedSlot?.options) { player.inventory.count(it) }
    }

    /** Cập nhật [levelCap]; trả về true nếu giá trị thay đổi. */
    private fun refreshLevelCap(): Boolean {
        if (mode != EditorMode.SURVIVAL) return false
        val player = client?.player
        val cap = SurvivalCosts.levelLimit(state.maxLevel, player?.let { RctCompat.clientLevelCap(it) })
        if (cap == levelCap) return false
        levelCap = cap
        return true
    }

    override fun init() {
        refreshLevelCap()
        rebuild()
    }

    override fun tick() {
        super.tick()
        val capChanged = refreshLevelCap()
        val currentHash = computeInventoryHash()
        if (capChanged || currentHash != lastInventoryHash) {
            lastInventoryHash = currentHash
            rebuild()
        }
    }

    fun rebuild() {
        // Chưa init (chưa biết kích thước màn hình): init() sẽ gọi lại.
        if (client == null) return
        lastInventoryHash = computeInventoryHash()
        clearChildren()
        buttons.clear()
        tabWidgets.clear()
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
            val widget = SummaryTab(left + 78 + i * step, top - 1, null, tab.icon, ButtonWidget.PressAction { switchTab(tab) })
            widget.toggleTab(tab == activeTab)
            tabWidgets += Pair(widget, tab.title.copy())
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
        if (data.shiny) aspects.add("shiny") else aspects.remove("shiny")
        when (data.gender.uppercase()) {
            "MALE" -> {
                aspects.remove("female")
                aspects.add("male")
            }
            "FEMALE" -> {
                aspects.remove("male")
                aspects.add("female")
            }
            else -> {
                aspects.remove("male")
                aspects.remove("female")
            }
        }
        val heldItem = clientPokemon(data)?.let { if (it.heldItemVisible) it.heldItem() else ItemStack.EMPTY } ?: heldItemStack(data)
        return RenderablePokemon(species, aspects, heldItem)
    }

    private fun heldItemStack(data: PokemonEditData): ItemStack {
        clientPokemon(data)?.let {
            val stack = it.heldItem()
            if (!stack.isEmpty) return stack
        }
        if (data.heldItemId.isEmpty() || data.heldItemCount <= 0) return ItemStack.EMPTY
        val id = Identifier.tryParse(data.heldItemId) ?: return ItemStack.EMPTY
        val item = Registries.ITEM.get(id)
        if (item == Items.AIR) return ItemStack.EMPTY
        return ItemStack(item, data.heldItemCount)
    }

    private fun modelKey(data: PokemonEditData): String =
        "${data.uuid}|${data.species}|${data.gender}|${(data.aspects + if (data.shiny) listOf("shiny") else listOf()).sorted()}"

    private fun updateModel() {
        val data = draft
        val pokemon = data?.let { renderable(it) }
        if (data == null || pokemon == null) {
            modelWidget = null
            modelKey = ""
            return
        }
        val key = modelKey(data)
        val current = modelWidget
        val widget = if (key != modelKey || current == null) {
            // Khớp Summary.init(): ModelWidget(left+6, top+32, 66, 66, pokemon, 2f, 325f, -10.0, playCryOnClick=false, shouldFollowCursor=true)
            // và gọi setPokemon để đăng ký các hàm MoLang trên FloatingState như Summary.updatePokemonInfo().
            ModelWidget(
                pX = left + 6, pY = top + 32, pWidth = 66, pHeight = 66,
                pokemon = pokemon,
                baseScale = 2f, rotationY = 325f, offsetY = -10.0,
                playCryOnClick = false, shouldFollowCursor = true,
            ).also {
                it.pokemon = pokemon
                modelWidget = it
                modelKey = key
            }
        } else {
            current.x = left + 6
            current.y = top + 32
            current
        }
        addDrawableChild(widget)
    }

    /** Cập nhật FloatingState cho từng ô party giống PartySlotWidget. */
    private fun updateSlotModels() {
        for (position in 0 until 6) {
            val data = slotData(position)
            val pokemon = data?.let { renderable(it) }
            if (data == null || pokemon == null) {
                slotStates.remove(position)
                continue
            }
            val key = modelKey(data)
            val cached = slotStates[position]
            if (cached == null || cached.first != key) {
                val state = FloatingState().also { it.currentAspects = pokemon.aspects }
                slotStates[position] = Pair(key, state)
            } else {
                cached.second.currentAspects = pokemon.aspects
            }
        }
    }

    // ---------- Vẽ ----------

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderBackground(context, mouseX, mouseY, delta)
        // Thứ tự như Summary: nền ảnh Pokémon, rồi khung chính.
        context.drawTexture(GuiKit.PORTRAIT_BACKGROUND, left + 6, top + 32, 0f, 0f, 66, 66, 66, 66)
        context.drawTexture(GuiKit.SUMMARY_BASE, left, top, 0f, 0f, GuiKit.BASE_WIDTH, GuiKit.BASE_HEIGHT, GuiKit.BASE_WIDTH, GuiKit.BASE_HEIGHT)
        if (draft != null || !activeTab.needsPokemon) activeTab.renderBackdrop(context) else GuiKit.panel(context, panelX, panelY, GuiKit.PANEL_W, GuiKit.PANEL_H)
        renderPartySlots(context, mouseX, mouseY, delta, backgroundPass = true)
        renderFooterFrame(context)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        pendingTooltip = null
        // Texture nền và model 3D vẽ ở renderBackground / super.render; ở đây vẽ các lớp chữ/icon và đẩy Z như Summary.render.
        super.render(context, mouseX, mouseY, delta)
        val matrices = context.matrices
        matrices.push()
        matrices.translate(0.0, 0.0, 1000.0)
        renderLeftPanel(context, mouseX, mouseY)
        renderPartySlots(context, mouseX, mouseY, delta, backgroundPass = false)
        renderModeLabel(context)
        renderFooter(context, mouseX, mouseY)
        if (draft != null || !activeTab.needsPokemon) {
            activeTab.render(context, mouseX, mouseY, delta)
        } else {
            GuiKit.textCentered(context, font, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.no_pokemon"), panelX + GuiKit.PANEL_W / 2, panelY + 70, GuiKit.GRAY)
        }
        for (btn in buttons) {
            val tip = btn.tooltipText
            if (btn.visible && tip != null && btn.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
                deferTooltip(tip)
                break
            }
        }
        for ((tabWidget, title) in tabWidgets) {
            if (tabWidget.visible && tabWidget.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
                deferTooltip(title)
                break
            }
        }
        renderPartyTooltip(mouseX, mouseY)
        pendingTooltip?.takeIf { it.isNotEmpty() }?.let { lines ->
            matrices.push()
            matrices.translate(0.0, 0.0, 500.0)
            context.drawOrderedTooltip(font, lines, mouseX, mouseY)
            matrices.pop()
        }
        matrices.pop()
    }

    /** Khung trái giống Summary: tab "Lv.xx", dòng tên (bóng + tên + giới tính), dấu shiny, ô Held Item, icon hệ. */
    private fun renderLeftPanel(context: DrawContext, mouseX: Int, mouseY: Int) {
        val data = draft ?: return
        val species = PokemonSpecies.getByIdentifier(Identifier.of(data.species)) ?: return
        GuiKit.text(context, font, Text.literal("Lv."), left + 6, top + 4.5f, scale = 1f, bold = true, shadow = true)
        GuiKit.text(context, font, Text.literal(data.level.toString()), left + 19, top + 4.5f, scale = 1f, bold = true, shadow = true)
        GuiKit.itemIcon(context, data.ball, left + 4, top + 15, 8)
        GuiKit.text(context, font, species.translatedName, left + 13, top + 14.5f, maxWidth = 54, minScale = GuiKit.MEDIUM)
        genderSymbol(data.gender)?.let { (symbol, color) ->
            GuiKit.text(context, font, Text.literal(symbol), left + 69, top + 14.5f, color)
        }
        if (data.shiny) {
            context.drawTexture(GuiKit.SHINY_ICON, left + 63, top + 34, 8, 8, 0f, 0f, 16, 16, 16, 16)
        }
        val heldStack = heldItemStack(data)
        if (!heldStack.isEmpty) {
            context.drawItem(heldStack, left + 3, top + 104)
            context.drawItemInSlot(font, heldStack, left + 3, top + 104)
            if (GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), left + 3, top + 104, 16, 16)) {
                val c = client
                if (c != null) deferTooltipLines(getTooltipFromItem(c, heldStack))
                else deferTooltip(heldStack.name)
            }
        }
        GuiKit.text(
            context, font, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.held_item"),
            left + 24, top + 114.5f,
            scale = 0.5f, minScale = 0.5f, bold = false, shadow = true, small = true,
        )
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

    private fun renderPartySlots(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float, backgroundPass: Boolean) {
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
                    renderSlotModel(context, position, x, y, data, delta)
                }
                continue
            }
            if (data == null) continue
            renderSlotInfo(context, x, y, data)
        }
    }

    /** Vẽ model Pokémon trong ô party đúng theo PartySlotWidget: dịch tới (x+12.5, y-3), scale 2.5, góc xoay (13, 35, 0), scale 4.5. */
    private fun renderSlotModel(context: DrawContext, position: Int, x: Int, y: Int, data: PokemonEditData, delta: Float) {
        val pokemon = renderable(data) ?: return
        val key = modelKey(data)
        val cached = slotStates[position]
        val state = if (cached != null && cached.first == key) {
            cached.second.also { it.currentAspects = pokemon.aspects }
        } else {
            FloatingState().also {
                it.currentAspects = pokemon.aspects
                slotStates[position] = Pair(key, it)
            }
        }
        val matrices = context.matrices
        matrices.push()
        matrices.translate(x + 12.5, y - 3.0, 0.0)
        matrices.scale(2.5f, 2.5f, 1f)
        drawProfilePokemon(
            renderablePokemon = pokemon,
            matrixStack = matrices,
            rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13f, 35f, 0f)),
            state = state,
            partialTicks = delta,
            scale = 4.5f,
        )
        matrices.pop()
    }

    /**
     * Vẽ các thành phần của ô party theo đúng thông số PartySlotWidget của Cobblemon 1.7.3:
     * - Bóng bắt: "textures/gui/ball/<tên bóng>.png" tại (x-2, y-3), vùng 18x22 trong 18x44, scale 0.5 (vẽ SAU model).
     * - Thanh HP: CobblemonResources.WHITE tại (x+4, y+25), cao 1, dài = 37 * hpRatio, màu getDepletableRedGreen.
     * - Tên: font vanilla, scale 0.5, không bóng chữ, tại (x+4, y+20), căn trái, cắt "…" nếu dài.
     * - Giới tính: "textures/gui/party/party_gender_male/female.png" 7x5, scale 0.5, tại (x+40, y+20).
     * - Level: "cobblemon.ui.lv.number", scale 0.5, căn giữa tại (x+31, y+13), có bóng chữ.
     * - Held item: tại (x+14, y+9.5), scale 0.5 (renderScaledGuiItemIcon).
     */
    private fun renderSlotInfo(context: DrawContext, x: Int, y: Int, data: PokemonEditData) {
        val matrices = context.matrices
        // 1. Bóng bắt (vẽ SAU model để nằm đè lên góc ô)
        matrices.push()
        matrices.translate((x - 2).toDouble(), (y - 3).toDouble(), 0.0)
        matrices.scale(0.5f, 0.5f, 1f)
        context.drawTexture(GuiKit.ballGuiTexture(data.ball), 0, 0, 0f, 0f, 18, 22, 18, 44)
        matrices.pop()

        // 2. Thanh HP
        val hpPokemon = clientPokemon(data)
        val maxHp = (hpPokemon?.maxHealth ?: data.maxHealth).coerceAtLeast(1)
        val curHp = (hpPokemon?.currentHealth ?: data.currentHealth).coerceIn(0, maxHp)
        val hpRatio = curHp.toFloat() / maxHp.toFloat()
        val barWidth = hpRatio * 37f
        if (barWidth > 0f) {
            val (red, green) = getDepletableRedGreen(hpRatio)
            blitk(
                matrixStack = matrices,
                texture = CobblemonResources.WHITE,
                x = x + 4,
                y = y + 25,
                height = 1,
                width = barWidth,
                uOffset = 37f - barWidth,
                textureWidth = barWidth / hpRatio,
                red = red * 0.8f,
                green = green * 0.8f,
                blue = 0.27f,
            )
        }

        // 3. Tên & Giới tính
        val species = PokemonSpecies.getByIdentifier(Identifier.of(data.species))
        val name = species?.translatedName ?: Text.literal(data.species)
        val genderTex = when (data.gender.uppercase()) {
            "MALE" -> GuiKit.PARTY_GENDER_MALE
            "FEMALE" -> GuiKit.PARTY_GENDER_FEMALE
            else -> null
        }
        GuiKit.text(
            context, font, name, x + 4, y + 20,
            scale = 0.5f, minScale = 0.5f, maxWidth = if (genderTex != null) 35 else 38,
            bold = false, shadow = false, small = true,
        )
        if (genderTex != null) {
            matrices.push()
            matrices.translate((x + 40).toDouble(), (y + 20).toDouble(), 0.0)
            matrices.scale(0.5f, 0.5f, 1f)
            context.drawTexture(genderTex, 0, 0, 0f, 0f, 7, 5, 7, 5)
            matrices.pop()
        }

        // 4. Level
        GuiKit.textCentered(
            context, font, Text.translatable("cobblemon.ui.lv.number", data.level),
            x + 31, y + 13,
            scale = 0.5f, minScale = 0.5f, bold = false, shadow = true, small = true,
        )

        // 5. Held item trong ô
        val heldStack = heldItemStack(data)
        if (!heldStack.isEmpty) {
            renderScaledGuiItemIcon(
                itemStack = heldStack,
                x = x + 14.0,
                y = y + 9.5,
                scale = 0.5,
                matrixStack = matrices,
            )
        }
    }

    /** Pokémon cùng UUID trong đội hình phía client (để lấy HP); null với Trainer Editor. */
    private fun clientPokemon(data: PokemonEditData) =
        if (mode == EditorMode.TRAINER) null else CobblemonClient.storage.party.findByUUID(data.uuid)

    private fun renderPartyTooltip(mouseX: Int, mouseY: Int) {
        for (position in 0 until 6) {
            val (x, y) = partySlotPos(position)
            if (!GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), x, y, GuiKit.PARTY_SLOT_W, GuiKit.PARTY_SLOT_H)) continue
            val data = slotData(position) ?: return
            val species = PokemonSpecies.getByIdentifier(Identifier.of(data.species))
            val hpPokemon = clientPokemon(data)
            val maxHp = (hpPokemon?.maxHealth ?: data.maxHealth).coerceAtLeast(1)
            val curHp = (hpPokemon?.currentHealth ?: data.currentHealth).coerceIn(0, maxHp)
            val lines = mutableListOf<Text>(
                species?.translatedName ?: Text.literal(data.species),
                Text.translatable("cobblemon.ui.lv.number", data.level),
                Text.literal("HP $curHp / $maxHp"),
            )
            deferTooltipLines(lines)
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
        val y = footerTop + 7
        GuiKit.text(context, font, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.title"), x, y, GuiKit.GOLD, maxWidth = 28, small = true, bold = true)
        val listX = left + 36
        val colWidth = 94
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
                deferTooltipLines(lines.map { Text.literal(it.first) })
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
