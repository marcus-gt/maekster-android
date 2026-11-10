package com.subtracks

/**
 * The single source for every name the app shows or sends to another service.
 *
 * In-app text says "maekster" (all lowercase). The launcher label and the Home header say "mækster"; both
 * read `app_name`, so the æ spelling lives in one string.
 */
object MaeksterBrand {
    /** The product name in app text. */
    const val NAME = "maekster"

    /** The client name sent to external services: the Subsonic `c` parameter and the User-Agent product token. */
    const val CLIENT_ID = "maekster"

    const val REPOSITORY_URL = "https://github.com/marcus-gt/maekster-android"

    fun userAgent(
        androidRelease: String,
        versionName: String,
    ): String = "$CLIENT_ID (Android $androidRelease; $versionName)"
}
