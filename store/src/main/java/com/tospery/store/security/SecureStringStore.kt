package com.tospery.store.security

import com.tospery.base.result.AppResult

/**
 * 安全字符串存储契约。持久化介质中不得包含明文值。
 *
 * 调用方负责选择不随备份迁移到其他设备的存储路径。
 */
interface SecureStringStore {
    fun get(key: String): AppResult<String?>

    fun put(
        key: String,
        value: String,
    ): AppResult<Unit>

    fun remove(key: String): AppResult<Unit>
}
