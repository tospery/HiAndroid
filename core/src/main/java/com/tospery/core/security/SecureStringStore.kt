package com.tospery.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.tospery.buildmetadata.module_core.ModuleMetadata
import com.tospery.base.error.UnknownAppError
import com.tospery.base.logging.LogTags
import com.tospery.base.result.AppResult
import com.tospery.base.logging.error
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val coreLogTag = LogTags.moduleTag(ModuleMetadata.path)

/**
 * 安全字符串存储契约。
 *
 * 实现必须保证持久化介质中不出现明文值。调用方仍需自行确保
 * `preferencesName` 对应的文件不会被备份或迁移到没有同一 Keystore 密钥的设备。
 */
interface SecureStringStore {
    fun get(key: String): AppResult<String?>

    fun put(
        key: String,
        value: String,
    ): AppResult<Unit>

    fun remove(key: String): AppResult<Unit>
}

/**
 * 基于 Android Keystore 与 AES-GCM 的安全字符串存储实现。
 *
 * 密文写入普通 SharedPreferences，AES 密钥仅由 Android Keystore 持有，
 * 不会以明文或可导出的形式落入应用数据目录。
 */
class AndroidKeystoreSecureStringStore(
    context: Context,
    private val preferencesName: String,
    private val keyAlias: String,
) : SecureStringStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()

    init {
        require(preferencesName.isNotBlank()) { "preferencesName 不能为空。" }
        require(keyAlias.isNotBlank()) { "keyAlias 不能为空。" }
    }

    override fun get(key: String): AppResult<String?> {
        validateKey(key)

        return synchronized(lock) {
            val encryptedValue = preferences.getString(key, null)
                ?: return@synchronized AppResult.Success(null)

            runCatching {
                decrypt(
                    key = key,
                    encryptedValue = encryptedValue,
                )
            }.fold(
                onSuccess = { value ->
                    AppResult.Success(value)
                },
                onFailure = { throwable ->
                    // 旧密钥、备份恢复或损坏数据都无法恢复明文，清理后交由调用方重新认证。
                    preferences.edit {
                        remove(key)
                    }
                    failure(
                        message = "读取安全字符串失败，已清除无法解密的数据。",
                        throwable = throwable,
                    )
                },
            )
        }
    }

    override fun put(
        key: String,
        value: String,
    ): AppResult<Unit> {
        validateKey(key)

        return synchronized(lock) {
            runCatching {
                val encryptedValue = encrypt(
                    key = key,
                    value = value,
                )
                check(commitPreferences {
                    putString(key, encryptedValue)
                }) {
                    "无法写入安全字符串。"
                }
            }.fold(
                onSuccess = {
                    AppResult.Success(Unit)
                },
                onFailure = { throwable ->
                    failure(
                        message = "写入安全字符串失败。",
                        throwable = throwable,
                    )
                },
            )
        }
    }

    override fun remove(key: String): AppResult<Unit> {
        validateKey(key)

        return synchronized(lock) {
            runCatching {
                check(commitPreferences {
                    remove(key)
                }) {
                    "无法删除安全字符串。"
                }
            }.fold(
                onSuccess = {
                    AppResult.Success(Unit)
                },
                onFailure = { throwable ->
                    failure(
                        message = "删除安全字符串失败。",
                        throwable = throwable,
                    )
                },
            )
        }
    }

    /**
     * 写入与删除需要感知持久化失败，因此这里不能使用异步的 apply()。
     */
    @Suppress("ApplySharedPref", "UseKtx")
    private fun commitPreferences(
        change: SharedPreferences.Editor.() -> Unit,
    ): Boolean {
        return preferences.edit().run {
            change()
            commit()
        }
    }

    private fun encrypt(
        key: String,
        value: String,
    ): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        cipher.updateAAD(key.toByteArray(StandardCharsets.UTF_8))

        val encryptedBytes = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val initializationVector = cipher.iv

        return listOf(
            CURRENT_PAYLOAD_VERSION,
            initializationVector.toBase64Url(),
            encryptedBytes.toBase64Url(),
        ).joinToString(PAYLOAD_SEPARATOR)
    }

    private fun decrypt(
        key: String,
        encryptedValue: String,
    ): String {
        val payload = encryptedValue.split(
            PAYLOAD_SEPARATOR,
            ignoreCase = false,
            limit = PAYLOAD_PART_COUNT,
        )
        require(payload.size == PAYLOAD_PART_COUNT) { "安全字符串密文格式无效。" }
        require(payload[PAYLOAD_VERSION_INDEX] == CURRENT_PAYLOAD_VERSION) {
            "安全字符串密文版本不受支持。"
        }

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(
                GCM_TAG_LENGTH_BITS,
                payload[INITIALIZATION_VECTOR_INDEX].fromBase64Url(),
            ),
        )
        cipher.updateAAD(key.toByteArray(StandardCharsets.UTF_8))

        return cipher.doFinal(payload[ENCRYPTED_BYTES_INDEX].fromBase64Url())
            .toString(StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        return synchronized(secretKeyLock) {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
            }
            val existingKey = keyStore.getKey(keyAlias, null) as? SecretKey

            existingKey ?: KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            ).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setKeySize(AES_KEY_SIZE_BITS)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
            }.generateKey()
        }
    }

    private fun failure(
        message: String,
        throwable: Throwable,
    ): AppResult.Failure {
        error(
            tag = coreLogTag,
            throwable = throwable,
        ) {
            message
        }

        return AppResult.Failure(
            UnknownAppError(
                debugMessage = message,
                cause = throwable,
            ),
        )
    }

    private fun validateKey(key: String) {
        require(key.isNotBlank()) { "key 不能为空。" }
    }

    private fun ByteArray.toBase64Url(): String {
        return Base64.encodeToString(this, BASE64_FLAGS)
    }

    private fun String.fromBase64Url(): ByteArray {
        return Base64.decode(this, BASE64_FLAGS)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
        const val CURRENT_PAYLOAD_VERSION = "v1"
        const val PAYLOAD_SEPARATOR = ":"
        const val PAYLOAD_PART_COUNT = 3
        const val PAYLOAD_VERSION_INDEX = 0
        const val INITIALIZATION_VECTOR_INDEX = 1
        const val ENCRYPTED_BYTES_INDEX = 2
        const val BASE64_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE

        val secretKeyLock = Any()
    }
}
