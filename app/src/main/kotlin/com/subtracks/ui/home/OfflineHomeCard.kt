package com.subtracks.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.subtracks.R

const val OFFLINE_HOME_CARD_TAG = "offline-home-card"

/**
 * Leads offline Home (iOS OfflineHomeCard): offline mode is on, how many songs can play, a way into them,
 * and turning offline mode off. [songCount] is null while counting.
 */
@Composable
fun OfflineHomeCard(
    songCount: Int?,
    onBrowse: () -> Unit,
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().testTag(OFFLINE_HOME_CARD_TAG),
    ) {
        Column {
            val canBrowse = (songCount ?: 0) > 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canBrowse, onClick = onBrowse)
                        .padding(16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                ) {
                    Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_offline_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            songCount == null -> stringResource(R.string.home_offline_loading)
                            songCount > 0 -> pluralStringResource(R.plurals.home_offline_songs, songCount, songCount)
                            else -> stringResource(R.string.home_offline_nothing)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canBrowse) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            TextButton(onClick = onTurnOff, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.home_offline_turn_off))
            }
        }
    }
}
