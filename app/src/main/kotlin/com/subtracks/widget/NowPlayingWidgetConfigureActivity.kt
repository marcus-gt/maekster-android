package com.subtracks.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.subtracks.R
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.context.GlobalContext

/**
 * A Now Playing widget's settings: what its large size shows below the player (UX-45). Opens from the widget
 * (long-press, then settings); a new widget skips it and shows the mood row. Only the app's own Now Playing widgets
 * can be configured here.
 */
class NowPlayingWidgetConfigureActivity : ComponentActivity() {
    private val preferences: UserPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: INVALID
        // Leaving without a choice keeps the widget as it is.
        setResult(RESULT_CANCELED, result(appWidgetId))
        if (!isOwnWidget(appWidgetId)) {
            finish()
            return
        }
        val current = preferences.nowPlayingWidgetRows().map { NowPlayingWidgetRow.of(it[appWidgetId]) }
        setContent {
            MaeksterTheme {
                val chosen by current.collectAsState(initial = null)
                NowPlayingRowChoice(chosen) { row ->
                    lifecycleScope.launch {
                        preferences.setNowPlayingWidgetRow(appWidgetId, row.name)
                        GlobalContext.getOrNull()?.getOrNull<NowPlayingWidgetUpdater>()?.refresh()
                        setResult(RESULT_OK, result(appWidgetId))
                        finish()
                    }
                }
            }
        }
    }

    private fun isOwnWidget(appWidgetId: Int): Boolean {
        if (appWidgetId == INVALID) return false
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId) ?: return false
        return info.provider == ComponentName(this, NowPlayingWidget::class.java)
    }

    private fun result(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private companion object {
        const val INVALID = AppWidgetManager.INVALID_APPWIDGET_ID
    }
}

/** The choices as a radio list; a tap picks and saves. [chosen] is null while the saved choice loads. */
@Composable
internal fun NowPlayingRowChoice(
    chosen: NowPlayingWidgetRow?,
    onPick: (NowPlayingWidgetRow) -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            Text(
                stringResource(R.string.widget_row_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.widget_row_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            )
            Column(Modifier.selectableGroup()) {
                NowPlayingWidgetRow.entries.forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = row == chosen, role = Role.RadioButton, onClick = { onPick(row) })
                            .padding(horizontal = 16.dp)
                            .testTag("widgetRow.${row.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = row == chosen, onClick = null)
                        Text(
                            stringResource(row.title),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
