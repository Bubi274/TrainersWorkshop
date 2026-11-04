package io.github.bubi274.trainersworkshop.compat

import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.config.WorkshopConfig
import io.github.bubi274.trainersworkshop.registry.ModItems
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.loot.LootPool
import net.minecraft.loot.LootTable
import net.minecraft.loot.condition.RandomChanceLootCondition
import net.minecraft.loot.entry.ItemEntry
import net.minecraft.loot.provider.number.ConstantLootNumberProvider
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier

/**
 * Tích hợp tuỳ chọn với Cobblemon Raid Dens: thêm Shiny Berry vào phần thưởng raid 7 sao.
 * Sửa bảng loot lúc tải, không ghi đè file của mod raid. Không cài Raid Dens thì bảng này
 * không tồn tại và đoạn mã dưới đây đơn giản là không làm gì.
 */
object RaidDensCompat {
    private val TIER_SEVEN: RegistryKey<LootTable> = RegistryKey.of(
        RegistryKeys.LOOT_TABLE,
        Identifier.of("cobblemonraiddens", "raid/tier/tier_seven"),
    )

    fun register() {
        LootTableEvents.MODIFY.register { key, tableBuilder, _, _ ->
            if (key != TIER_SEVEN) return@register

            // Đọc lại config mỗi lần tải bảng loot, nên /reload áp dụng tỉ lệ mới mà không cần khởi động lại.
            WorkshopConfig.load()
            val chance = WorkshopConfig.data.raidTierSevenShinyBerryChance.toFloat()
            if (chance <= 0f) return@register

            tableBuilder.pool(
                LootPool.builder()
                    .rolls(ConstantLootNumberProvider.create(1f))
                    .with(ItemEntry.builder(ModItems.SHINY_BERRY))
                    .conditionally(RandomChanceLootCondition.builder(chance))
            )
            TrainersWorkshop.LOGGER.info("Added Shiny Berry to {} ({}% chance)", key.value, chance * 100)
        }
    }
}
