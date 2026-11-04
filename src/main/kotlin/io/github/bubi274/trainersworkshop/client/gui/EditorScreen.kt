package io.github.bubi274.trainersworkshop.client.gui

import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget
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

/**
 * Màn hình chỉnh sửa dùng chung cho PokeEditor (creative/survival).
 * Bố cục theo màn hình Summary của Cobblemon: trái = Pokémon đang chọn, giữa = các tab, phải = đội hình + nút.
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

    val mode: EditorMode get() = state.mode
    val selectedSlot: EditorSlot? get() = state.slot(selectedIndex)
    val left: Int get() = (width - GuiKit.BASE_WIDTH) / 2
    val top: Int get() = (height - GuiKit.BASE_HEIGHT) / 2

    /** Vùng nội dung của tab (panel giữa). */
    val contentX: Int get() = left + 80
    val contentY: Int get() = top + 16
    val contentWidth: Int = 128
    val contentHeight: Int = 140

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
        return SurvivalCosts.compute(slot.data, data, slot.options, { player.inventory.count(it) }, player.abilities.creativeMode)
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
        activeTab = tab
        rebuild()
    }

    fun <T : ClickableWidget> addWidget(widget: T): T = addDrawableChild(widget)

    fun button(label: Text, x: Int, y: Int, w: Int, h: Int, tooltip: Text? = null, active: Boolean = true, onPress: () -> Unit): ButtonWidget {
        val builder = ButtonWidget.builder(label) { onPress() }.dimensions(x, y, w, h)
        if (tooltip != null) builder.tooltip(Tooltip.of(tooltip))
        return addWidget(builder.build()).also { it.active = active }
    }

    override fun init() {
        rebuild()
    }

    fun rebuild() {
        // Chưa init (chưa biết kích thước màn hình): init() sẽ gọi lại.
        if (client == null) return
        clearChildren()
        val tabWidth = contentWidth / tabs.size
        tabs.forEachIndexed { i, tab ->
            button(tab.title, contentX - 1 + i * tabWidth, top + 1, tabWidth, 12, tab.title, active = tab != activeTab) { switchTab(tab) }
        }
        if (draft != null || !activeTab.needsPokemon) activeTab.init()
        if (mode == EditorMode.TRAINER) initTrainerPanel() else initRightPanel()
        updateModel()
    }

    private fun initRightPanel() {
        val y = top + 137
        button(Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cancel"), left + 216, y, 54, 13) { cancel() }
        button(Text.translatable("gui.${TrainersWorkshop.MOD_ID}.apply"), left + 273, y, 54, 13, active = canApply()) { apply() }
    }

    /** Panel phải của Trainer Editor: đổi loài, thêm/bớt Pokémon, Reset / Close / Save. */
    private fun initTrainerPanel() {
        if (trainer == null) trainer = decodeTrainer()
        val x = left + 216
        val field = TextFieldWidget(textRenderer, x, top + 78, 111, 12, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.species"))
        field.setMaxLength(64)
        field.text = speciesInput
        field.setPlaceholder(Text.translatable("gui.${TrainersWorkshop.MOD_ID}.species.placeholder"))
        field.setChangedListener { speciesInput = it }
        addWidget(field)
        val gui = { key: String -> Text.translatable("gui.${TrainersWorkshop.MOD_ID}.$key") }
        button(gui("button.set_species"), x, top + 93, 36, 12, gui("hint.set_species"), active = selectedSlot != null) {
            trainerAction(TrainerEditing.SET_SPECIES) {
                putInt("index", selectedIndex)
                putString("species", speciesInput)
            }
        }
        button(gui("button.add"), x + 38, top + 93, 36, 12, gui("hint.add_pokemon"), active = state.slots.size < 6) {
            trainerAction(TrainerEditing.ADD) { putString("species", speciesInput) }
        }
        button(gui("button.remove"), x + 76, top + 93, 36, 12, gui("hint.remove_pokemon"), active = state.slots.size > 1 && selectedSlot != null) {
            trainerAction(TrainerEditing.REMOVE) { putInt("index", selectedIndex) }
        }
        button(gui("button.reset"), x, top + 137, 36, 13, gui("hint.reset")) { trainerAction(TrainerEditing.RESET) {} }
        button(gui("button.close"), x + 38, top + 137, 36, 13) { close() }
        button(gui("button.save"), x + 76, top + 137, 36, 13, gui("hint.save")) { trainerAction(TrainerEditing.SAVE) {} }
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

    private fun updateModel() {
        val data = draft
        val species = data?.let { PokemonSpecies.getByIdentifier(Identifier.of(it.species)) }
        if (data == null || species == null) {
            modelWidget = null
            modelKey = ""
            return
        }
        val aspects = data.aspects.toMutableSet()
        if (data.shiny) aspects.add("shiny") else aspects.remove("shiny")
        val key = "${data.uuid}|${data.species}|${aspects.sorted()}"
        if (key != modelKey || modelWidget == null) {
            modelWidget = ModelWidget(
                pX = left + 6, pY = top + 31, pWidth = 66, pHeight = 66,
                pokemon = RenderablePokemon(species, aspects, ItemStack.EMPTY),
                baseScale = 2f, rotationY = 325f, offsetY = -10.0,
            )
            modelKey = key
        }
        modelWidget?.let { addDrawableChild(it) }
    }

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderBackground(context, mouseX, mouseY, delta)
        context.drawTexture(GuiKit.SUMMARY_BASE, left, top, 0f, 0f, GuiKit.BASE_WIDTH, GuiKit.BASE_HEIGHT, GuiKit.BASE_WIDTH, GuiKit.BASE_HEIGHT)
        context.drawTexture(GuiKit.PORTRAIT_BACKGROUND, left + 6, top + 31, 0f, 0f, 66, 66, 66, 66)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        renderTitle(context)
        renderInfo(context)
        renderParty(context, mouseX, mouseY)
        renderCost(context)
        renderRightPanelInfo(context, mouseX, mouseY)
        if (draft != null || !activeTab.needsPokemon) {
            activeTab.render(context, mouseX, mouseY, delta)
        } else {
            GuiKit.centeredText(context, textRenderer, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.no_pokemon"), contentX + contentWidth / 2, contentY + 60, GuiKit.GRAY)
        }
    }

    private fun renderTitle(context: DrawContext) {
        val key = when (mode) {
            EditorMode.CREATIVE -> "pokeeditor_creative"
            EditorMode.SURVIVAL -> "pokeeditor"
            EditorMode.TRAINER -> "trainer_editor"
        }
        GuiKit.text(context, textRenderer, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.title.$key"), left + 6, top + 18, scale = 0.75f)
    }

    /** Panel trái: tên, level, shiny, hệ, nature, ability. */
    private fun renderInfo(context: DrawContext) {
        val data = draft ?: return
        val species = PokemonSpecies.getByIdentifier(Identifier.of(data.species)) ?: return
        val x = left + 6
        GuiKit.text(context, textRenderer, species.translatedName, x, top + 100, scale = 0.75f)
        GuiKit.text(context, textRenderer, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.level", data.level), x, top + 112, scale = 0.75f)
        if (data.shiny) {
            context.drawTexture(GuiKit.SHINY_ICON, left + 62, top + 110, 0f, 0f, 10, 10, 10, 10)
        }
        val form = species.getForm(data.aspects.toSet())
        TypeIcon(x = left + 39, y = top + 145, type = form.primaryType, secondaryType = form.secondaryType, centeredX = true, small = true).render(context)
        val nature = GuiKit.natureName(data.effectiveNature)
        GuiKit.text(context, textRenderer, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.nature", nature), x, top + 123, scale = 0.5f)
        val ability = Abilities.get(data.ability)?.displayName
        GuiKit.text(context, textRenderer, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.ability", ability?.let { Text.translatable(it) } ?: Text.literal(data.ability)), x, top + 130, scale = 0.5f)
        GuiKit.text(context, textRenderer, GuiKit.ballName(data.ball), x, top + 137, GuiKit.GRAY, scale = 0.5f)
    }

    /** Ô đội hình: 2 cột x 3 hàng ở panel phải. */
    private fun partySlotPos(position: Int): Pair<Int, Int> = Pair(left + 215 + (position % 2) * 57, top + 21 + (position / 2) * 18)

    private fun renderParty(context: DrawContext, mouseX: Int, mouseY: Int) {
        GuiKit.text(context, textRenderer, partyTitle(), left + 216, top + 10, scale = 0.75f)
        for (position in 0 until 6) {
            val (x, y) = partySlotPos(position)
            val slot = state.slot(position)
            val hovered = GuiKit.inside(mouseX.toDouble(), mouseY.toDouble(), x, y, 55, 16)
            context.fill(x, y, x + 55, y + 16, if (hovered && slot != null) GuiKit.SLOT_HOVER else GuiKit.SLOT_FILL)
            if (position == selectedIndex) context.drawBorder(x, y, 55, 16, GuiKit.SELECTED_BORDER)
            if (slot == null) continue
            val species = PokemonSpecies.getByIdentifier(Identifier.of(slot.data.species))
            val name = species?.translatedName ?: Text.literal(slot.data.species)
            GuiKit.text(context, textRenderer, name, x + 3, y + 2, scale = 0.5f)
            GuiKit.text(context, textRenderer, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.level", slot.data.level), x + 3, y + 9, GuiKit.GRAY, scale = 0.5f)
            if (slot.data.shiny) context.drawTexture(GuiKit.SHINY_ICON, x + 46, y + 3, 0f, 0f, 6, 6, 6, 6)
        }
    }

    fun partyTitle(): Text {
        val view = trainer ?: return Text.translatable("gui.${TrainersWorkshop.MOD_ID}.party")
        val key = if (view.customized) "trainer.edited" else "trainer.default"
        return Text.translatable("gui.${TrainersWorkshop.MOD_ID}.$key", view.name)
    }

    /** Vùng dưới đội hình: thông báo kết quả từ server. */
    private fun renderRightPanelInfo(context: DrawContext, mouseX: Int, mouseY: Int) {
        val statusNbt = status ?: return
        val args = readStringList(statusNbt, "args").map { Text.literal(it) }.toTypedArray<Any>()
        val message = Text.translatable(statusNbt.getString("key"), *args)
        val color = if (statusNbt.getBoolean("error")) GuiKit.RED else GuiKit.GREEN
        val lines = textRenderer.wrapLines(message, 220)
        lines.take(3).forEachIndexed { i, line ->
            val matrices = context.matrices
            matrices.push()
            val statusY = if (mode == EditorMode.TRAINER) top + 108 else top + 112
            matrices.translate((left + 216).toDouble(), (statusY + i * 6).toDouble(), 0.0)
            matrices.scale(0.5f, 0.5f, 1f)
            context.drawText(textRenderer, line, 0, 0, color, true)
            matrices.pop()
        }
    }

    /** Survival: danh sách vật phẩm cần (đỏ nếu thiếu). */
    private fun renderCost(context: DrawContext) {
        val cost = survivalCost() ?: return
        val x = left + 216
        var y = top + 78
        GuiKit.text(context, textRenderer, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost"), x, y, GuiKit.GRAY, 0.5f)
        y += 6
        val creative = client?.player?.abilities?.creativeMode == true
        if (cost.isEmpty) {
            GuiKit.text(context, textRenderer, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.none"), x, y, GuiKit.GRAY, 0.5f)
            return
        }
        if (creative) {
            GuiKit.text(context, textRenderer, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.creative"), x, y, GuiKit.GOLD, 0.5f)
            y += 6
        }
        val entries = cost.needed.entries.toList()
        val shown = entries.take(4)
        shown.forEach { (item, count) ->
            val have = haveCount(item)
            val color = if (creative || have >= count) GuiKit.WHITE else GuiKit.RED
            GuiKit.text(context, textRenderer, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.line", item.name, have, count), x, y, color, 0.5f)
            y += 6
        }
        if (entries.size > shown.size) {
            GuiKit.text(context, textRenderer, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.more", entries.size - shown.size), x, y, GuiKit.GRAY, 0.5f)
            y += 6
        }
        // Vật phẩm của mod tuỳ chọn chưa cài (Only Bottle Caps, TMCraft).
        cost.lines.filter { it.item == null }.map { it.missingItemId ?: it.field }.distinct().take(2).forEach { id ->
            GuiKit.text(context, textRenderer, Text.translatable("gui.${TrainersWorkshop.MOD_ID}.cost.unavailable", id), x, y, GuiKit.RED, 0.5f)
            y += 6
        }
        cost.error?.let {
            GuiKit.text(context, textRenderer, Text.translatable("message.${TrainersWorkshop.MOD_ID}.editor.$it"), x, y, GuiKit.RED, 0.5f)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        for (position in 0 until 6) {
            val (x, y) = partySlotPos(position)
            if (GuiKit.inside(mouseX, mouseY, x, y, 55, 16) && state.slot(position) != null) {
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
