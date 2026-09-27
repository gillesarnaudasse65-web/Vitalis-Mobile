package com.vitalis.healthos

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiKeyKind(val storageName: String, val label: String) {
    HEALTH("openai_api_key", "Vitalis Health AI"),
    DEVELOPER("openai_developer_api_key", "Vitalis Developer AI");

    companion object {
        fun fromWire(value: String?): AiKeyKind =
            if (value.equals("developer", ignoreCase = true)) DEVELOPER else HEALTH
    }
}

data class SecretStatus(val configured: Boolean, val maskedSuffix: String? = null)

class SecureSecretStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)

    fun status(kind: AiKeyKind): SecretStatus {
        val key = readForRequest(kind)
        return SecretStatus(
            configured = key != null,
            maskedSuffix = key?.takeLast(4)?.let { "••••$it" }
        )
    }

    fun save(kind: AiKeyKind, rawValue: String): Boolean {
        val clean = rawValue.trim()
        if (!BridgeInputPolicy.apiKey(clean)) return false
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val encrypted = cipher.doFinal(clean.toByteArray(StandardCharsets.UTF_8))
            val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            preferences.edit().putString(kind.storageName, encoded).commit()
        }.getOrDefault(false)
    }

    fun delete(kind: AiKeyKind): Boolean =
        preferences.edit().remove(kind.storageName).commit()

    /** Internal request-only access. Never expose this value to JavaScript, logs or exports. */
    fun readForRequest(kind: AiKeyKind): String? = runCatching {
        val encoded = preferences.getString(kind.storageName, null) ?: return null
        val separator = encoded.indexOf(':')
        if (separator <= 0 || separator >= encoded.lastIndex) return null
        val iv = Base64.decode(encoded.substring(0, separator), Base64.NO_WRAP)
        val encrypted = Base64.decode(encoded.substring(separator + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
            .takeIf(BridgeInputPolicy::apiKey)
    }.getOrNull()

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        const val SECURE_PREFS = "vitalis_secure_preferences"
        const val KEYSTORE_ALIAS = "vitalis_openai_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
