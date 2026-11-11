package com.subtracks.data.model

/**
 * A server's connection details with its password opened. [needsSignIn] is true when the stored password can no
 * longer be decrypted; [password] is then empty and nothing should connect until the user enters it again.
 */
data class SubsonicConfig(
    val id: Long,
    val name: String,
    val address: String,
    val username: String,
    val password: String,
    val useTokenAuth: Boolean,
    val needsSignIn: Boolean = false,
)
