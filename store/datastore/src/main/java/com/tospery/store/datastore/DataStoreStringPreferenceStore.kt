package com.tospery.store.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tospery.store.preference.StringPreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Adapts an application-owned DataStore instance to [StringPreferenceStore]. */
class DataStoreStringPreferenceStore(
    private val dataStore: DataStore<Preferences>,
) : StringPreferenceStore {
    override fun observe(key: String): Flow<String?> {
        val preferenceKey = stringPreferencesKey(validateKey(key))
        return dataStore.data.map { preferences -> preferences[preferenceKey] }
    }

    override suspend fun put(key: String, value: String) {
        val preferenceKey = stringPreferencesKey(validateKey(key))
        dataStore.edit { preferences -> preferences[preferenceKey] = value }
    }

    override suspend fun remove(key: String) {
        val preferenceKey = stringPreferencesKey(validateKey(key))
        dataStore.edit { preferences -> preferences.remove(preferenceKey) }
    }

    private fun validateKey(key: String): String {
        require(key.isNotBlank()) { "Preference key must not be blank." }
        return key
    }
}
