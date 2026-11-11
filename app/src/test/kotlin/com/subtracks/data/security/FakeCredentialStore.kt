package com.subtracks.data.security

/**
 * A [CredentialStore] for tests. Ciphertexts are tagged with the key generation that made them; [loseKey] starts a
 * new generation, so older ciphertexts can no longer be opened, as after a Keystore reset. [corrupt] makes every
 * ciphertext open to the wrong secret, to exercise the verify-before-clear step.
 */
class FakeCredentialStore : CredentialStore {
    @Volatile
    private var generation = 0

    @Volatile
    var corrupt = false

    fun loseKey() {
        generation++
    }

    override fun encrypt(secret: String): String = "fake:$generation:${secret.reversed()}"

    override fun decrypt(ciphertext: String): String? {
        val parts = ciphertext.split(":", limit = 3)
        if (parts.size != 3 || parts[0] != "fake" || parts[1].toIntOrNull() != generation) return null
        val secret = parts[2].reversed()
        return if (corrupt) "$secret-corrupted" else secret
    }
}
