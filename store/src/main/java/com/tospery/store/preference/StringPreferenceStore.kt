package com.tospery.store.preference

import kotlinx.coroutines.flow.Flow

/**
 * Non-sensitive string preferences. Values are scoped by the caller's chosen store instance.
 *
 * A failed read or write propagates to the caller; implementations must not silently replace
 * persistent failures with an empty preference.
 */
interface StringPreferenceStore {
    fun observe(key: String): Flow<String?>

    suspend fun put(key: String, value: String)

    suspend fun remove(key: String)
}
