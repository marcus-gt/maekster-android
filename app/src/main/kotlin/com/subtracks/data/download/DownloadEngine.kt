package com.subtracks.data.download

import com.subtracks.data.model.DownloadError
import com.subtracks.data.net.HeaderEntry

data class EngineRequest(
    val uri: String,
    val path: String,
    val title: String,
    val allowMetered: Boolean = false,
    // The server's custom headers, already limited to [uri]'s origin.
    val headers: List<HeaderEntry> = emptyList(),
)

enum class EngineStatus { Pending, Running, Completed, Failed }

data class EngineDownload(
    val status: EngineStatus,
    val bytes: Long = 0,
    val total: Long = 0,
    val error: DownloadError? = null,
)

interface DownloadEngine {
    fun enqueue(request: EngineRequest): Long

    fun download(id: Long): EngineDownload?

    fun cancel(ids: List<Long>)
}
