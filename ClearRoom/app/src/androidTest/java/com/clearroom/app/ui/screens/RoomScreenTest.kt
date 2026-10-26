package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.R
import com.clearroom.app.data.FieldProblem
import com.clearroom.app.data.Material
import com.clearroom.app.data.RoomField
import com.clearroom.app.data.RoomForm
import com.clearroom.app.data.RoomSummary
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SavedRoom
import com.clearroom.app.ui.RoomNotice
import com.clearroom.app.ui.RoomUiState
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.theme.ClearRoomTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless Room screen: what it shows for a state, and which events the user's taps send. */
@RunWith(AndroidJUnit4::class)
class RoomScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<UiEvent>()
    private val sampleForm = RoomForm.from(SampleData.room)

    private fun text(@StringRes id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun show(state: RoomUiState) = compose.setContent {
        ClearRoomTheme { RoomScreen(state = state, onEvent = { events += it }) }
    }

    private fun scrollTo(label: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
    }

    @Test
    fun typingARoomName_sendsTheUpdatedForm() {
        show(RoomUiState(form = sampleForm))
        compose.onNodeWithText(text(R.string.room_field_name)).performTextReplacement("Lab 2")
        assertEquals(UiEvent.RoomFormChanged(sampleForm.copy(name = "Lab 2")), events.last())
    }

    @Test
    fun pickingAMaterial_sendsTheFormWithThatMaterial() {
        show(RoomUiState(form = sampleForm))
        scrollTo(text(R.string.room_save))
        // Three pickers (floor, walls, ceiling) each offer Curtain; the second one is the walls.
        compose.onAllNodesWithText(text(R.string.material_curtain))[1].performClick()
        assertEquals(UiEvent.RoomFormChanged(sampleForm.copy(walls = Material.CURTAIN)), events.last())
    }

    @Test
    fun saveButton_sendsSaveRoom() {
        show(RoomUiState(form = sampleForm))
        scrollTo(text(R.string.room_save))
        compose.onNodeWithText(text(R.string.room_save)).performClick()
        assertEquals(UiEvent.SaveRoom, events.last())
    }

    @Test
    fun fieldProblems_areExplainedNextToTheirFields() {
        show(RoomUiState(form = RoomForm(), problems = mapOf(RoomField.NAME to FieldProblem.MISSING, RoomField.ROWS to FieldProblem.OUT_OF_RANGE)))
        scrollTo(text(R.string.room_problem_missing))
        compose.onNodeWithText(text(R.string.room_problem_missing)).assertIsDisplayed()
        scrollTo(text(R.string.room_problem_range, "1", "30"))
        compose.onNodeWithText(text(R.string.room_problem_range, "1", "30")).assertIsDisplayed()
    }

    @Test
    fun seatsThatDontFit_sayHowManyDo() {
        val cramped = sampleForm.copy(lengthM = "3.5", widthM = "2.5")
        show(RoomUiState(form = cramped, problems = mapOf(RoomField.ROWS to FieldProblem.DOESNT_FIT, RoomField.COLS to FieldProblem.DOESNT_FIT)))
        val rows = InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(R.plurals.room_problem_rows_dont_fit, 1, 1)
        val seats = InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(R.plurals.room_problem_seats_dont_fit, 2, 2)
        scrollTo(rows)
        compose.onNodeWithText(rows).assertIsDisplayed()
        compose.onNodeWithText(seats).assertIsDisplayed()
    }

    @Test
    fun newRoomAndSampleButtons_sendTheirEvents() {
        show(RoomUiState())
        compose.onNodeWithText(text(R.string.room_new)).performClick()
        compose.onNodeWithText(text(R.string.room_load_sample)).performClick()
        assertEquals(listOf(UiEvent.NewRoom, UiEvent.LoadSampleRoom), events)
    }

    @Test
    fun aValidForm_showsTheFloorPlan_andAnIncompleteOneExplainsWhy() {
        show(RoomUiState(form = sampleForm))
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val description = text(
            R.string.room_plan_description,
            "7",
            "9",
            resources.getQuantityString(R.plurals.room_plan_rows, 5, 5),
            resources.getQuantityString(R.plurals.room_plan_seats_per_row, 6, 6),
        )
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(description))
        compose.onNode(hasContentDescription(description)).assertIsDisplayed()
    }

    @Test
    fun anIncompleteForm_explainsWhyThereIsNoPlan() {
        show(RoomUiState(form = RoomForm()))
        scrollTo(text(R.string.room_plan_missing))
        compose.onNodeWithText(text(R.string.room_plan_missing)).assertIsDisplayed()
    }

    @Test
    fun noticeAfterSaving_isShown() {
        show(RoomUiState(form = sampleForm, notice = RoomNotice.SAVED))
        scrollTo(text(R.string.room_notice_saved))
        compose.onNodeWithText(text(R.string.room_notice_saved)).assertIsDisplayed()
    }

    @Test
    fun tappingASavedRoom_opensIt() {
        show(RoomUiState(saved = listOf(SavedRoom.Readable(summary("r1", "Physics lab")))))
        scrollTo("Physics lab")
        compose.onNodeWithText("Physics lab").performClick()
        assertEquals(UiEvent.OpenRoom("r1"), events.last())
    }

    @Test
    fun deletingASavedRoom_asksFirst_andOnlyDeletesWhenConfirmed() {
        show(RoomUiState(saved = listOf(SavedRoom.Readable(summary("r1", "Physics lab")))))
        scrollTo("Physics lab")
        compose.onNodeWithText(text(R.string.room_delete)).performClick()
        compose.onNodeWithText(text(R.string.room_delete_cancel)).performClick()
        assertEquals(emptyList<UiEvent>(), events)

        compose.onNodeWithText(text(R.string.room_delete)).performClick()
        compose.onAllNodesWithText(text(R.string.room_delete))[1].performClick() // the dialog's button
        assertEquals(listOf<UiEvent>(UiEvent.DeleteRoom("r1")), events)
    }

    @Test
    fun aDamagedFile_isShownAsSuch() {
        show(RoomUiState(saved = listOf(SavedRoom.Damaged("bad"))))
        scrollTo(text(R.string.room_saved_damaged))
        compose.onNodeWithText(text(R.string.room_saved_damaged)).assertIsDisplayed()
    }

    private fun summary(id: String, name: String) = RoomSummary(id, name, 9.0, 7.0, 30, 3, savedAtMillis = 0)
}
