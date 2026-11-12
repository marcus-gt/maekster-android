package com.subtracks.ui.library

import android.content.res.Resources
import com.subtracks.R

/** A list's total length for its summary line ("38 min", "1 hr 12 min"); null under a minute. */
internal fun totalDurationText(
    resources: Resources,
    seconds: Long,
): String? {
    if (seconds < 60) return null
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> resources.getString(R.string.playlist_duration_minutes, totalMinutes)
        minutes == 0L -> resources.getString(R.string.playlist_duration_hours, hours)
        else -> resources.getString(R.string.playlist_duration_hours_minutes, hours, minutes)
    }
}
