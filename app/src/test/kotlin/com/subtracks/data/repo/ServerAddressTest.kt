package com.subtracks.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {
    @Test
    fun anAddressWithoutASchemeGetsHttps() {
        assertEquals("https://music.example/", normalizeServerAddress("music.example"))
        assertEquals("https://192.168.1.5:4533/", normalizeServerAddress("  192.168.1.5:4533  "))
    }

    @Test
    fun aTypedSchemeIsKeptAndTheSlashIsSingle() {
        assertEquals("http://192.168.1.5:4533/", normalizeServerAddress("http://192.168.1.5:4533"))
        assertEquals("https://music.example/sub/", normalizeServerAddress("https://music.example/sub///"))
        assertEquals("HTTP://music.example/", normalizeServerAddress("HTTP://music.example"))
    }

    @Test
    fun onlyAnExplicitHttpAddressIsPlain() {
        assertTrue(isPlainHttp("http://192.168.1.5:4533"))
        assertTrue(isPlainHttp("HTTP://music.example"))
        assertFalse(isPlainHttp("https://music.example"))
        assertFalse("a bare host now means https", isPlainHttp("music.example"))
    }
}
