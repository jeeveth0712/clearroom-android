package com.clearroom.app.data

import com.clearroom.app.testing.aRoom
import com.clearroom.app.testing.aValidForm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RoomFormTest {

    private fun problemsOf(form: RoomForm) = assertIs<RoomForm.Result.Invalid>(form.toRoom("id")).problems

    @Test
    fun `a complete form becomes a room with the given id`() {
        val form = aValidForm(name = "  Lab 2  ").copy(floor = Material.WOOD, walls = Material.CURTAIN, ceiling = Material.SOFT_BOARD)
        val room = assertIs<RoomForm.Result.Valid>(form.toRoom("abc")).room
        assertEquals(
            Room("abc", "Lab 2", 9.0, 7.0, 3.0, 5, 6, Surfaces(Material.WOOD, Material.CURTAIN, Material.SOFT_BOARD), 0),
            room,
        )
    }

    @Test
    fun `decimal commas are accepted`() {
        val room = assertIs<RoomForm.Result.Valid>(aValidForm().copy(lengthM = "8,5").toRoom("id")).room
        assertEquals(8.5, room.lengthM)
    }

    @Test
    fun `every empty field is reported at once`() {
        assertEquals(
            mapOf(
                RoomField.NAME to FieldProblem.MISSING,
                RoomField.LENGTH to FieldProblem.MISSING,
                RoomField.WIDTH to FieldProblem.MISSING,
                RoomField.HEIGHT to FieldProblem.MISSING,
                RoomField.ROWS to FieldProblem.MISSING,
                RoomField.COLS to FieldProblem.MISSING,
                RoomField.STUDENTS to FieldProblem.MISSING,
            ),
            problemsOf(RoomForm(students = "")),
        )
    }

    @Test
    fun `a name of only spaces counts as missing, and long names are refused`() {
        assertEquals(FieldProblem.MISSING, problemsOf(aValidForm(name = "   "))[RoomField.NAME])
        assertEquals(FieldProblem.OUT_OF_RANGE, problemsOf(aValidForm(name = "x".repeat(RoomLimits.NAME_MAX_CHARS + 1)))[RoomField.NAME])
        assertIs<RoomForm.Result.Valid>(aValidForm(name = "x".repeat(RoomLimits.NAME_MAX_CHARS)).toRoom("id"))
    }

    @Test
    fun `text that isn't a number is reported, including NaN and Infinity`() {
        assertEquals(FieldProblem.NOT_A_NUMBER, problemsOf(aValidForm().copy(lengthM = "nine"))[RoomField.LENGTH])
        assertEquals(FieldProblem.NOT_A_NUMBER, problemsOf(aValidForm().copy(widthM = "NaN"))[RoomField.WIDTH])
        assertEquals(FieldProblem.NOT_A_NUMBER, problemsOf(aValidForm().copy(heightM = "Infinity"))[RoomField.HEIGHT])
    }

    @Test
    fun `seat counts must be whole numbers`() {
        assertEquals(FieldProblem.NOT_A_NUMBER, problemsOf(aValidForm().copy(rows = "5.5"))[RoomField.ROWS])
        assertEquals(FieldProblem.NOT_A_NUMBER, problemsOf(aValidForm().copy(students = "two"))[RoomField.STUDENTS])
    }

    @Test
    fun `range limits are inclusive and anything beyond them is refused`() {
        fun valid(form: RoomForm) = assertIs<RoomForm.Result.Valid>(form.toRoom("id"))
        valid(aValidForm().copy(lengthM = "3.5", widthM = "60", heightM = "20", rows = "1", cols = "30", students = "500"))
        valid(aValidForm().copy(lengthM = "60", widthM = "2", heightM = "2", rows = "30", cols = "1", students = "0"))

        val beyond = aValidForm().copy(lengthM = "3.49", widthM = "60.01", heightM = "1.9", rows = "0", cols = "31", students = "-1")
        assertEquals(
            mapOf(
                RoomField.LENGTH to FieldProblem.OUT_OF_RANGE,
                RoomField.WIDTH to FieldProblem.OUT_OF_RANGE,
                RoomField.HEIGHT to FieldProblem.OUT_OF_RANGE,
                RoomField.ROWS to FieldProblem.OUT_OF_RANGE,
                RoomField.COLS to FieldProblem.OUT_OF_RANGE,
                RoomField.STUDENTS to FieldProblem.OUT_OF_RANGE,
            ),
            problemsOf(beyond),
        )
    }

    @Test
    fun `more rows or seats than the room has space for are refused`() {
        val cramped = aValidForm().copy(lengthM = "3.5", widthM = "2.5", rows = "5", cols = "6")
        assertEquals(mapOf(RoomField.ROWS to FieldProblem.DOESNT_FIT, RoomField.COLS to FieldProblem.DOESNT_FIT), problemsOf(cramped))
        assertIs<RoomForm.Result.Valid>(cramped.copy(rows = "1", cols = "2").toRoom("id"))
    }

    @Test
    fun `the form says how many rows and seats per row fit`() {
        val form = aValidForm().copy(lengthM = "3,5", widthM = "2.5")
        assertEquals(1, form.rowsThatFit())
        assertEquals(2, form.seatsPerRowThatFit())
    }

    @Test
    fun `nothing is said to fit while the size isn't a valid number`() {
        assertEquals(null, aValidForm().copy(lengthM = "").rowsThatFit())
        assertEquals(null, aValidForm().copy(widthM = "wide").seatsPerRowThatFit())
        assertEquals(null, aValidForm().copy(lengthM = "600").rowsThatFit())
    }

    @Test
    fun `a size problem is reported on the size, not as seats that don't fit`() {
        val problems = problemsOf(aValidForm().copy(lengthM = "1", rows = "30"))
        assertEquals(mapOf(RoomField.LENGTH to FieldProblem.OUT_OF_RANGE), problems)
    }

    @Test
    fun `a room shown in the form and saved again comes back unchanged`() {
        listOf(aRoom(), aRoom(lengthM = 8.75, widthM = 6.2, heightM = 3.05, students = 32), SampleData.room).forEach { room ->
            assertEquals(RoomForm.Result.Valid(room), RoomForm.from(room).toRoom(room.id), "room=$room")
        }
    }

    @Test
    fun `whole metres show without a trailing zero`() {
        assertEquals("9", RoomForm.from(aRoom(lengthM = 9.0)).lengthM)
        assertEquals("3.2", RoomForm.from(aRoom(heightM = 3.2)).heightM)
    }
}
