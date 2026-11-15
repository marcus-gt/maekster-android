package com.subtracks.ui.settings

import androidx.annotation.StringRes
import com.subtracks.R
import com.subtracks.data.prefs.StreamQuality

/**
 * The stream and download quality choices offered first, as on iOS (`StreamQuality`): the original file,
 * or server-transcoded MP3 at 320 or 192 kbps. Anything else is "Custom" (any bitrate and format).
 */
enum class QualityPreset(
    val quality: StreamQuality,
    @param:StringRes val label: Int,
) {
    Original(StreamQuality(), R.string.quality_original),
    Mp3High(StreamQuality(maxBitrate = 320, format = "mp3"), R.string.quality_mp3_320),
    Mp3Standard(StreamQuality(maxBitrate = 192, format = "mp3"), R.string.quality_mp3_192),
    ;

    companion object {
        /** The preset [quality] matches, or null when it is a custom choice. */
        fun of(quality: StreamQuality): QualityPreset? {
            val normalized = quality.copy(format = quality.format?.takeIf { it.isNotEmpty() })
            return entries.firstOrNull { it.quality == normalized }
        }
    }
}
