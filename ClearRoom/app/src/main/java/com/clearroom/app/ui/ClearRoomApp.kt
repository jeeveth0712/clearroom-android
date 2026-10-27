package com.clearroom.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

/**
 * The five tabs as a floating pill above the system navigation: black (raised grey at night) with
 * the open tab in a lime pill. Each tab is one selectable item, so TalkBack reads "Results, tab,
 * selected" from its visible label.
 */
@Composable
private fun AppNavigationBar(selected: AppTab, onTabSelected: (AppTab) -> Unit) {
    val brand = ClearRoomTheme.brand
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(CircleShape)
            .background(brand.navBar)
            .selectableGroup()
            .padding(6.dp),
    ) {
        AppTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val content = if (isSelected) brand.onNavSelected else brand.onNavBar
            Column(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isSelected) brand.navSelected else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onTabSelected(tab) })
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // The visible label already names the tab for TalkBack.
                Icon(painterResource(tab.iconRes), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                Text(stringResource(tab.labelRes), style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClearRoomAppPreview() {
    ClearRoomTheme { ClearRoomApp(state = UiState(), evaluation = null, fixes = null, log = null, liveLevelDb = { -60.0 }, onEvent = {}) }
}
