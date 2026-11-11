package com.subtracks.data.net

import com.subtracks.R
import com.subtracks.data.source.subsonic.HttpStatusException
import com.subtracks.data.source.subsonic.SubsonicException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

class ConnectionErrorsTest {
    private fun messageId(failure: Throwable) = connectionErrorMessage(failure)?.resId

    @Test
    fun networkFailuresSayWhatToCheck() {
        assertEquals(R.string.error_connection_unknown_host, messageId(UnknownHostException("music.example")))
        assertEquals(R.string.error_connection_refused, messageId(ConnectException("refused")))
        assertEquals(R.string.error_connection_timeout, messageId(SocketTimeoutException("timeout")))
    }

    @Test
    fun anUntrustedCertificateIsToldApartFromAFailedHandshake() {
        // How Android reports a certificate with no trusted anchor.
        val untrusted =
            SSLHandshakeException("handshake").apply {
                initCause(CertificateException("Trust anchor not found", CertPathValidatorException("no anchor")))
            }

        assertEquals(R.string.error_connection_certificate, messageId(untrusted))
        assertEquals(
            R.string.error_connection_certificate,
            messageId(SSLHandshakeException("handshake").apply { initCause(CertPathValidatorException("no anchor")) }),
        )
        assertEquals("e.g. a plain-HTTP server", R.string.error_connection_tls, messageId(SSLException("Unable to parse TLS packet")))
    }

    @Test
    fun theUsefulCauseIsFoundUnderWrappers() {
        assertEquals(R.string.error_connection_unknown_host, messageId(IOException("wrapped", UnknownHostException("x"))))
    }

    @Test
    fun httpStatusesBeforeAnySubsonicAnswerAreExplained() {
        assertEquals(R.string.error_http_denied, messageId(HttpStatusException(401)))
        assertEquals(R.string.error_http_denied, messageId(HttpStatusException(403)))
        assertEquals(R.string.error_http_not_found, messageId(HttpStatusException(404)))
        assertEquals(R.string.error_http_server, messageId(HttpStatusException(502)))
        assertEquals(R.string.error_http, messageId(HttpStatusException(418)))
    }

    @Test
    fun anythingElseIsLeftToTheRawMessage() {
        assertNull(connectionErrorMessage(IllegalStateException("boom")))
        assertNull("a server error without a message of ours", connectionErrorMessage(SubsonicException(40, "Wrong username")))
    }
}
