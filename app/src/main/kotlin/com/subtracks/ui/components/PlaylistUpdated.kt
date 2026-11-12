package com.subtracks.ui.components

import android.content.res.Resources
import android.text.format.DateUtils
import com.subtracks.R

/**
 * "Updated 2 days ago" for a playlist's last change ([changedEpochSeconds], as synced), then a plain date once it is
 * more than a week old. Null when the server sent no change date.
 */
fun playlistUpdatedText(
    resources: Resources,
    changedEpochSeconds: Long,
    now: Long = System.currentTimeMillis(),
): String? {
    if (changedEpochSeconds <= 0) return null
    val relative = DateUtils.getRelativeTimeSpanString(changedEpochSeconds * 1000, now, DateUtils.DAY_IN_MILLIS)
    return resources.getString(R.string.playlist_updated, relative)
}
