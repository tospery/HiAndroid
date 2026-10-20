package com.tospery.net

/**
 * 适用于 code/message/data 格式的通用响应包裹模型。
 * 如果后端返回格式不同，应在 App 的 data 层适配转换到该模型。
 */
data class ApiEnvelope<DATA>(
    val code: String,
    val message: String?,
    val data: DATA?,
)

/**
 * 适用于带分页元数据和列表数据的通用响应包裹模型。
 * 不同后端的分页格式应由 App 的 data 层适配转换到该模型。
 */
data class PagedEnvelope<ITEM>(
    val hasNext: Boolean,
    val count: Int?,
    val items: List<ITEM>,
)