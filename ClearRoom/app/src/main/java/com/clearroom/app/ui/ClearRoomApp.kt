package com.clearroom.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.clearroom.app.R
import com.clearroom.app.ui.screens.MeasureScreen
import com.clearroom.app.ui.screens.RoomScreen
import com.clearroom.app.ui.screens.TabIntro
import com.clearroom.app.ui.theme.ClearRoomTheme

/**
 * App shell: bottom navigation over the five tabs. Stateless apart from the selected tab, which
 * survives rotation; data comes in as [state] and user actions go out through [onEvent].
 */
@Composable
fun ClearRoomApp(
    state: UiState,
    liveLevelDb: () -> Double,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.ROOM) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { AppNavigationBar(selected = selectedTab, onTabSelected = { selectedTab = it }) },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            AppTab.ROOM -> RoomScreen(state.room, onEvent, contentModifier)
            AppTab.MEASURE -> MeasureScreen(state.session, state.measure, liveLevelDb, onEvent, contentModifier)
            AppTab.RESULTS -> TabIntro(AppTab.RESULTS.labelRes, R.string.results_intro, contentModifier)
            AppTab.FIXES -> TabIntro(AppTab.FIXES.labelRes, R.string.fixes_intro, contentModifier)
            AppTab.LOG -> TabIntro(AppTab.LOG.labelRes, R.string.log_intro, contentModifier)
        }
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
    ClearRoomTheme { ClearRoomApp(state = UiState(), liveLevelDb = { -60.0 }, onEvent = {}) }
}
