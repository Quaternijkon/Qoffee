package com.qoffee.core.model

enum class BeanInventoryPriority(
    val displayName: String,
) {
    UNKNOWN("未记录烘焙日"),
    EMPTY("已用完"),
    RESTING("养豆期"),
    FRESH("新鲜"),
    AGING("尽快使用"),
    URGENT("优先使用"),
}

fun buildBeanInventoryPriorities(
    inventory: List<BeanInventory>,
    todayEpochDay: Long,
): Map<String, BeanInventoryPriority> {
    val basePriorities = inventory.associate { item ->
        item.id to item.basePriority(todayEpochDay)
    }.toMutableMap()
    val urgentCandidate = inventory
        .filter { item ->
            item.id.isNotBlank() &&
                item.remainingStockG > 0.0 &&
                item.roastDateEpochDay != null &&
                item.basePriority(todayEpochDay) == BeanInventoryPriority.AGING
        }
        .maxByOrNull { item ->
            val ageDays = (todayEpochDay - checkNotNull(item.roastDateEpochDay)).coerceAtLeast(0L)
            ageDays * item.remainingStockG
        }
    if (urgentCandidate != null) {
        basePriorities[urgentCandidate.id] = BeanInventoryPriority.URGENT
    }
    return basePriorities
}

private fun BeanInventory.basePriority(todayEpochDay: Long): BeanInventoryPriority {
    if (remainingStockG <= 0.0 || remainingRatio <= 0f) return BeanInventoryPriority.EMPTY
    val roastDate = roastDateEpochDay ?: return BeanInventoryPriority.UNKNOWN
    val ageDays = (todayEpochDay - roastDate).coerceAtLeast(0L)
    return when {
        ageDays < RESTING_DAYS -> BeanInventoryPriority.RESTING
        ageDays <= FRESH_UNTIL_DAYS -> BeanInventoryPriority.FRESH
        else -> BeanInventoryPriority.AGING
    }
}

private const val RESTING_DAYS = 7L
private const val FRESH_UNTIL_DAYS = 30L
