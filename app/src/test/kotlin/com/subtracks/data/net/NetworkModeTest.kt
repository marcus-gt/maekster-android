package com.subtracks.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModeTest {
    @Test
    fun meteredIsMobileAndUnmeteredIsWifi() {
        assertEquals(NetworkMode.Mobile, networkModeFor(true, NetworkMode.Wifi))
        assertEquals(NetworkMode.Wifi, networkModeFor(false, NetworkMode.Mobile))
    }

    @Test
    fun noActiveNetworkKeepsTheLastMode() {
        assertEquals(NetworkMode.Wifi, networkModeFor(null, NetworkMode.Wifi))
        assertEquals(NetworkMode.Mobile, networkModeFor(null, NetworkMode.Mobile))
    }

    private val wifi = NetworkFacts(internet = true, vpn = false)
    private val vpn = NetworkFacts(internet = true, vpn = true)

    @Test
    fun aVpnLeftConnectedInAirplaneModeIsNoNetwork() {
        // Tailscale in airplane mode: the VPN is the default network, validated, with nothing under it.
        assertFalse(hasUsableNetwork(vpn, listOf(vpn)))
    }

    @Test
    fun aVpnOverARealNetworkCounts() {
        assertTrue(hasUsableNetwork(vpn, listOf(vpn, wifi)))
    }

    @Test
    fun aRealNetworkCountsAndNoneDoesNot() {
        assertTrue(hasUsableNetwork(wifi, emptyList()))
        assertFalse(hasUsableNetwork(null, emptyList()))
        assertFalse(hasUsableNetwork(NetworkFacts(internet = false, vpn = false), emptyList()))
    }
}
