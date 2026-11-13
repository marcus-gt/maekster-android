package com.subtracks.data.repo

data class RetainedQueueOrder(
    val sourceId: Long,
    val prefix: ShufflePrefix,
    val entries: List<ResolvedQueueEntry>,
)
