package com.subtracks.ui.settings

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.BuildConfig
import com.subtracks.MaeksterBrand
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.UiMessages
import com.subtracks.data.model.Source
import com.subtracks.data.prefs.DEFAULT_SYNC_INTERVAL_MINUTES
import com.subtracks.data.prefs.MAX_REPLAY_GAIN_PREAMP_DB
import com.subtracks.data.prefs.MIN_REPLAY_GAIN_PREAMP_DB
import com.subtracks.data.prefs.ReplayGainMode
import com.subtracks.data.prefs.SortNameMode
import com.subtracks.data.prefs.StreamQuality
import com.subtracks.data.prefs.SyncMode
import com.subtracks.data.prefs.ThemeMode
import com.subtracks.log.Log
import com.subtracks.ui.components.DismissOnRequest
import com.subtracks.ui.components.rememberViewportFill
import com.subtracks.ui.theme.MaeksterSpacing
import com.subtracks.ui.theme.materialYouSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val bitrateOptions = listOf(0, 24, 32, 64, 96, 128, 192, 256, 320)
private val streamFormats = listOf(null, "mp3", "opus", "ogg", "webm", "aac", "flac")

private const val PROJECT_HOMEPAGE = MaeksterBrand.REPOSITORY_URL

/**
 * The Settings sub-pages, mirroring the iOS grouping. Servers and About stay on the main page. Integrations joins
 * once it has something to offer (M3); Android-only settings sit in their natural group.
 */
enum class SettingsPage(
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
    val icon: ImageVector,
) {
    Application(R.string.settings_application_name, R.string.settings_application_summary, Icons.Rounded.Palette),
    Playback(R.string.settings_music_name, R.string.settings_playback_summary, Icons.Rounded.PlayCircle),
    Storage(R.string.settings_storage_name, R.string.settings_storage_summary, Icons.Rounded.Storage),
}

/** The page a route argument names, or null for the main page. */
internal fun settingsPageFor(name: String?): SettingsPage? = SettingsPage.entries.firstOrNull { it.name == name }

private enum class SettingsDialog {
    WifiQuality,
    MobileQuality,
    DownloadQuality,
    SyncConcurrency,
    SyncInterval,
    SyncMode,
    ReplayGain,
    ReplayGainPreamp,
    SortNames,
    Theme,
}

