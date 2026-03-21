package com.sndiy.chatfin.feature.finance.analytics.ui

data class DailyExpensePoint(
    val date: String,
    val dayLabel: String,
    val amount: Long
)

data class CategorySlice(
    val categoryId: String,
    val categoryName: String,
    val amount: Long,
    val percentage: Float
)

data class MonthlyBarEntry(
    val monthLabel: String,
    val income: Long,
    val expense: Long
)

enum class AnalyticsPeriod(val label: String) {
    THIS_MONTH("Bulan Ini"),
    LAST_MONTH("Bulan Lalu"),
    LAST_3_MONTHS("3 Bulan"),
    LAST_6_MONTHS("6 Bulan")
}