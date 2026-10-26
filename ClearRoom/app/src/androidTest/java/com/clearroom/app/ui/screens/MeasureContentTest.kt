package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.R
import com.clearroom.app.permission.MicAccess
import com.clearroom.app.ui.theme.ClearRoomTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the stateless Measure content for each microphone state, without touching the real permission. */
@RunWith(AndroidJUnit4::class)
class MeasureContentTest {

    @get:Rule
    val compose = createComposeRule()

    private var allowClicks = 0
    private var settingsClicks = 0

    private fun text(@StringRes id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(access: MicAccess) = compose.setContent {
        ClearRoomTheme {
            MeasureContent(micAccess = access, onAllowMic = { allowClicks++ }, onOpenSettings = { settingsClicks++ })
        }
    }

    @Test
    fun notAsked_explainsWhy_andAllowButtonRequestsPermission() {
        show(MicAccess.NOT_ASKED)
        compose.onNodeWithText(text(R.string.mic_why)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_allow)).performClick()
        assertEquals(1, allowClicks)
        compose.onNodeWithText(text(R.string.mic_open_settings)).assertDoesNotExist()
    }

    @Test
    fun deniedOnce_offersToAskAgain() {
        show(MicAccess.DENIED_CAN_ASK_AGAIN)
        compose.onNodeWithText(text(R.string.mic_denied_can_ask_again)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_allow)).performClick()
        assertEquals(1, allowClicks)
    }

    @Test
    fun deniedPermanently_offersSettingsInsteadOfAsking() {
        show(MicAccess.DENIED_PERMANENTLY)
        compose.onNodeWithText(text(R.string.mic_denied_permanently)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_open_settings)).performClick()
        assertEquals(1, settingsClicks)
        compose.onNodeWithText(text(R.string.mic_allow)).assertDoesNotExist()
    }

    @Test
    fun granted_showsReady_withNoPermissionButtons() {
        show(MicAccess.GRANTED)
        compose.onNodeWithText(text(R.string.mic_ready)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_allow)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.mic_open_settings)).assertDoesNotExist()
    }
}
