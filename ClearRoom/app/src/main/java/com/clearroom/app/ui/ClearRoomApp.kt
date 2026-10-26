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
import com.clearroom.app.ui.screens.TabIntro
import com.clearroom.app.ui.theme.ClearRoomTheme

/** App shell: bottom navigation over the five tabs. The selected tab survives rotation. */
@Composable
fun ClearRoomApp(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.ROOM) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { AppNavigationBar(selected = selectedTab, onTabSelected = { selectedTab = it }) },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            AppTab.ROOM -> TabIntro(AppTab.ROOM.labelRes, R.string.room_intro, contentModifier)
            AppTab.MEASURE -> MeasureScreen(contentModifier)
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
    ClearRoomTheme { ClearRoomApp() }
}
