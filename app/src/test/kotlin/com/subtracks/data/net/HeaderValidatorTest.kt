package com.subtracks.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderValidatorTest {
    @Test
    fun namesAreRfc7230Tokens() {
        assertTrue(HeaderValidator.isValidName("CF-Access-Client-Id"))
        assertTrue(HeaderValidator.isValidName("X_Token.v2!#$%&'*+^`|~"))
        assertFalse(HeaderValidator.isValidName(""))
        assertFalse(HeaderValidator.isValidName("Has Space"))
        assertFalse(HeaderValidator.isValidName("Colon:"))
        assertFalse(HeaderValidator.isValidName("Ünicode"))
    }

    @Test
    fun valuesArePrintableAsciiWithoutLineBreaks() {
        assertTrue(HeaderValidator.isValidValue("Bearer abc.def-123"))
        assertTrue(HeaderValidator.isValidValue("tab\tok"))
        assertTrue(HeaderValidator.isValidValue(""))
        assertFalse("header injection", HeaderValidator.isValidValue("x\r\nSet-Cookie: y"))
        assertFalse(HeaderValidator.isValidValue("line\nbreak"))
        assertFalse(HeaderValidator.isValidValue("nul\u0000"))
        assertFalse("OkHttp would refuse to send it", HeaderValidator.isValidValue("café"))
    }

    @Test
    fun headersTheStackSetsItselfAreReserved() {
        listOf("Host", "content-length", "Range", "User-Agent", "Transfer-Encoding", "Connection").forEach {
            assertTrue(it, HeaderValidator.isReserved(it))
        }
        assertFalse(HeaderValidator.isReserved("Authorization"))
        assertFalse(HeaderValidator.isReserved("CF-Access-Client-Secret"))
    }

    @Test
    fun theFirstProblemIsReported() {
        assertNull(HeaderValidator.problem(listOf(HeaderEntry("A", "1"), HeaderEntry("B", "2"))))
        assertEquals(HeaderProblem.InvalidName, HeaderValidator.problem(listOf(HeaderEntry("bad name", "1"))))
        assertEquals(HeaderProblem.ReservedName, HeaderValidator.problem(listOf(HeaderEntry("Host", "x"))))
        assertEquals(HeaderProblem.InvalidValue, HeaderValidator.problem(listOf(HeaderEntry("A", "1\n2"))))
        assertEquals(
            "names compare case-insensitively",
            HeaderProblem.Duplicate,
            HeaderValidator.problem(listOf(HeaderEntry("X-Token", "1"), HeaderEntry("x-token", "2"))),
        )
    }
}
