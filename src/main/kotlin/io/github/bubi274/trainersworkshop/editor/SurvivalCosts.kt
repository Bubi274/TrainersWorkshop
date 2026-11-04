package io.github.bubi274.trainersworkshop.editor

import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.item.berry.FriendshipRaisingBerryItem
import com.cobblemon.mod.common.item.interactive.EVIncreaseItem
import com.cobblemon.mod.common.item.interactive.HyperTrainingItem
import com.cobblemon.mod.common.item.interactive.MintItem
import io.github.bubi274.trainersworkshop.registry.ModItems
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import kotlin.math.min

/**
 * Tính vật phẩm cần cho PokeEditor bản survival.
 * Dùng chung cho client (hiển thị ô đỏ, khoá nút Apply) và server (kiểm tra và trừ vật phẩm),
 * nên hai bên luôn tính giống nhau.
 */
object SurvivalCosts {
    const val MAX_IV = 31
    const val EV_PER_VITAMIN = 10
    const val EV_PER_FEATHER = 1
    /**
     * Berry giảm EV (Pomeg, Kelpsy...) giảm 10 mỗi quả.
     * TODO(api): Cobblemon đọc mức giảm từ cấu hình cơ chế berry (getEvLowerAmount); ở đây cố định 10 theo yêu cầu.
     */
    const val EV_PER_BERRY = 10

    /** Một dòng chi phí, gắn với phần đang chỉnh (để tô đỏ đúng chỗ). */
    data class Line(val field: String, val item: Item?, val count: Int, val missingItemId: String? = null)

    data class Result(
        val lines: List<Line>,
        /** Tổng số cần cho mỗi vật phẩm. */
        val needed: Map<Item, Int>,
        /** Các phần đang chỉnh bị thiếu vật phẩm hoặc không hợp lệ (ví dụ "iv.0", "move.2", "nature"). */
        val problems: Set<String>,
        /** Khoá lỗi chặn hẳn việc Apply (thay đổi không được phép ở survival). */
        val error: String?,
    ) {
        val ok: Boolean get() = error == null && problems.isEmpty()
        val isEmpty: Boolean get() = lines.isEmpty()
    }

    private val CAP_SUFFIX: Map<Stat, String> = mapOf(
        Stats.HP to "hp",
        Stats.ATTACK to "attack",
        Stats.DEFENCE to "defence",
        Stats.SPECIAL_ATTACK to "special_attack",
        Stats.SPECIAL_DEFENCE to "special_defence",
        Stats.SPEED to "speed",
    )

    // Danh sách vật phẩm lấy từ registry (lần đầu dùng), nên tự nhận vitamin/feather/berry/mint của Cobblemon.
    private val vitamins by lazy { evItems(EV_PER_VITAMIN) }
    private val feathers by lazy { evItems(EV_PER_FEATHER) }
    private val evBerries: Map<Stat, Item> by lazy {
        Registries.ITEM.filterIsInstance<FriendshipRaisingBerryItem>().associateBy { it.stat }
    }
    /**
     * 12 kẹo IV của Cobblemon (Health/Mighty/... Candy tăng, Sickly/Weak/... Candy giảm).
     * Trong Cobblemon chúng là HyperTrainingItem với ivIncreaseAmount = +1 hoặc -1 và một chỉ số.
     */
    private val candiesUp: Map<Stat, Item> by lazy { candies(1) }
    private val candiesDown: Map<Stat, Item> by lazy { candies(-1) }

    // TODO(build): kiểm tra trong game là đủ 12 kẹo (Cobblemon 1.7.3 tạo chúng bằng hyperTrainingItem(id, ±1, setOf(stat), 0..31)).
    private fun candies(amount: Int): Map<Stat, Item> =
        Registries.ITEM.filterIsInstance<HyperTrainingItem>()
            .filter { it.ivIncreaseAmount == amount && it.targetStats.size == 1 }
            .associateBy { it.targetStats.first() }

    fun candy(stat: Stat, up: Boolean): Item? = if (up) candiesUp[stat] else candiesDown[stat]

