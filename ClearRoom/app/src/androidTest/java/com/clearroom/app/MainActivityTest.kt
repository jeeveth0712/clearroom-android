package com.clearroom.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end on the real activity: the real permission plumbing (rememberMicPermission) must
 * notice a granted microphone and show "ready". The denied paths need the system dialog, so they
 * are covered by MeasureContentTest plus a manual check on the phone.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule(order = 0)
    val microphone: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun launches_onRoomTab() {
        compose.onNodeWithText(text(R.string.room_intro)).assertIsDisplayed()
    }

    @Test
    fun measureTab_withMicrophoneGranted_showsReady() {
        compose.onNode(hasText(text(R.string.tab_measure)) and hasClickAction()).performClick()
        compose.onNodeWithText(text(R.string.mic_ready)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_allow)).assertDoesNotExist()
    }
}
