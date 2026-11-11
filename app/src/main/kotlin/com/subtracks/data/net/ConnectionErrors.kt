package com.subtracks.data.net

import com.subtracks.R
import com.subtracks.UiException
import com.subtracks.UiMessage
import com.subtracks.data.source.subsonic.SubsonicException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * What to tell the user about a failed connection, or null when nothing more specific than the raw message is known.
 * Walks the cause chain, because OkHttp and the TLS stack wrap the useful exception.
 */
fun connectionErrorMessage(failure: Throwable): UiMessage? {
    var current: Throwable? = failure
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        messageFor(current)?.let { return it }
        current = current.cause
    }
    return null
}

private fun messageFor(failure: Throwable): UiMessage? =
    when (failure) {
        is UiException -> {
            failure.uiMessage
        }

        is SubsonicException -> {
            failure.uiMessage
        }

        is UnknownHostException -> {
            UiMessage(R.string.error_connection_unknown_host)
        }

        is ConnectException, is NoRouteToHostException -> {
            UiMessage(R.string.error_connection_refused)
        }

        is SocketTimeoutException -> {
            UiMessage(R.string.error_connection_timeout)
        }

        is SSLPeerUnverifiedException, is CertificateException, is CertPathValidatorException -> {
            UiMessage(R.string.error_connection_certificate)
        }

        // A handshake that failed on the certificate says so further down the chain; any other one, such as a
        // plain-HTTP server answering a TLS handshake, gets the secure-connection message.
        is SSLException -> {
            if (failure.cause is CertificateException || failure.cause is CertPathValidatorException) {
                null
            } else {
                UiMessage(R.string.error_connection_tls)
            }
        }

        else -> {
            null
        }
    }
