package com.subtracks.data.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CiphertextCodecTest {
    @Test
    fun aCiphertextRoundTripsItsNonceAndSealedBytes() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val sealed = byteArrayOf(42, -1, 0, 7)

        val encoded = CiphertextCodec.encode(iv, sealed)
        val (decodedIv, decodedSealed) = CiphertextCodec.decode(encoded)!!

        assertTrue(encoded.startsWith("v1:"))
        assertArrayEquals(iv, decodedIv)
        assertArrayEquals(sealed, decodedSealed)
    }

    @Test
    fun theStoredFormNeverContainsTheSealedBytesVerbatim() {
        val encoded = CiphertextCodec.encode(byteArrayOf(1), "hunter2".toByteArray())

        assertFalse(encoded.contains("hunter2"))
    }

    @Test
    fun anythingElseIsRejected() {
        assertNull(CiphertextCodec.decode(""))
        assertNull(CiphertextCodec.decode("hunter2"))
        assertNull(CiphertextCodec.decode("v2:AQ==:AQ=="))
        assertNull(CiphertextCodec.decode("v1:AQ=="))
        assertNull(CiphertextCodec.decode("v1::AQ=="))
        assertNull(CiphertextCodec.decode("v1:not base64!:AQ=="))
    }
}
