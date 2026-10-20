package com.tospery.net

/**
 * 网络响应映射契约。
 * 用于把原始响应体或后端特定格式转换成 App 需要的目标模型。
 */
fun interface NetworkResponseMapper<RAW, TARGET> {
    fun map(raw: RAW): NetworkResult<TARGET>
}