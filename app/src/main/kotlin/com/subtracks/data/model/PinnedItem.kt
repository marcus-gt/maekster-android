package com.subtracks.data.model

data class PinRef(
    val sourceId: Long,
    val kind: PinKind,
    val refId: String,
)

data class PinnedItem(
    val sourceId: Long,
    val kind: PinKind,
    val refId: String,
    val name: String,
    val subtitle: String?,
    val coverArt: String?,
) {
    val key: String get() = "$sourceId:${kind.name}:$refId"
}
