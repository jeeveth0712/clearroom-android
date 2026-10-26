package com.clearroom.app.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.R
import com.clearroom.app.ui.theme.ClearRoomTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClearRoomAppTest {

    @get:Rule
    val compose = createComposeRule()

    private fun text(@StringRes id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun tabButton(tab: AppTab) = compose.onNode(hasText(text(tab.labelRes)) and hasClickAction())

    @Before
    fun showApp() {
        compose.setContent { ClearRoomTheme { ClearRoomApp() } }
    }

    @Test
    fun opensOnRoomTab_withItsIntroduction() {
        tabButton(AppTab.ROOM).assertIsSelected()
        compose.onNodeWithText(text(R.string.room_intro)).assertIsDisplayed()
    }

    @Test
    fun everyTab_showsItsOwnIntroduction_whenTapped() {
        val introByTab = mapOf(
            AppTab.MEASURE to R.string.measure_intro,
            AppTab.RESULTS to R.string.results_intro,
            AppTab.FIXES to R.string.fixes_intro,
            AppTab.LOG to R.string.log_intro,
            AppTab.ROOM to R.string.room_intro,
        )
        introByTab.forEach { (tab, intro) ->
            tabButton(tab).performClick()
            tabButton(tab).assertIsSelected()
            compose.onNodeWithText(text(intro)).assertIsDisplayed()
        }
    }
}
