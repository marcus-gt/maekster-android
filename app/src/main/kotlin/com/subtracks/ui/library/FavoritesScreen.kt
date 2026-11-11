package com.subtracks.ui.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.subtracks.R
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.source.StarType
import com.subtracks.ui.components.ContextMenuHost
import com.subtracks.ui.home.FavoriteKind
import com.subtracks.ui.home.HomeListRequest
import com.subtracks.ui.home.HomeListRoute
import com.subtracks.ui.theme.MaeksterSpacing
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The Favorites page in the Library tab: favorite songs, albums or artists, newest first. Songs play and shuffle as
 * the favorites queue. The server calls favorites stars; the app shows hearts, as on iOS.
 */
@Composable
fun FavoritesRoute(
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onViewAlbum: (String) -> Unit,
    onViewArtist: (String) -> Unit,
    setStar: (StarType, String, Boolean) -> Unit,
    contextMenuHost: ContextMenuHost? = null,
) {
    var kind by rememberSaveable { mutableStateOf(FavoriteKind.Songs) }
    val request = HomeListRequest(title = stringResource(R.string.resources_filter_starred), favorites = kind)
    HomeListRoute(
        request = request,
        onBack = onBack,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onViewAlbum = onViewAlbum,
        onViewArtist = onViewArtist,
        onGenreClick = {},
        onDecadeClick = {},
        contextMenuHost = contextMenuHost,
        setStar = setStar,
        // One view model per list, so switching never shows the previous list's items.
        viewModel = koinViewModel(key = "favorites-${kind.name}") { parametersOf(request) },
        topBarExtra = { FavoriteKindSelector(selected = kind, onSelect = { kind = it }) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoriteKindSelector(
    selected: FavoriteKind,
    onSelect: (FavoriteKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = MaeksterSpacing.l, vertical = MaeksterSpacing.s),
    ) {
        FavoriteKind.entries.forEachIndexed { index, kind ->
            SegmentedButton(
                selected = kind == selected,
                onClick = { onSelect(kind) },
                shape = SegmentedButtonDefaults.itemShape(index, FavoriteKind.entries.size),
            ) { Text(stringResource(kind.title)) }
        }
    }
}