@Composable
fun SettingsRoute(
    onAddServer: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLicenses: () -> Unit,
    onEditServer: (Long) -> Unit,
    onBack: () -> Unit,
    dismissals: Flow<Unit> = emptyFlow(),
    page: SettingsPage? = null,
    onOpenPage: (SettingsPage) -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
    uiMessages: UiMessages = koinInject(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val activeSourceId by viewModel.activeSourceId.collectAsStateWithLifecycle()
    val wifiQuality by viewModel.wifiQuality.collectAsStateWithLifecycle()
    val mobileQuality by viewModel.mobileQuality.collectAsStateWithLifecycle()
    val syncConcurrency by viewModel.syncConcurrency.collectAsStateWithLifecycle()
    val syncMode by viewModel.syncMode.collectAsStateWithLifecycle()
    val syncIntervalMinutes by viewModel.syncIntervalMinutes.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val downloadOverMetered by viewModel.downloadOverMetered.collectAsStateWithLifecycle()
    val scrobbling by viewModel.scrobbling.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val verboseLogging by viewModel.verboseLogging.collectAsStateWithLifecycle()
    val replayGainMode by viewModel.replayGainMode.collectAsStateWithLifecycle()
    val replayGainPreamp by viewModel.replayGainPreamp.collectAsStateWithLifecycle()
    val sortNameMode by viewModel.sortNameMode.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val materialYou by viewModel.materialYou.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SettingsScreen(
        sources = sources,
        activeSourceId = activeSourceId,
        wifiQuality = wifiQuality,
        mobileQuality = mobileQuality,
        syncConcurrency = syncConcurrency,
        syncMode = syncMode,
        syncInterval = syncIntervalMinutes,
        downloadQuality = downloadQuality,
        downloadOverMetered = downloadOverMetered,
        scrobbling = scrobbling,
        replayGainMode = replayGainMode,
        replayGainPreamp = replayGainPreamp,
        sortNameMode = sortNameMode,
        themeMode = themeMode,
        materialYou = materialYou,
        keepScreenOn = keepScreenOn,
        offline = offline,
        verboseLogging = verboseLogging,
        onSelectSource = viewModel::selectSource,
        onEditServer = onEditServer,
        onWifiQualityChange = viewModel::setWifiQuality,
        onMobileQualityChange = viewModel::setMobileQuality,
        onSyncConcurrencyChange = viewModel::setSyncConcurrency,
        onSyncModeChange = viewModel::setSyncMode,
        onSyncIntervalChange = viewModel::setSyncIntervalMinutes,
        onDownloadQualityChange = viewModel::setDownloadQuality,
        onDownloadOverMeteredChange = viewModel::setDownloadOverMetered,
        onScrobblingChange = viewModel::setScrobbling,
        onReplayGainModeChange = viewModel::setReplayGainMode,
        onReplayGainPreampChange = viewModel::setReplayGainPreamp,
        onSortNameModeChange = viewModel::setSortNameMode,
        onThemeModeChange = viewModel::setThemeMode,
        onMaterialYouChange = viewModel::setMaterialYou,
        onKeepScreenOnChange = viewModel::setKeepScreenOn,
        onOfflineChange = viewModel::setOfflineMode,
        onVerboseLoggingChange = viewModel::setVerboseLogging,
        onAddServer = onAddServer,
        onOpenDownloads = onOpenDownloads,
        onOpenLicenses = onOpenLicenses,
        onShareLogs = {
            scope.launch {
                val result =
                    runCatching {
                        val zip = withContext(Dispatchers.IO) { Log.exportZip() }
                        if (zip != null) Log.share(context, zip)
                        zip
                    }
                val message =
                    when {
                        result.isFailure -> R.string.settings_about_share_logs_failed
                        result.getOrNull() == null -> R.string.settings_about_share_logs_empty
                        else -> null
                    }
                message?.let { uiMessages.show(UiMessage(it)) }
            }
        },
        onBack = onBack,
        dismissals = dismissals,
        page = page,
        onOpenPage = onOpenPage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sources: List<Source>,
    activeSourceId: Long?,
    wifiQuality: StreamQuality,
    mobileQuality: StreamQuality,
    syncConcurrency: Int,
    downloadQuality: StreamQuality,
    downloadOverMetered: Boolean,
    scrobbling: Boolean,
    modifier: Modifier = Modifier,
    keepScreenOn: Boolean = false,
    offline: Boolean = false,
    verboseLogging: Boolean = false,
    syncMode: SyncMode = SyncMode.WifiOnly,
    syncInterval: Int = DEFAULT_SYNC_INTERVAL_MINUTES,
    replayGainMode: ReplayGainMode = ReplayGainMode.Auto,
    replayGainPreamp: Float = 0f,
    sortNameMode: SortNameMode = SortNameMode.Name,
    themeMode: ThemeMode = ThemeMode.System,
    materialYou: Boolean = false,
    materialYouAvailable: Boolean = materialYouSupported,
    onSelectSource: (Long) -> Unit,
    onEditServer: (Long) -> Unit,
    onWifiQualityChange: (StreamQuality) -> Unit,
    onMobileQualityChange: (StreamQuality) -> Unit,
    onSyncConcurrencyChange: (Int) -> Unit,
    onSyncModeChange: (SyncMode) -> Unit = {},
    onSyncIntervalChange: (Int) -> Unit = {},
    onDownloadQualityChange: (StreamQuality) -> Unit,
    onDownloadOverMeteredChange: (Boolean) -> Unit,
    onScrobblingChange: (Boolean) -> Unit,
    onReplayGainModeChange: (ReplayGainMode) -> Unit = {},
    onReplayGainPreampChange: (Float) -> Unit = {},
    onSortNameModeChange: (SortNameMode) -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onMaterialYouChange: (Boolean) -> Unit = {},
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onOfflineChange: (Boolean) -> Unit = {},
    onVerboseLoggingChange: (Boolean) -> Unit = {},
    onAddServer: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLicenses: () -> Unit,
    onShareLogs: () -> Unit,
    onBack: () -> Unit,
    dismissals: Flow<Unit> = emptyFlow(),
    page: SettingsPage? = null,
    onOpenPage: (SettingsPage) -> Unit = {},
) {
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    DismissOnRequest(dismissals) { dialog = null }
    val listState = rememberLazyListState()
    val fill = rememberViewportFill(listState)
    val uriHandler = LocalUriHandler.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(page?.title ?: R.string.navigation_tabs_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.navigation_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when (page) {
                null -> {
                    rootItems(
                        sources = sources,
                        activeSourceId = activeSourceId,
                        onSelectSource = onSelectSource,
                        onEditServer = onEditServer,
                        onAddServer = onAddServer,
                        onOpenPage = onOpenPage,
                        onOpenLicenses = onOpenLicenses,
                        onOpenHomepage = { runCatching { uriHandler.openUri(PROJECT_HOMEPAGE) } },
                    )
                }

                SettingsPage.Application -> {
                    item {
                        SwitchRow(
                            title = stringResource(R.string.settings_network_options_offline_mode),
                            description =
                                if (offline) {
                                    stringResource(R.string.settings_network_options_offline_mode_on)
                                } else {
                                    stringResource(R.string.settings_network_options_offline_mode_off)
                                },
                            checked = offline,
                            onCheckedChange = onOfflineChange,
                        )
                    }
                    item { SectionHeader(stringResource(R.string.settings_appearance_name)) }
                    item {
                        ValueRow(
                            title = stringResource(R.string.settings_theme_title),
                            value = themeModeLabel(LocalResources.current, themeMode),
                            onClick = { dialog = SettingsDialog.Theme },
                        )
                    }
                    if (materialYouAvailable) {
                        item {
                            SwitchRow(
                                title = stringResource(R.string.settings_material_you_title),
                                description = stringResource(R.string.settings_material_you_description),
                                checked = materialYou,
                                onCheckedChange = onMaterialYouChange,
                            )
                        }
                    }
                    item { SectionHeader(stringResource(R.string.settings_library_name)) }
                    item {
                        ValueRow(
                            title = stringResource(R.string.settings_sort_names_title),
                            value = sortNameModeLabel(LocalResources.current, sortNameMode),
                            onClick = { dialog = SettingsDialog.SortNames },
                        )
                    }
                    item { SectionHeader(stringResource(R.string.settings_troubleshooting_name)) }
                    item {
                        ActionRow(
                            title = stringResource(R.string.settings_about_actions_share_logs),
                            icon = Icons.Rounded.Share,
                            onClick = onShareLogs,
                        )
                    }
                    item {
                        SwitchRow(
                            title = stringResource(R.string.settings_about_verbose_logging),
                            description = stringResource(R.string.settings_about_verbose_logging_description),
                            checked = verboseLogging,
                            onCheckedChange = onVerboseLoggingChange,
                        )
                    }
                }

                SettingsPage.Playback -> {
                    item {
                        SwitchRow(
                            title = stringResource(R.string.settings_music_options_scrobble_title),
                            description = stringResource(R.string.settings_music_options_scrobble_description_on),
                            checked = scrobbling,
                            onCheckedChange = onScrobblingChange,
                        )
                    }
                    item {
                        SwitchRow(
                            title = stringResource(R.string.settings_music_options_keep_screen_on_title),
                            description = stringResource(R.string.settings_music_options_keep_screen_on_description),
                            checked = keepScreenOn,
                            onCheckedChange = onKeepScreenOnChange,
                        )
                    }
                    item { SectionHeader(stringResource(R.string.settings_streaming_name)) }
                    item {
                        ValueRow(
                            title = stringResource(R.string.settings_network_options_max_bitrate_wifi_title),
                            value = qualityLabel(wifiQuality),
                            onClick = { dialog = SettingsDialog.WifiQuality },
                        )
                    }
                    item {
                        ValueRow(
                            title = stringResource(R.string.settings_network_options_max_bitrate_mobile_title),
                            value = qualityLabel(mobileQuality),
                            onClick = { dialog = SettingsDialog.MobileQuality },
                        )
                    }
                    item { SectionHeader(stringResource(R.string.settings_volume_name)) }
                    item {
                        ValueRow(
                            title = stringResource(R.string.settings_replay_gain_title),
                            value = replayGainModeLabel(LocalResources.current, replayGainMode),
                            onClick = { dialog = SettingsDialog.ReplayGain },
                        )
                    }
                    item {
                        ValueRow(
                            title = stringResource(R.string.settings_replay_gain_preamp_title),
                            value = replayGainPreampLabel(LocalResources.current, replayGainPreamp),
                            onClick = { dialog = SettingsDialog.ReplayGainPreamp },
                        )
                    }
                }

                SettingsPage.Storage -> {
                    item { SectionHeader(stringResource(R.string.downloads_title)) }
                    item {
                        ValueRow(
                            title = stringResource(R.string.download_quality),
                            value = qualityLabel(downloadQuality),
                            onClick = { dialog = SettingsDialog.DownloadQuality },
                        )
                    }
                    item {
                        SwitchRow(
                            title = stringResource(R.string.download_over_mobile),
                            description =
                                if (downloadOverMetered) {
                                    stringResource(R.string.download_network_wifi_and_mobile)
                                } else {
                                    stringResource(R.string.download_network_wifi_only)
                                },
                            checked = downloadOverMetered,
                            onCheckedChange = onDownloadOverMeteredChange,
                        )
                    }
                    item {
                        ActionRow(
                            title = stringResource(R.string.manage_downloads),
                            icon = Icons.Rounded.Download,
                            onClick = onOpenDownloads,
                        )
                    }
                    item { SectionHeader(stringResource(R.string.settings_library_sync_name)) }
                    item {
                        ValueRow(
                            title = stringResource(R.string.settings_sync_auto_title),
                            value = syncModeLabel(LocalResources.current, syncMode),
                            onClick = { dialog = SettingsDialog.SyncMode },
                        )
                    }
                    item {
                        ValueRow(
                            title = stringResource(R.string.sync_interval),
                            value = syncIntervalLabel(LocalResources.current, syncInterval),
                            onClick = { dialog = SettingsDialog.SyncInterval },
                        )
                    }
                    item {
                        ValueRow(
                            title = stringResource(R.string.sync_concurrency),
                            value = concurrencyLabel(LocalResources.current, syncConcurrency),
                            onClick = { dialog = SettingsDialog.SyncConcurrency },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(fill)) }
        }
    }

    when (dialog) {
        SettingsDialog.WifiQuality -> {
            QualityDialog(
                title = stringResource(R.string.settings_network_options_max_bitrate_wifi_title),
                quality = wifiQuality,
                onSelect = onWifiQualityChange,
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.MobileQuality -> {
            QualityDialog(
                title = stringResource(R.string.settings_network_options_max_bitrate_mobile_title),
                quality = mobileQuality,
                onSelect = onMobileQualityChange,
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.DownloadQuality -> {
            QualityDialog(
                title = stringResource(R.string.download_quality),
                quality = downloadQuality,
                onSelect = onDownloadQualityChange,
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.SyncConcurrency -> {
            ConcurrencyDialog(
                selected = syncConcurrency,
                onSelect = onSyncConcurrencyChange,
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.SyncInterval -> {
            SyncIntervalDialog(
                selected = syncInterval,
                onSelect = onSyncIntervalChange,
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.SyncMode -> {
            SyncModeDialog(
                selected = syncMode,
                onSelect = onSyncModeChange,
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.ReplayGain -> {
            ReplayGainModeDialog(
                selected = replayGainMode,
                onSelect = onReplayGainModeChange,
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.ReplayGainPreamp -> {
            ReplayGainPreampDialog(
                selected = replayGainPreamp,
                onSelect = onReplayGainPreampChange,
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.SortNames -> {
            SortNameModeDialog(
                selected = sortNameMode,
                onSelect = onSortNameModeChange,
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.Theme -> {
            ThemeModeDialog(
                selected = themeMode,
                onSelect = onThemeModeChange,
                onDismiss = { dialog = null },
            )
        }

        null -> {
            Unit
        }
    }
}

/** The main page: the server list, a row per sub-page, and About. */
private fun LazyListScope.rootItems(
    sources: List<Source>,
    activeSourceId: Long?,
    onSelectSource: (Long) -> Unit,
    onEditServer: (Long) -> Unit,
    onAddServer: () -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenHomepage: () -> Unit,
) {
    item { SectionHeader(stringResource(R.string.settings_servers_name)) }
    items(sources.size, key = { sources[it].id }) { index ->
        val source = sources[index]
        ListItem(
            headlineContent = { Text(source.name) },
            supportingContent = {
                Text(source.address, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = {
                RadioButton(
                    selected = source.id == activeSourceId,
                    onClick = { onSelectSource(source.id) },
                )
            },
            trailingContent = {
                IconButton(onClick = { onEditServer(source.id) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_source_description, source.name))
                }
            },
            modifier =
                Modifier.clickable(
                    onClickLabel = stringResource(R.string.use_source_description, source.name),
                ) { onSelectSource(source.id) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onAddServer) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_servers_actions_add))
            }
        }
    }
    item { Spacer(Modifier.height(MaeksterSpacing.s)) }
    items(SettingsPage.entries, key = { it.name }) { page ->
        ListItem(
            headlineContent = { Text(stringResource(page.title)) },
            supportingContent = { Text(stringResource(page.summary)) },
            leadingContent = { Icon(page.icon, contentDescription = null) },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable { onOpenPage(page) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    item { SectionHeader(stringResource(R.string.settings_about_name)) }
    item {
        ListItem(
            headlineContent = { Text(MaeksterBrand.NAME) },
            supportingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME))
                    if (BuildConfig.COMMIT_HASH.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            BuildConfig.COMMIT_HASH,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            },
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    item {
        ActionRow(
            title = stringResource(R.string.settings_about_actions_licenses),
            icon = Icons.Rounded.Description,
            onClick = onOpenLicenses,
        )
    }
    item {
        ActionRow(
            title = stringResource(R.string.settings_about_actions_project_homepage),
            icon = Icons.Rounded.Language,
            onClick = onOpenHomepage,
            supporting = PROJECT_HOMEPAGE,
        )
    }
    item {
        ActionRow(
            title = stringResource(R.string.settings_about_credits),
            icon = Icons.Rounded.Favorite,
            onClick = null,
            supporting = stringResource(R.string.settings_about_credits_description),
        )
    }
}

/** A setting that opens a dialog; [value] shows its current choice. */
@Composable
private fun ValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** An on or off setting; the whole row toggles it, as does the switch. */
@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors =
                    SwitchDefaults.colors(
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** A row with a leading icon that runs [onClick], or just informs when it is null. */
@Composable
private fun ActionRow(
    title: String,
    icon: ImageVector,
    onClick: (() -> Unit)?,
    supporting: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun qualityLabel(quality: StreamQuality): String {
    val resources = LocalResources.current
    val format = quality.format ?: stringResource(R.string.stream_quality_server_default)
    return stringResource(R.string.download_status_summary, format, bitrateLabel(resources, quality.maxBitrate))
}

private fun bitrateLabel(
    resources: Resources,
    kbps: Int,
): String =
    if (kbps == 0) {
        resources.getString(R.string.settings_network_values_unlimited_kbps)
    } else {
        resources.getString(R.string.settings_network_values_kbps, kbps.toString())
    }

private val syncConcurrencyOptions = listOf(1, 2, 4, 8, 16)

private val syncIntervalOptions = listOf(15, 60, 360, 720, DEFAULT_SYNC_INTERVAL_MINUTES)

private fun concurrencyLabel(
    resources: Resources,
    value: Int,
): String = if (value <= 1) resources.getString(R.string.sync_concurrency_sequential) else value.toString()

private fun syncIntervalLabel(
    resources: Resources,
    minutes: Int,
): String =
    if (minutes >= 60 && minutes % 60 == 0) {
        resources.getQuantityString(R.plurals.sync_interval_hours, minutes / 60, minutes / 60)
    } else {
        resources.getQuantityString(R.plurals.sync_interval_minutes, minutes, minutes)
    }

@Composable
private fun SyncIntervalDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    var draft by remember(selected) { mutableIntStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_interval)) },
        text = {
            ChoiceGroup(
                header = stringResource(R.string.sync_interval_prompt),
                options = syncIntervalOptions.map { it to syncIntervalLabel(resources, it) },
                selected = draft,
                onSelect = { draft = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(draft)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_cancel)) }
        },
    )
}

@Composable
private fun ConcurrencyDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    var draft by remember(selected) { mutableIntStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_concurrency)) },
        text = {
            ChoiceGroup(
                header = stringResource(R.string.sync_concurrency_parallel),
                options = syncConcurrencyOptions.map { it to concurrencyLabel(resources, it) },
                selected = draft,
                onSelect = { draft = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(draft)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_cancel)) }
        },
    )
}

private val syncModeOptions = SyncMode.entries

private fun syncModeLabel(
    resources: Resources,
    mode: SyncMode,
): String =
    when (mode) {
        SyncMode.Off -> resources.getString(R.string.sync_mode_off)
        SyncMode.WifiOnly -> resources.getString(R.string.sync_mode_wifi)
        SyncMode.Always -> resources.getString(R.string.sync_mode_always)
    }

@Composable
private fun SyncModeDialog(
    selected: SyncMode,
    onSelect: (SyncMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    var draft by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_sync_auto_title)) },
        text = {
            ChoiceGroup(
                header = stringResource(R.string.sync_mode_prompt),
                options = syncModeOptions.map { it to syncModeLabel(resources, it) },
                selected = draft,
                onSelect = { draft = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(draft)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_cancel)) }
        },
    )
}

private val themeModeOptions = ThemeMode.entries

private fun themeModeLabel(
    resources: Resources,
    mode: ThemeMode,
): String =
    when (mode) {
        ThemeMode.System -> resources.getString(R.string.theme_mode_system)
        ThemeMode.Light -> resources.getString(R.string.theme_mode_light)
        ThemeMode.Dark -> resources.getString(R.string.theme_mode_dark)
    }

@Composable
private fun ThemeModeDialog(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    var draft by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme_title)) },
        text = {
            ChoiceGroup(
                header = stringResource(R.string.settings_theme_prompt),
                options = themeModeOptions.map { it to themeModeLabel(resources, it) },
                selected = draft,
                onSelect = { draft = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(draft)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_cancel)) }
        },
    )
}

private val sortNameModeOptions =
    listOf(SortNameMode.Name, SortNameMode.SortName, SortNameMode.NonLatin)

private fun sortNameModeLabel(
    resources: Resources,
    mode: SortNameMode,
): String =
    when (mode) {
        SortNameMode.SortName -> resources.getString(R.string.sort_name_mode_sort_name)
        SortNameMode.Name -> resources.getString(R.string.sort_name_mode_name)
        SortNameMode.NonLatin -> resources.getString(R.string.sort_name_mode_non_latin)
    }

@Composable
private fun SortNameModeDialog(
    selected: SortNameMode,
    onSelect: (SortNameMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    var draft by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_sort_names_title)) },
        text = {
            ChoiceGroup(
                header = stringResource(R.string.settings_sort_names_prompt),
                options = sortNameModeOptions.map { it to sortNameModeLabel(resources, it) },
                selected = draft,
                onSelect = { draft = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(draft)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_cancel)) }
        },
    )
}

@Composable
private fun ReplayGainModeDialog(
    selected: ReplayGainMode,
    onSelect: (ReplayGainMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    var draft by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_replay_gain_title)) },
        text = {
            ChoiceGroup(
                header = stringResource(R.string.settings_replay_gain_prompt),
                options = ReplayGainMode.entries.map { it to replayGainModeLabel(resources, it) },
                selected = draft,
                onSelect = { draft = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(draft)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplayGainPreampDialog(
    selected: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    var draft by remember(selected) { mutableFloatStateOf(snapReplayGainPreampDb(selected)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_replay_gain_preamp_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.settings_replay_gain_preamp_prompt),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { draft = stepReplayGainPreampDb(draft, -1) },
                        enabled = draft > MIN_REPLAY_GAIN_PREAMP_DB,
                    ) {
                        Icon(
                            Icons.Rounded.Remove,
                            contentDescription = stringResource(R.string.settings_replay_gain_preamp_decrease),
                        )
                    }
                    Slider(
                        value = draft,
                        onValueChange = { draft = snapReplayGainPreampDb(it) },
                        valueRange = MIN_REPLAY_GAIN_PREAMP_DB..MAX_REPLAY_GAIN_PREAMP_DB,
                        steps = REPLAY_GAIN_PREAMP_STEPS,
                        // Compose Material 3 draws one track tick per step, and at 59 steps they
                        // smear together, so the ticks are suppressed and the snapping kept.
                        track = { state -> SliderDefaults.Track(sliderState = state, drawTick = { _, _ -> }) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { draft = stepReplayGainPreampDb(draft, 1) },
                        enabled = draft < MAX_REPLAY_GAIN_PREAMP_DB,
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.settings_replay_gain_preamp_increase),
                        )
                    }
                }
                Text(
                    replayGainPreampLabel(resources, draft),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = { draft = 0f },
                    enabled = draft != 0f,
                ) { Text(stringResource(R.string.settings_replay_gain_preamp_reset)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_cancel)) }
                TextButton(
                    onClick = {
                        onSelect(draft)
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.action_done)) }
            }
        },
    )
}

@Composable
private fun QualityDialog(
    title: String,
    quality: StreamQuality,
    onSelect: (StreamQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    var draft by remember(quality) { mutableStateOf(quality) }
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box {
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    ChoiceGroup(
                        header = stringResource(R.string.maximum_bitrate),
                        options = bitrateOptions.map { it to bitrateLabel(resources, it) },
                        selected = draft.maxBitrate,
                        onSelect = { draft = draft.copy(maxBitrate = it) },
                    )
                    Spacer(Modifier.height(16.dp))
                    ChoiceGroup(
                        header = stringResource(R.string.settings_network_options_stream_format),
                        options =
                            streamFormats.map {
                                it to
                                    (it ?: resources.getString(R.string.settings_network_options_stream_format_server_default))
                            },
                        selected = draft.format,
                        onSelect = { draft = draft.copy(format = it) },
                    )
                }
                if (scrollState.canScrollForward) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerHigh),
                                    ),
                                ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(draft)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_cancel)) }
        },
    )
}

@Composable
private fun <T> ChoiceGroup(
    header: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(header, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        options.forEach { (value, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onSelect(value) },
            ) {
                RadioButton(selected = value == selected, onClick = { onSelect(value) })
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
