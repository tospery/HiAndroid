package com.tospery.core.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tospery.base.result.AppResult
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecureStringStoreTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var store: AndroidKeystoreSecureStringStore

    @Before
    fun setUp() {
        clearTestData()
        store = createStore()
    }

    @After
    fun tearDown() {
        clearTestData()
    }

    @Test
    fun putThenReloadGetThenRemoveRestoresSecureString() {
        assertEquals(AppResult.Success(Unit), store.put(TEST_KEY, TEST_VALUE))
        val persistedValue = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).getString(TEST_KEY, null)
        assertNotNull(persistedValue)
        assertNotEquals(TEST_VALUE, persistedValue)
        assertEquals(AppResult.Success(TEST_VALUE), store.get(TEST_KEY))

        val reloadedStore = createStore()
        assertEquals(AppResult.Success(TEST_VALUE), reloadedStore.get(TEST_KEY))

        assertEquals(AppResult.Success(Unit), reloadedStore.remove(TEST_KEY))
        assertEquals(AppResult.Success<String?>(null), reloadedStore.get(TEST_KEY))
    }

    private fun createStore(): AndroidKeystoreSecureStringStore {
        return AndroidKeystoreSecureStringStore(
            context = context,
            preferencesName = PREFERENCES_NAME,
            keyAlias = KEY_ALIAS,
        )
    }

    private fun clearTestData() {
        context.deleteSharedPreferences(PREFERENCES_NAME)

        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
            if (containsAlias(KEY_ALIAS)) {
                deleteEntry(KEY_ALIAS)
            }
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val PREFERENCES_NAME = "android_keystore_secure_string_store_test"
        const val KEY_ALIAS = "android_keystore_secure_string_store_test_key"
        const val TEST_KEY = "token"
        const val TEST_VALUE = "secret-value"
    }
}