    private val mints: Map<String, Item> by lazy {
        Registries.ITEM.filterIsInstance<MintItem>().associateBy { it.nature.name.toString() }
    }

    private fun evItems(amount: Int): Map<Stat, Item> =
        Registries.ITEM.filterIsInstance<EVIncreaseItem>().filter { it.evIncreaseAmount == amount }.associateBy { it.stat }

    fun itemById(id: String): Item? {
        val parsed = Identifier.tryParse(id) ?: return null
        return Registries.ITEM.getOrEmpty(parsed).orElse(null)
    }

    fun mintFor(nature: String): Item? = mints[nature]
    fun vitamin(stat: Stat): Item? = vitamins[stat]
    fun feather(stat: Stat): Item? = feathers[stat]
    fun evBerry(stat: Stat): Item? = evBerries[stat]

    // ID vật phẩm của Only Bottle Caps (đối chiếu trong jar obc 1.3.0).
    fun bottleCapId(stat: Stat): String = "obc:bottle_cap_${CAP_SUFFIX[stat]}"
    fun witheredCapId(stat: Stat): String = "obc:bottle_cap_${CAP_SUFFIX[stat]}_withered"
    const val GOLD_CAP_ID = "obc:bottle_cap_gold"
    const val GOLD_WITHERED_CAP_ID = "obc:bottle_cap_gold_withered"
    const val MIN_IV = 0
    fun tmId(move: String): String = "tmcraft:tm_$move"

    /**
     * @param have số lượng vật phẩm người chơi đang có.
     * @param free true nếu người chơi ở chế độ creative (không tốn vật phẩm).
     */
    fun compute(current: PokemonEditData, target: PokemonEditData, options: PokemonEditOptions, have: (Item) -> Int, free: Boolean = false): Result {
        val lines = mutableListOf<Line>()
        var error: String? = null

        // Những phần survival không được đổi.
        if (target.uuid != current.uuid || target.species != current.species) error = "pokemon_changed"
        if (target.level != current.level || target.gender != current.gender || target.ability != current.ability ||
            target.ball != current.ball || target.nature != current.nature
        ) error = "survival_locked"

        addIvCosts(current, target, lines)?.let { error = it }
        addEvCosts(current, target, lines, have)?.let { error = it }
        addNatureCost(current, target, lines)?.let { error = it }
        addMoveCosts(current, target, options, lines)?.let { error = it }
        if (target.shiny != current.shiny) {
            if (!target.shiny) error = "survival_unshiny" else lines += Line("shiny", ModItems.SHINY_BERRY, 1)
        }

        val needed = LinkedHashMap<Item, Int>()
        lines.forEach { line -> line.item?.let { needed.merge(it, line.count, Int::plus) } }

        val problems = mutableSetOf<String>()
        lines.filter { it.item == null }.forEach { problems += it.field }
        if (!free) {
            // Thiếu vật phẩm: đánh dấu mọi phần dùng vật phẩm đó.
            val short = needed.filter { (item, count) -> have(item) < count }.keys
            lines.filter { it.item in short }.forEach { problems += it.field }
        }
        return Result(lines, needed, problems, error)
    }

