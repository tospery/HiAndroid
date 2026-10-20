package com.tospery.base.analytics

fun String.toAnalyticsValue(): AnalyticsValue = AnalyticsValue.Text(this)

fun Int.toAnalyticsValue(): AnalyticsValue = AnalyticsValue.IntegerNumber(toLong())

fun Long.toAnalyticsValue(): AnalyticsValue = AnalyticsValue.IntegerNumber(this)

fun Float.toAnalyticsValue(): AnalyticsValue = AnalyticsValue.DecimalNumber(toDouble())

fun Double.toAnalyticsValue(): AnalyticsValue = AnalyticsValue.DecimalNumber(this)

fun Boolean.toAnalyticsValue(): AnalyticsValue = AnalyticsValue.BooleanValue(this)

fun analyticsPropertiesOf(
    vararg pairs: Pair<String, AnalyticsValue>,
): AnalyticsProperties = mapOf(*pairs)