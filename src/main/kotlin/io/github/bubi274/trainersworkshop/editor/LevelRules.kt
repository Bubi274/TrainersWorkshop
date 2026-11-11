package io.github.bubi274.trainersworkshop.editor

/**
 * Luật tăng level của PokeEditor bản Survival: chỉ tăng, mỗi level tốn đúng 1 Rare Candy,
 * không vượt level cap. Không phụ thuộc Minecraft/Cobblemon nên kiểm thử được bằng JUnit thuần.
 */
object LevelRules {
    /** Số Rare Candy cần để lên từ [from] tới [to] (0 nếu không tăng). */
    fun cost(from: Int, to: Int): Int = maxOf(0, to - from)

    /** Khoá lỗi: "level_decrease" nếu giảm level, "level_over_cap" nếu vượt [cap]; null nếu hợp lệ. */
    fun error(from: Int, to: Int, cap: Int): String? = when {
        to < from -> "level_decrease"
        to > from && to > cap -> "level_over_cap"
        else -> null
    }

    /** Level cao nhất đạt được: min(cap, from + số Rare Candy đang có). Không bao giờ thấp hơn [from]. */
    fun maxLevel(from: Int, cap: Int, rareCandies: Int): Int = maxOf(from, minOf(cap, from + maxOf(0, rareCandies)))
}
