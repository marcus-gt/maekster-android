package com.subtracks.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.subtracks.R
import com.subtracks.data.model.FreshRelease
import com.subtracks.data.musicbrainz.NewReleaseMatching
import com.subtracks.ui.theme.artworkColorsFromSeed
import com.subtracks.ui.theme.placeholderSeed
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** New releases by library artists, newest first; each opens its MusicBrainz page. */
@Composable
fun FreshReleasesShelf(
    releases: List<FreshRelease>,
    modifier: Modifier = Modifier,
    showCovers: Boolean = true,
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.fresh_releases_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(releases, key = { it.id }) { release ->
                val brush = remember(release.artistName) { artworkColorsFromSeed(placeholderSeed(release.artistName)).discoverCardBrush() }
                VividTileCard(
                    onClick = { uriHandler.openUri(release.pageUrl()) },
                    enabled = true,
                    brush = brush,
                    title = release.title,
                    caption = release.artistName,
                    detail = listOfNotNull(release.typeLabel, releaseDate(release.firstReleaseDate)).joinToString(" · "),
                    modifier = Modifier.width(VIVID_TILE),
                ) {
                    // The icon shows until a cover loads, and stays when there is none.
                    Icon(Icons.Rounded.Album, contentDescription = null, tint = VividCardText, modifier = Modifier.size(56.dp))
                    if (showCovers) {
                        AsyncImage(
                            model = release.coverUrl(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** A full date in the user's format; a partial MusicBrainz date ("2026-09") as written. */
private fun releaseDate(value: String?): String? {
    if (value.isNullOrEmpty()) return null
    if (value.length < 10) return value
    return NewReleaseMatching.date(value)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: value
}
