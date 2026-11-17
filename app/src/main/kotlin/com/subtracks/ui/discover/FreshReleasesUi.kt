package com.subtracks.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                Card(onClick = { uriHandler.openUri(release.pageUrl()) }, modifier = Modifier.width(152.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val cover =
                            Modifier
                                .size(128.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        if (showCovers) {
                            AsyncImage(
                                model = release.coverUrl(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = cover,
                            )
                        } else {
                            Box(cover)
                        }
                        Text(
                            release.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            release.artistName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(release.typeLabel, releaseDate(release.firstReleaseDate)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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

/** The opt-in switch, saying exactly what is sent where. */
@Composable
fun FreshReleasesSwitch(
    enabled: Boolean,
    preparing: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.fresh_releases_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.fresh_releases_disclosure),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enabled && preparing) {
                Text(stringResource(R.string.fresh_releases_preparing), style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}
