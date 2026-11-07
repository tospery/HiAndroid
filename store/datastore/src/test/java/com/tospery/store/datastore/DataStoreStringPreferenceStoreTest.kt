package com.tospery.store.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DataStoreStringPreferenceStoreTest {
    @Test
    fun writesObservesAndRemovesAValue() = runBlocking {
        val store = DataStoreStringPreferenceStore(InMemoryPreferencesDataStore())

        assertNull(store.observe("theme").first())
        store.put("theme", "dark")
        assertEquals("dark", store.observe("theme").first())
        store.remove("theme")
        assertNull(store.observe("theme").first())
    }

    @Test
    fun rejectsBlankKeys() {
        val store = DataStoreStringPreferenceStore(InMemoryPreferencesDataStore())
        assertThrows(IllegalArgumentException::class.java) {
            store.observe(" ")
        }
    }
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val value = transform(state.value)
        state.value = value
        return value
    }
}
