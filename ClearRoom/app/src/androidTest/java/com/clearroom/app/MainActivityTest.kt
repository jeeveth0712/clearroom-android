package com.clearroom.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end on the real activity, ViewModel, store and permission plumbing (rememberMicPermission).
 * Runs in the separate "uiTest" app, which starts with no saved rooms. The denied-permission paths
 * need the system dialog, so they're covered by MeasureContentTest plus a manual check.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule(order = 0)
    val microphone: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun openTab(label: Int) = compose.onNode(hasText(text(label)) and hasClickAction()).performClick()

    @Test
    fun launches_onRoomTab() {
        compose.onNodeWithText(text(R.string.room_intro)).assertIsDisplayed()
    }

    @Test
    fun measureTab_withMicrophoneGranted_butNoRoom_pointsToTheRoomTab() {
        openTab(R.string.tab_measure)
        compose.onNodeWithText(text(R.string.measure_no_room)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_allow)).assertDoesNotExist()
    }

    @Test
    fun loadingTheSample_thenMeasureTab_explainsTheSampleIsForExploring() {
        compose.onNodeWithText(text(R.string.room_load_sample)).performClick()
        openTab(R.string.tab_measure)
        compose.onNodeWithText(text(R.string.measure_sample_room)).performScrollTo().assertIsDisplayed()
    }
}
