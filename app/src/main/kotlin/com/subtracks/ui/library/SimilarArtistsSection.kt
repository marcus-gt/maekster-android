package com.subtracks.ui.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import com.subtracks.data.model.Artist
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.musicbrainz.NewReleaseMatching
import com.subtracks.data.recommendations.SimilarArtist
import com.subtracks.data.repo.SourceRepository
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.components.ArtistIcon
import com.subtracks.ui.components.artistArt
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SimilarArtistsSectionRoute(
    artistId: String,
    onViewArtist: (String) -> Unit,
) {
    val model: SimilarArtistsViewModel = koinViewModel(key = "similar:$artistId") { parametersOf(artistId) }
    val state by model.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val sources: SourceRepository = koinInject()
    SimilarArtistsSection(state, model::refresh, onViewArtist, { uriHandler.openUri(it) }, { sources.artistArt(it) })
}

/** An artist outside the library: its photo, when one was found, is the only image it can have. */
internal fun SimilarArtist.externalArtist(
    sourceId: Long,
    photo: String?,
): Pair<Artist, ArtistArt> =
    Artist(sourceId, "external:$name", name, 0, null) to
        ArtistArt(image = photo?.let { CoverArtRef(it, "external-artist:$it") })

internal fun SimilarArtist.musicBrainzUrl(): String =
    musicBrainzId?.takeIf(NewReleaseMatching::isMusicBrainzId)?.let { "https://musicbrainz.org/artist/$it" }
        ?: "https://musicbrainz.org/search?query=${Uri.encode(name)}&type=artist&method=indexed"

private val ART_SIZE = 148.dp

@Composable
fun SimilarArtistsSection(
    state: SimilarArtistsState,
    onRefresh: () -> Unit,
    onViewArtist: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    artistArt: (Artist) -> ArtistArt = { ArtistArt() },
) {
    if (state.offline && state.artists.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.similar_artists_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (!state.offline) {
            TextButton(onClick = onRefresh, enabled = !state.loading && !state.busy, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(if (state.busy) R.string.similar_artists_loading else R.string.similar_artists_refresh))
            }
        }
        if (state.checked && state.artists.isEmpty()) {
            Text(stringResource(R.string.similar_artists_empty), modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (state.artists.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(state.artists) { _, resolved ->
                    val local = resolved.libraryArtist
                    Card(
                        onClick = {
                            if (local !=
                                null
                            ) {
                                onViewArtist(local.id)
                            } else {
                                onOpenExternal(resolved.recommendation.musicBrainzUrl())
                            }
                        },
                        modifier = Modifier.width(180.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val (artist, art) =
                                if (local != null) {
                                    local to artistArt(local)
                                } else {
                                    resolved.recommendation.externalArtist(state.sourceId ?: 0, state.photos[resolved.recommendation])
                                }
                            ArtistIcon(artist, art, Modifier.size(ART_SIZE).clip(CircleShape))
                            Text(local?.name ?: resolved.recommendation.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(if (local != null) R.string.similar_artists_local else R.string.similar_artists_external),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
