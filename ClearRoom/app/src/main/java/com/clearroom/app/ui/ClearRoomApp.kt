package com.clearroom.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.clearroom.app.R
import com.clearroom.app.report.ReportSharing
import com.clearroom.app.ui.screens.FixesScreen
import com.clearroom.app.ui.screens.LogScreen
import com.clearroom.app.ui.screens.MeasureScreen
import com.clearroom.app.ui.screens.ResultsScreen
import com.clearroom.app.ui.screens.RoomScreen
import com.clearroom.app.ui.theme.ClearRoomTheme

/**
 * App shell: bottom navigation over the five tabs. Stateless apart from the selected tab, which
 * survives rotation; data comes in as [state], [evaluation], [fixes] and [log], and user actions go out through [onEvent].
 */
@Composable
fun ClearRoomApp(
    state: UiState,
    evaluation: Evaluation?,
    fixes: FixesView?,
    log: DayLogView?,
    liveLevelDb: () -> Double,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.ROOM) }
    // While the day log runs the screen stays on, on every tab: with the screen off Android hands
    // a background app silence instead of sound.
    KeepScreenOn(log?.log?.running == true)
    ShareReport(state.report, onShown = { onEvent(UiEvent.ReportShown) })
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { AppNavigationBar(selected = selectedTab, onTabSelected = { selectedTab = it }) },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            AppTab.ROOM -> RoomScreen(state.room, onEvent, contentModifier)
            AppTab.MEASURE -> MeasureScreen(state.session, state.measure, liveLevelDb, onEvent, contentModifier)
            AppTab.RESULTS -> ResultsScreen(state.session, evaluation, state.report, onEvent, contentModifier)
            AppTab.FIXES -> FixesScreen(state.session, state.fixes, fixes, onEvent, contentModifier)
            AppTab.LOG -> LogScreen(state.session, state.measure.phase.isBusy, log, liveLevelDb, onEvent, contentModifier)
        }
    }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** Opens the share sheet once for a finished report (FR-12), then reports it shown. */
@Composable
private fun ShareReport(report: ReportState, onShown: () -> Unit) {
    val ready = report as? ReportState.Ready ?: return
    val context = LocalContext.current
    val title = stringResource(R.string.report_share_title)
    LaunchedEffect(ready) {
        context.startActivity(ReportSharing.chooser(context, ready.file, title))
        onShown()
    }
}

@Composable
private fun AppNavigationBar(selected: AppTab, onTabSelected: (AppTab) -> Unit) {
    NavigationBar {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onTabSelected(tab) },
                // The visible label already names the tab for TalkBack.
                icon = { Icon(painterResource(tab.iconRes), contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClearRoomAppPreview() {
    ClearRoomTheme { ClearRoomApp(state = UiState(), evaluation = null, fixes = null, log = null, liveLevelDb = { -60.0 }, onEvent = {}) }
}