    /**
     * IV có hai phần, giống Cobblemon:
     * - IV gốc: Bottle Cap đưa lên 31, Withered Bottle Cap đưa về 0 (Only Bottle Caps gọi Pokemon.setIV).
     *   Gold / Gold Withered làm cả 6 chỉ số. Mod đó báo lỗi nếu không có chỉ số nào thay đổi.
     * - IV hyper train (IV có hiệu lực): mỗi kẹo ±1, trong khoảng 0..31.
     * Server áp dụng nắp trước rồi mới tới kẹo, nên số kẹo tính từ IV có hiệu lực sau khi dùng nắp.
     */
    private fun addIvCosts(current: PokemonEditData, target: PokemonEditData, lines: MutableList<Line>): String? {
        if (target.useGoldCap && target.useGoldWithered) return "survival_iv"
        val gold = when {
            target.useGoldCap -> MAX_IV
            target.useGoldWithered -> MIN_IV
            else -> null
        }
        if (gold != null) {
            if (target.ivs.any { it != gold } || current.ivs.all { it == gold }) return "survival_iv"
            val id = if (gold == MAX_IV) GOLD_CAP_ID else GOLD_WITHERED_CAP_ID
            lines += Line("iv.gold", itemById(id), 1, id)
        } else {
            EDITOR_STATS.forEachIndexed { i, stat ->
                if (target.ivs[i] == current.ivs[i]) return@forEachIndexed
                val id = when (target.ivs[i]) {
                    MAX_IV -> bottleCapId(stat)
                    MIN_IV -> witheredCapId(stat)
                    else -> return "survival_iv"
                }
                lines += Line("iv.$i", itemById(id), 1, id)
            }
        }

        EDITOR_STATS.forEachIndexed { i, stat ->
            val hyper = target.hyperIvs[i]
            if (hyper == current.hyperIvs[i]) return@forEachIndexed
            // Hyper bằng IV gốc thì Cobblemon xoá hyper (IVs.setHyperTrainedIV), nên phải ghi là NO_HYPER.
            if (hyper != NO_HYPER && (hyper !in MIN_IV..MAX_IV || hyper == target.ivs[i])) return "survival_iv"
            val from = current.hyperIvs[i].takeIf { it >= 0 } ?: target.ivs[i]
            val to = target.effectiveIv(i)
            if (from == to) return "survival_iv"
            val up = to > from
            lines += Line("iv.$i", candy(stat, up), kotlin.math.abs(to - from))
        }
        return null
    }

    private fun addEvCosts(current: PokemonEditData, target: PokemonEditData, lines: MutableList<Line>, have: (Item) -> Int): String? {
        if (target.evs.any { it !in 0..PokemonEditing.MAX_EV } || target.evs.sum() > PokemonEditing.MAX_EV_TOTAL) return "invalid_evs"
        EDITOR_STATS.forEachIndexed { i, stat ->
            val diff = target.evs[i] - current.evs[i]
            when {
                diff > 0 -> {
                    val vitamin = vitamins[stat]
                    val feather = feathers[stat]
                    // Ưu tiên vitamin (+10); phần lẻ và phần thiếu vitamin bù bằng feather (+1).
                    var useVitamins = diff / EV_PER_VITAMIN
                    var useFeathers = diff % EV_PER_VITAMIN
                    if (vitamin != null) {
                        val available = min(useVitamins, have(vitamin))
                        useFeathers += (useVitamins - available) * EV_PER_VITAMIN
                        useVitamins = available
                    } else {
                        useFeathers += useVitamins * EV_PER_VITAMIN
                        useVitamins = 0
                    }
                    if (useVitamins > 0) lines += Line("ev.$i", vitamin, useVitamins)
                    if (useFeathers > 0) lines += Line("ev.$i", feather, useFeathers)
                }
                diff < 0 -> {
                    val decrease = -diff
                    // Mỗi berry giảm đúng 10 (không xuống dưới 0).
                    if (decrease % EV_PER_BERRY != 0 && target.evs[i] != 0) return "survival_ev_step"
                    val berries = (decrease + EV_PER_BERRY - 1) / EV_PER_BERRY
                    lines += Line("ev.$i", evBerries[stat], berries)
                }
            }
        }
        return null
    }

    private fun addNatureCost(current: PokemonEditData, target: PokemonEditData, lines: MutableList<Line>): String? {
        if (target.effectiveNature == current.effectiveNature) {
            return if (target.mintedNature != current.mintedNature) "survival_locked" else null
        }
        if (target.mintedNature.isEmpty()) return "survival_locked"
        lines += Line("nature", mints[target.mintedNature], 1)
        return null
    }

    private fun addMoveCosts(current: PokemonEditData, target: PokemonEditData, options: PokemonEditOptions, lines: MutableList<Line>): String? {
        target.moves.forEachIndexed { slot, move ->
            if (move.isEmpty() || move == current.moves[slot] || move in options.freeMoves || move in current.moves) return@forEachIndexed
            if (move !in options.tmMoves) return "survival_move"
            val id = tmId(move)
            lines += Line("move.$slot", itemById(id), 1, id)
        }
        return null
    }
}
