package com.subtracks.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.subtracks.data.db.LetterBucket

/**
 * A list with a column of letters beside it; a letter jumps to its first row. [railBottomPadding] lets the last
 * letters scroll clear of anything floating over the list's bottom edge, such as the shuffle button.
 */
@Composable
internal fun LetterIndexedContent(
    buckets: List<LetterBucket>,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
    railBottomPadding: Dp = 0.dp,
    content: @Composable (Modifier) -> Unit,
) {
    Row(modifier) {
        content(Modifier.weight(1f))
        if (buckets.isNotEmpty()) {
            Column(
                Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = railBottomPadding),
            ) {
                buckets.forEach { bucket ->
                    TextButton(
                        onClick = { onJump(bucket.position) },
                        modifier = Modifier.testTag("letter-rail-${bucket.letter}"),
                    ) { Text(bucket.letter) }
                }
            }
        }
    }
}
