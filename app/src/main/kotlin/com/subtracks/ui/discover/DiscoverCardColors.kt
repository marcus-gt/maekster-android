package com.subtracks.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subtracks.data.audiomuse.RadioMood
import com.subtracks.ui.theme.ArtworkColors
import com.subtracks.ui.theme.MaeksterTheme
import com.subtracks.ui.theme.artworkColorsFromSeeds

internal val VIVID_TILE = 152.dp

/** Text and icons on a vivid gradient: always white, like other artwork-led surfaces. */
internal val VividCardText = Color.White

/**
 * A vivid diagonal gradient for a Discover card. Every stop is an artwork accent or gradient tone, which
 * [artworkColorsFromSeeds] caps in luminance so white text and icons on it keep at least 4.5:1 contrast.
 */
internal fun ArtworkColors.discoverCardBrush(): Brush =
    Brush.linearGradient(
        colors = discoverCardStops(),
        start = Offset.Zero,
        end = Offset.Infinite,
    )

/** The gradient stops of [discoverCardBrush]. */
internal fun ArtworkColors.discoverCardStops(): List<Color> = listOf(accents[0], accents[1], gradientHigh)

/** Song Path keeps the cool blue-teal of the iOS card, whatever the accent. */
internal fun songPathColors(): ArtworkColors = artworkColorsFromSeeds(hueSeed(205f), hueSeed(178f))

/** A darker glass layer for panels on a vivid card, so text on it keeps its contrast. */
internal val VividPanel = Color.Black.copy(alpha = 0.22f)

/** Each mood keeps its own pair of hues, so the row reads as distinct radios rather than one colour. */
internal fun RadioMood.cardColors(): ArtworkColors {
    val (primary, secondary) =
        when (this) {
            RadioMood.NIGHT -> 250f to 175f
            RadioMood.ENERGETIC -> 12f to 215f
            RadioMood.WORKOUT -> 355f to 32f
            RadioMood.CHILL -> 168f to 280f
            RadioMood.FOCUS -> 222f to 150f
        }
    return artworkColorsFromSeeds(hueSeed(primary), hueSeed(secondary))
}

/** Smart Shuffle follows the active accent (Material You aware) and the secondary accent token. */
@Composable
internal fun smartShuffleColors(): ArtworkColors {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaeksterTheme.colors.accentSecondary
    return remember(primary, secondary) { artworkColorsFromSeeds(primary.toArgb(), secondary.toArgb()) }
}

/** A card whose own surface is transparent; the content paints the gradient. Disabled keeps the colours. */
@Composable
internal fun vividCardColors(): CardColors =
    CardDefaults.cardColors(
        containerColor = Color.Transparent,
        contentColor = VividCardText,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = VividCardText.copy(alpha = 0.7f),
    )

/**
 * A square gradient tile with a centred visual, and the title and caption below it on the page
 * surface. The whole card is one button, so TalkBack reads the title and caption with the action.
 */
@Composable
internal fun VividTileCard(
    onClick: () -> Unit,
    enabled: Boolean,
    brush: Brush,
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    visual: @Composable BoxScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(brush),
                contentAlignment = Alignment.Center,
            ) { visual() }
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (detail == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!detail.isNullOrEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun hueSeed(hue: Float): Int = Color.hsl(hue, 0.7f, 0.45f).toArgb()
