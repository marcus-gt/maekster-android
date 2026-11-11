package com.subtracks.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts server passwords at rest. Implementations must never log a secret or a ciphertext.
 *
 * A ciphertext can outlive its key: a Keystore reset, a restore onto another device or an interrupted key
 * change all leave ciphertexts nothing can open. [decrypt] then returns null, and the server needs signing in
 * again; the library mirror, downloads, queue and outbox are untouched.
 */
interface CredentialStore {
    /** A ciphertext for [secret], ready to store. Throws if no key can be made. */
    fun encrypt(secret: String): String

    /** The secret [ciphertext] holds, or null when it cannot be opened any more. */
    fun decrypt(ciphertext: String): String?
}

/**
 * The stored form of a ciphertext: a version tag, then the GCM nonce and the sealed bytes, both base64.
 * Kept apart from the Keystore so the format can be tested without one.
 */
internal object CiphertextCodec {
    private const val VERSION = "v1"
    private const val SEPARATOR = ":"
    private const val FLAGS = Base64.NO_WRAP

    fun encode(
        iv: ByteArray,
        sealed: ByteArray,
    ): String = listOf(VERSION, Base64.encodeToString(iv, FLAGS), Base64.encodeToString(sealed, FLAGS)).joinToString(SEPARATOR)

    /** The nonce and sealed bytes, or null for anything that is not a well-formed v1 ciphertext. */
    fun decode(ciphertext: String): Pair<ByteArray, ByteArray>? {
        val parts = ciphertext.split(SEPARATOR)
        if (parts.size != 3 || parts[0] != VERSION) return null
        return runCatching { Base64.decode(parts[1], FLAGS) to Base64.decode(parts[2], FLAGS) }
            .getOrNull()
            ?.takeIf { (iv, sealed) -> iv.isNotEmpty() && sealed.isNotEmpty() }
    }
}

/**
 * [CredentialStore] backed by an AES-256-GCM key in the Android Keystore. The key never leaves secure hardware
 * where the device has it, needs no user authentication, and works while the screen is locked, so background
 * sync and playback can still reach the server.
 */
class KeystoreCredentialStore(
    private val alias: String = KEY_ALIAS,
) : CredentialStore {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    @Synchronized
    override fun encrypt(secret: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
        return CiphertextCodec.encode(cipher.iv, cipher.doFinal(secret.toByteArray(Charsets.UTF_8)))
    }

    @Synchronized
    override fun decrypt(ciphertext: String): String? {
        val (iv, sealed) = CiphertextCodec.decode(ciphertext) ?: return null
        // Never make a key here: a missing key means this ciphertext is lost, and a new one would not open it.
        val key = existingKey() ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun existingKey(): SecretKey? =
        try {
            keyStore.getKey(alias, null) as? SecretKey
        } catch (_: GeneralSecurityException) {
            null
        }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "maekster.server-credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
