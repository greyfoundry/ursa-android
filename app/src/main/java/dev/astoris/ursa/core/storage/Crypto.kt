package dev.astoris.ursa.core.storage

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * AES-256-GCM encryption for at-rest credentials. The keyset is stored locally but
 * the master key that wraps it lives in the Android Keystore (hardware-backed where
 * available) and never leaves it. See docs/references/datastore-tink.mdx.
 */
class Crypto(context: Context) {

    private val appContext = context.applicationContext
    private val aad = "ursa-connections".toByteArray()

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(appContext, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    fun encrypt(plain: String): String =
        Base64.encodeToString(aead.encrypt(plain.toByteArray(), aad), Base64.NO_WRAP)

    /** Returns null if the ciphertext can't be decrypted (e.g. keyset lost/rotated). */
    fun decrypt(cipherB64: String): String? = runCatching {
        String(aead.decrypt(Base64.decode(cipherB64, Base64.NO_WRAP), aad))
    }.getOrNull()

    private companion object {
        const val KEYSET_NAME = "ursa_keyset"
        const val KEYSET_PREFS = "ursa_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://ursa_master_key"
    }
}
