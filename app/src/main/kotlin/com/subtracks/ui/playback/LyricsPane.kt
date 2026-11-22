package com.subtracks.ui.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.subtracks.R
import com.subtracks.data.lyrics.LyricsDisplay
import com.subtracks.data.lyrics.LyricsProviderId
import com.subtracks.data.lyrics.LyricsState
import java.util.Locale

const val LYRICS_PANE_TAG = "lyrics-pane"
const val LYRICS_PREVIEW_TAG = "lyrics-preview"

fun lyricsLineTag(index: Int) = "lyrics-line-$index"

private const val NOTE = "♪"
private const val PREVIEW_MIN_SCALE = 0.7f
private const val PREVIEW_SCALE_STEP = 0.02f

/** The current synced line, or null when the lyrics have no timing or nothing has been sung yet. */
fun LyricsUi.currentLineText(positionMs: Long): String? {
    val shown = display ?: return null
    val index = shown.timing.currentLine(positionMs) ?: return null
    return shown.lyrics.lyrics.lines
        .getOrNull(index)
        ?.text
        ?.takeIf { it.isNotBlank() }
}

/** Every line of the song's lyrics, following the music when they are synced, in place of the cover. */
@Composable
fun LyricsPane(
    lyrics: LyricsUi,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.testTag(LYRICS_PANE_TAG), contentAlignment = Alignment.Center) {
        when (val state = lyrics.state) {
            LyricsState.Idle -> {}

            LyricsState.Loading -> {
                CircularProgressIndicator()
            }

            is LyricsState.Missing -> {
                LyricsMessage(stringResource(if (state.offline) R.string.lyrics_none_offline else R.string.lyrics_none))
            }

            LyricsState.Failed -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LyricsMessage(stringResource(R.string.lyrics_failed))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.lyrics_retry)) }
                }
            }

            is LyricsState.Found -> {
                lyrics.display?.let { LyricsLines(it, positionMs, durationMs, onSeek, onSelectLanguage) }
            }
        }
    }
}

@Composable
private fun LyricsMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun LyricsLines(
    display: LyricsDisplay,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onSelectLanguage: (String) -> Unit,
) {
    val lyrics = display.lyrics.lyrics
    if (lyrics.instrumental || lyrics.lines.isEmpty()) {
        LyricsMessage(stringResource(R.string.lyrics_instrumental))
        return
    }
    val current = if (lyrics.synced) display.timing.currentLine(positionMs) else null
    val listState = remember(display.lyrics) { LazyListState() }
    var following by remember(display.lyrics) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) following = false }
    }
    LaunchedEffect(listState, current, following) {
        if (following && current != null) listState.centerOn(current)
    }
    val seekLabel = stringResource(R.string.lyrics_seek_line)
    Column(Modifier.fillMaxSize()) {
        if (display.languages.size > 1) LanguageMenu(display, onSelectLanguage)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                // Synced lyrics get half the pane above and below, so the first and last lines can reach the middle.
                contentPadding = PaddingValues(vertical = if (lyrics.synced) maxHeight / 2 else 48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize().fadingEdges(),
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val seekTo = display.timing.seekPosition(index, durationMs.takeIf { it > 0 })
                    val color by animateColorAsState(
                        when {
                            !lyrics.synced || index == current -> MaterialTheme.colorScheme.onBackground
                            else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        },
                        label = "lyric-line-color",
                    )
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(lyricsLineTag(index))
                                .then(if (lyrics.synced) Modifier.semantics { selected = index == current } else Modifier)
                                .then(
                                    if (seekTo != null) {
                                        Modifier.clickable(onClickLabel = seekLabel) {
                                            following = true
                                            onSeek(seekTo)
                                        }
                                    } else {
                                        Modifier
                                    },
                                ).padding(vertical = 4.dp),
                    )
                }
                if (display.lyrics.provider == LyricsProviderId.Lrclib) {
                    item {
                        Text(
                            text = stringResource(R.string.lyrics_from_lrclib),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
            FollowButton(
                visible = !following && current != null,
                onClick = { following = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
        }
    }
}

/** Scrolls so the middle of line [index] sits in the middle of the list. */
private suspend fun LazyListState.centerOn(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val itemCenter = item.offset - layoutInfo.viewportStartOffset + item.size / 2
    animateScrollBy((itemCenter - layoutInfo.viewportSize.height / 2).toFloat())
}

private fun Modifier.fadingEdges(): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.15f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

/** Scrolling by hand stops the lyrics following the music; this brings them back. */
@Composable
private fun FollowButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        FilledTonalButton(onClick = onClick) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.lyrics_follow), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun LanguageMenu(
    display: LyricsDisplay,
    onSelectLanguage: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.lyrics_language)
    val original = stringResource(R.string.lyrics_language_original)
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(languageName(display.language, original), modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            display.languages.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(languageName(tag, original)) },
                    onClick = {
                        expanded = false
                        onSelectLanguage(tag)
                    },
                )
            }
        }
    }
}

private fun languageName(
    tag: String,
    original: String,
): String {
    if (tag == "und") return original
    val name = Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault())
    return name.ifBlank { tag }.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/** The line being sung, between the track details and the scrubber; tapping it opens the lyrics. */
@Composable
fun CurrentLyricLine(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic)
    val height = with(LocalDensity.current) { style.lineHeight.toDp() } + 8.dp
    val label = stringResource(R.string.lyrics_current_line)
    val action = stringResource(R.string.lyrics_show_all)
    val color = LocalContentColor.current.copy(alpha = 0.75f)
    // A fixed height keeps the controls below still as lines come and go.
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Crossfade(text, label = "lyric-preview") { line ->
            if (line != null) {
                val shown = "$NOTE $line $NOTE"
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val measurer = rememberTextMeasurer()
                    val available = constraints.maxWidth
                    val fontSize = remember(shown, style, available) { previewFontSize(measurer, shown, style, available) }
                    Text(
                        text = shown,
                        style = style,
                        fontSize = fontSize,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        onTextLayout = onTextLayout,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(LYRICS_PREVIEW_TAG)
                                .clickable(onClickLabel = action, onClick = onClick)
                                .semantics {
                                    contentDescription = label
                                    stateDescription = line
                                },
                    )
                }
            }
        }
    }
}

/**
 * As on iOS, a long line shrinks (down to 70%) rather than losing its ending; only a line too long even then is cut
 * off with an ellipsis. Width grows with the font size, so the fitting size is found by scaling, then checked.
 */
private fun previewFontSize(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    available: Int,
): TextUnit {
    fun width(size: TextUnit) = measurer.measure(text, style.copy(fontSize = size), maxLines = 1, softWrap = false).size.width
    val full = width(style.fontSize)
    if (full <= available) return style.fontSize
    var scale = (available.toFloat() / full).coerceIn(PREVIEW_MIN_SCALE, 1f)
    while (scale > PREVIEW_MIN_SCALE && width(style.fontSize * scale) > available) {
        scale = (scale - PREVIEW_SCALE_STEP).coerceAtLeast(PREVIEW_MIN_SCALE)
    }
    return style.fontSize * scale
}
