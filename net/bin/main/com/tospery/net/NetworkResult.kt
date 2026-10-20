package com.tospery.net

import com.tospery.base.result.AppResult

/**
 * 网络请求结果类型。
 * 成功时包含业务数据，失败时包含 NetworkError。
 */
typealias NetworkResult<DATA> = AppResult<DATA>