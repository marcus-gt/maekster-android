package com.subtracks.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun QueueBuilderRoute(viewModel: QueueBuilderViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val building by viewModel.building.collectAsStateWithLifecycle()
    val focus by viewModel.focusedMain.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.refresh() }
    val actions =
        remember(viewModel) {
            QueueBuilderActions(
                open = {
                    viewModel.refresh()
                    editing = true
                },
                toggleMain = viewModel::toggleMain,
                tapMainInCard = viewModel::tapMainInCard,
                togglePill = viewModel::togglePill,
                toggleDecade = viewModel::toggleDecade,
                clearGenres = viewModel::clearGenres,
                clearDecades = viewModel::clearDecades,
                reset = viewModel::reset,
                setLength = viewModel::setLength,
                build = { viewModel.build(onStarted = { editing = false }) },
            )
        }
    if (editing) QueueBuilderSheet(state, building, actions, onDismiss = { editing = false })
    QueueBuilderSection(state, focus, building, actions)
}
