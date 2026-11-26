package com.subtracks.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkMode(
    val key: String,
) {
    Wifi("wifi"),
    Mobile("mobile"),
}

internal fun networkModeFor(
    metered: Boolean?,
    last: NetworkMode,
): NetworkMode =
    when (metered) {
        true -> NetworkMode.Mobile
        false -> NetworkMode.Wifi
        null -> last
    }

fun networkMode(context: Context): Flow<NetworkMode> {
    val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    return callbackFlow {
        var last = NetworkMode.Wifi

        fun push() {
            val metered = connectivity?.let { if (it.activeNetwork == null) null else it.isActiveNetworkMetered }
            last = networkModeFor(metered, last)
            this@callbackFlow.trySend(last)
        }
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = push()

                override fun onLost(network: Network) = push()

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) = push()
            }
        val registered = runCatching { connectivity?.registerDefaultNetworkCallback(callback) }.isSuccess
        push()
        awaitClose { if (registered) runCatching { connectivity?.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}

/** What matters about one network for reaching the server. */
internal data class NetworkFacts(
    val internet: Boolean,
    val vpn: Boolean,
)

/**
 * Whether the server may be reachable. INTERNET, not VALIDATED: a LAN-only server is reachable without internet
 * validation. A VPN (Tailscale, WireGuard) stays connected and validated in airplane mode with nothing under it,
 * so it only counts while a real network is up too.
 */
internal fun hasUsableNetwork(
    active: NetworkFacts?,
    all: List<NetworkFacts>,
): Boolean = active?.internet == true && (!active.vpn || all.any { it.internet && !it.vpn })

private fun NetworkCapabilities.facts() =
    NetworkFacts(
        internet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        vpn = hasTransport(NetworkCapabilities.TRANSPORT_VPN),
    )

private fun ConnectivityManager?.hasNetwork(): Boolean {
    val connectivity = this ?: return false
    val active = connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }?.facts()
    if (active?.vpn != true) return hasUsableNetwork(active, emptyList())
    // The VPN's own capabilities hide whether anything is under it, so look at the other networks.
    @Suppress("DEPRECATION")
    val all = connectivity.allNetworks.mapNotNull { connectivity.getNetworkCapabilities(it)?.facts() }
    return hasUsableNetwork(active, all)
}

/** Whether a network is up right now. Unlike [networkAvailable], it never reports a stale starting value. */
fun isNetworkAvailable(context: Context): Boolean =
    runCatching { context.applicationContext.getSystemService(ConnectivityManager::class.java).hasNetwork() }
        .getOrDefault(true)

fun networkAvailable(context: Context): Flow<Boolean> {
    val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    // Conflated, not distinct: switching between two available networks must still re-notify so the
    // replay retries against the (possibly different) newly active route to the server.
    return callbackFlow {
        fun push() {
            trySend(connectivity.hasNetwork())
        }
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = push()

                override fun onLost(network: Network) = push()

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) = push()
            }
        val registered = runCatching { connectivity?.registerDefaultNetworkCallback(callback) }.isSuccess
        // A VPN can stay the default network while Wi-Fi or mobile data come and go under it.
        val physical =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = push()

                override fun onLost(network: Network) = push()
            }
        // Requests exclude VPNs by default (NOT_VPN), so this follows the real networks only.
        val physicalRequest = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        val physicalRegistered = runCatching { connectivity?.registerNetworkCallback(physicalRequest, physical) }.isSuccess
        push()
        awaitClose {
            if (registered) runCatching { connectivity?.unregisterNetworkCallback(callback) }
            if (physicalRegistered) runCatching { connectivity?.unregisterNetworkCallback(physical) }
        }
    }.conflate()
}
