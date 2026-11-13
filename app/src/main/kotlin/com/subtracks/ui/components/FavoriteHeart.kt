package com.subtracks.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.subtracks.R
import com.subtracks.data.model.Song

/**
 * Opens the quick playlist menu for a song from a long-press on its heart; null where quick playlists are
 * unavailable (offline, or outside the app shell), and the heart then only toggles the favorite.
 */
val LocalQuickPlaylists = staticCompositionLocalOf<((Song) -> Unit)?> { null }

/** A song's heart: tap toggles the favorite; when [onLongPress] is set, touch and hold opens quick playlists. */
@Composable
fun FavoriteHeart(
    favorite: Boolean,
    onToggle: () -> Unit,
    buttonSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
) {
    Box(
        modifier =
            modifier
                .size(buttonSize)
                .clip(CircleShape)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onLongClickLabel = onLongPress?.let { stringResource(R.string.quick_playlists_title) },
                    onLongClick = onLongPress,
                    onClick = onToggle,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(if (favorite) R.string.actions_unstar else R.string.actions_star),
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize),
        )
    }
}
