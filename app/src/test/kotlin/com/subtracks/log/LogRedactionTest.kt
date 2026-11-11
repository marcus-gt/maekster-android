package com.subtracks.log

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogRedactionTest {
    @After
    fun forgetSecrets() = setRedactedSecrets(emptyList())

    @Test
    fun subsonicCredentialsInUrlsAreRemoved() {
        val line = redactLogs("GET https://music.example/rest/ping.view?v=1.15.0&u=marcus&s=abcd&t=0123456789abcdef&c=maekster")

        assertEquals("GET https://music.example/rest/ping.view?v=1.15.0&u=REDACTED&s=REDACTED&t=REDACTED&c=maekster", line)
        assertEquals("…?p=REDACTED", redactLogs("…?p=enc:73656372657"))
    }

    @Test
    fun apiKeysTokensAndUserInfoAreRemoved() {
        assertEquals("x?apiKey=REDACTED&id=1", redactLogs("x?apiKey=k3y&id=1"))
        assertEquals("x?TOKEN=REDACTED", redactLogs("x?TOKEN=abc"))
        assertEquals("https://REDACTED@music.example/", redactLogs("https://user:pass@music.example/"))
    }

    @Test
    fun authSchemeCredentialsAreRemoved() {
        assertEquals("Authorization: Bearer REDACTED", redactLogs("Authorization: Bearer eyJhbGciOi.J9.sig"))
        assertEquals("basic REDACTED rest", redactLogs("basic dXNlcjpwYXNz rest"))
    }

    @Test
    fun theActiveServersSecretsAreRemovedWhereverTheyAppear() {
        setRedactedSecrets(listOf("hunter22", "cf-secret-value", "abc"))

        val line = redactLogs("failed: unexpected header value cf-secret-value for hunter22 (abc)")

        assertFalse(line.contains("hunter22"))
        assertFalse(line.contains("cf-secret-value"))
        assertEquals("too short to redact safely", "failed: unexpected header value REDACTED for REDACTED (abc)", line)
    }
}
