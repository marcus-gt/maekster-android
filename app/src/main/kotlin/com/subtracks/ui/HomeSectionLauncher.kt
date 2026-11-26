package com.subtracks.ui

import com.subtracks.ui.home.HomeSection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

internal class HomeSectionLauncher {
    private val requests = Channel<HomeSection>(Channel.CONFLATED)
    val sections = requests.receiveAsFlow()

    fun request(section: HomeSection) {
        requests.trySend(section)
    }
}
