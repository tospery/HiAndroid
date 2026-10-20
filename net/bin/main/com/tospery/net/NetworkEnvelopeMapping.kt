package com.tospery.net

import com.tospery.base.result.AppResult

/**
 * 把成功时必须包含 data 的响应转换成 NetworkResult。
 * 适合详情、列表、搜索结果等接口。
 */
fun <DATA : Any> ApiEnvelope<DATA>.toRequiredDataNetworkResult(
    isSuccessCode: (String) -> Boolean,
    businessErrorMapper: BusinessErrorMapper = DefaultBusinessErrorMapper,
): NetworkResult<DATA> {
    if (!isSuccessCode(code)) {
        return AppResult.Failure(businessErrorMapper.map(code, message))
    }

    return data
        ?.let { AppResult.Success(it) }
        ?: AppResult.Failure(
            InvalidDataError.InvalidResponseFormat(
                debugMessage = "接口成功，但缺少必需的 data 字段。",
            ),
        )
}

/**
 * 把成功时不需要 data 的响应转换成 NetworkResult<Unit>。
 * 适合收藏、取消收藏、删除、提交设置等只关心成功/失败的接口。
 */
fun ApiEnvelope<*>.toEmptyNetworkResult(
    isSuccessCode: (String) -> Boolean,
    businessErrorMapper: BusinessErrorMapper = DefaultBusinessErrorMapper,
): NetworkResult<Unit> {
    if (!isSuccessCode(code)) {
        return AppResult.Failure(businessErrorMapper.map(code, message))
    }

    return AppResult.Success(Unit)
}

/**
 * 把分页包裹模型转换成 NetworkResult。
 * 默认允许空列表，因为搜索无结果、第一页为空等场景不一定是错误。
 */
fun <ITEM> PagedEnvelope<ITEM>.toNetworkResult(
    allowEmpty: Boolean = true,
): NetworkResult<PagedEnvelope<ITEM>> {
    if (!allowEmpty && items.isEmpty()) {
        return AppResult.Failure(
            InvalidDataError.EmptyList(
                debugMessage = "PagedEnvelope.items 为空。",
            ),
        )
    }

    return AppResult.Success(this)
}

/**
 * 把业务 code 和 message 映射成 NetworkError。
 * 不同项目的业务 code 规则不同，因此默认实现只映射为 ServerError.BusinessFailure。
 */
fun interface BusinessErrorMapper {
    fun map(code: String, message: String?): NetworkError
}

/**
 * 默认业务错误映射：保留原始业务 code。
 */
object DefaultBusinessErrorMapper : BusinessErrorMapper {
    override fun map(code: String, message: String?): NetworkError {
        return ServerError.BusinessFailure(
            code = code,
            message = message,
            debugMessage = message,
        )
    }
}
