package com.tospery.github.model.core

/**
 * GitHub Trending 支持的时间范围。
 */
enum class DateRange(
    val value: String,
) {
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly");

    companion object {
        fun all(): List<DateRange> = entries
    }
}
