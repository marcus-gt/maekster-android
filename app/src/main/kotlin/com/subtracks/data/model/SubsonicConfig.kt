package com.subtracks.data.model

import com.subtracks.data.net.HeaderEntry

/**
 * A server's connection details with its secrets opened. [needsSignIn] is true when the stored password or a header
 * value can no longer be decrypted; the secrets are then empty and nothing should connect until the user enters them
 * again.
 */
data class SubsonicConfig(
    val id: Long,
    val name: String,
    val address: String,
    val username: String,
    val password: String,
    val useTokenAuth: Boolean,
    val needsSignIn: Boolean = false,
    val headers: List<HeaderEntry> = emptyList(),
) {
    override fun toString(): String =
        "SubsonicConfig(id=$id, name=$name, address=$address, username=$username, password=[REDACTED], " +
            "useTokenAuth=$useTokenAuth, needsSignIn=$needsSignIn, headers=$headers)"
}
