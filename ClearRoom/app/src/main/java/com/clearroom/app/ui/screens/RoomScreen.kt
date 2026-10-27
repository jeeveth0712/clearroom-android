package com.clearroom.app.ui.screens

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.data.FieldProblem
import com.clearroom.app.data.Material
import com.clearroom.app.data.RoomField
import com.clearroom.app.data.RoomForm
import com.clearroom.app.data.RoomLimits
import com.clearroom.app.data.RoomSummary
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.SavedRoom
import com.clearroom.app.ui.AppTab
import com.clearroom.app.ui.RoomNotice
import com.clearroom.app.ui.RoomUiState
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.formatMetres
import com.clearroom.app.ui.theme.ClearRoomTheme
import com.clearroom.app.viz.RoomPlan
import com.clearroom.app.viz.ClassroomPicture3D
import kotlinx.coroutines.launch

/** Room tab (FR-1): describe the room, see its floor plan, and switch between saved rooms. */
@Composable
fun RoomScreen(state: RoomUiState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(AppTab.ROOM, action = {
                ActionPill(R.drawable.ic_edit, R.string.room_edit_action, onClick = { scope.launch { listState.animateScrollToItem(3) } })
            })
        }
        item { RoomOverview(state.form) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onEvent(UiEvent.NewRoom) }) { Text(stringResource(R.string.room_new)) }
                OutlinedButton(onClick = { onEvent(UiEvent.LoadSampleRoom) }) { Text(stringResource(R.string.room_load_sample)) }
            }
        }
        item { RoomFormCard(state, onEvent) }
        item { PlanCard(state.form) }
        item { SavedRoomsCard(state.saved, openId = state.editingId, onEvent = onEvent, onDelete = { pendingDelete = it }) }
    }

    pendingDelete?.let { roomId ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.room_delete_title)) },
            text = { Text(stringResource(R.string.room_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onEvent(UiEvent.DeleteRoom(roomId))
                    pendingDelete = null
                }) { Text(stringResource(R.string.room_delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.room_delete_cancel)) } },
        )
    }
}

/** Live room preview: only valid dimensions reach the 3D scene while the form is being edited. */
@Composable
private fun RoomOverview(form: RoomForm) {
    val room = (form.toRoom("preview") as? RoomForm.Result.Valid)?.room
    if (room == null) {
        InfoCard { Body(R.string.room_intro) }
        return
    }
    InfoCard {
        Text(room.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ClassroomPicture3D(room, stringResource(R.string.room_hero_picture, room.name, room.rows, room.cols))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.room_hero_size), stringResource(R.string.room_hero_size_value,
                room.lengthM.formatMetres(), room.widthM.formatMetres(), room.heightM.formatMetres()), Modifier.weight(2f))
            StatTile(stringResource(R.string.room_hero_rows), room.rows.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.room_hero_seats), (room.rows * room.cols).toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun RoomFormCard(state: RoomUiState, onEvent: (UiEvent) -> Unit) {
    val form = state.form
    val change = { updated: RoomForm -> onEvent(UiEvent.RoomFormChanged(updated)) }

    @Composable
    fun errorFor(field: RoomField): String? = state.problems[field]?.let { problemText(field, it, form) }
    SectionCard(R.string.room_details) {
        FormField(R.string.room_field_name, form.name, errorFor(RoomField.NAME), KeyboardType.Text) {
            change(form.copy(name = it))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField(R.string.room_field_length, form.lengthM, errorFor(RoomField.LENGTH), KeyboardType.Decimal, Modifier.weight(1f), unit = R.string.room_unit_metres) {
                change(form.copy(lengthM = it))
            }
            FormField(R.string.room_field_width, form.widthM, errorFor(RoomField.WIDTH), KeyboardType.Decimal, Modifier.weight(1f), unit = R.string.room_unit_metres) {
                change(form.copy(widthM = it))
            }
            FormField(R.string.room_field_height, form.heightM, errorFor(RoomField.HEIGHT), KeyboardType.Decimal, Modifier.weight(1f), unit = R.string.room_unit_metres) {
                change(form.copy(heightM = it))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField(R.string.room_field_rows, form.rows, errorFor(RoomField.ROWS), KeyboardType.Number, Modifier.weight(1f)) {
                change(form.copy(rows = it))
            }
            FormField(R.string.room_field_cols, form.cols, errorFor(RoomField.COLS), KeyboardType.Number, Modifier.weight(1f)) {
                change(form.copy(cols = it))
            }
        }
        FormField(R.string.room_field_students, form.students, errorFor(RoomField.STUDENTS), KeyboardType.Number) {
            change(form.copy(students = it))
        }
        MaterialPicker(R.string.room_surface_floor, form.floor) { change(form.copy(floor = it)) }
        MaterialPicker(R.string.room_surface_walls, form.walls) { change(form.copy(walls = it)) }
        MaterialPicker(R.string.room_surface_ceiling, form.ceiling) { change(form.copy(ceiling = it)) }
        Button(onClick = { onEvent(UiEvent.SaveRoom) }) { Text(stringResource(R.string.room_save)) }
        state.notice?.let { Text(stringResource(it.messageRes()), style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun FormField(
    @StringRes label: Int,
    value: String,
    error: String?,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
    // The unit after the number ("9 m") keeps the label short enough for three fields in a row.
    @StringRes unit: Int? = null,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        suffix = unit?.let { { Text(stringResource(it)) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
    )
}

@Composable
private fun problemText(field: RoomField, problem: FieldProblem, form: RoomForm): String = when (problem) {
    FieldProblem.MISSING -> stringResource(R.string.room_problem_missing)
    FieldProblem.NOT_A_NUMBER -> stringResource(R.string.room_problem_not_a_number)
    FieldProblem.OUT_OF_RANGE -> when (field) {
        RoomField.NAME -> pluralStringResource(R.plurals.room_problem_name_too_long, RoomLimits.NAME_MAX_CHARS, RoomLimits.NAME_MAX_CHARS)
        RoomField.LENGTH -> rangeText(RoomLimits.LENGTH_M.start.formatMetres(), RoomLimits.LENGTH_M.endInclusive.formatMetres())
        RoomField.WIDTH -> rangeText(RoomLimits.WIDTH_M.start.formatMetres(), RoomLimits.WIDTH_M.endInclusive.formatMetres())
        RoomField.HEIGHT -> rangeText(RoomLimits.HEIGHT_M.start.formatMetres(), RoomLimits.HEIGHT_M.endInclusive.formatMetres())
        RoomField.ROWS -> rangeText(RoomLimits.ROWS.first.toString(), RoomLimits.ROWS.last.toString())
        RoomField.COLS -> rangeText(RoomLimits.COLS.first.toString(), RoomLimits.COLS.last.toString())
        RoomField.STUDENTS -> rangeText(RoomLimits.STUDENTS.first.toString(), RoomLimits.STUDENTS.last.toString())
    }
    FieldProblem.DOESNT_FIT -> {
        val fits = when (field) {
            RoomField.ROWS -> form.rowsThatFit()?.let { pluralStringResource(R.plurals.room_problem_rows_dont_fit, it, it) }
            RoomField.COLS -> form.seatsPerRowThatFit()?.let { pluralStringResource(R.plurals.room_problem_seats_dont_fit, it, it) }
            else -> null
        }
        fits ?: stringResource(R.string.room_problem_doesnt_fit)
    }
}

@Composable
private fun rangeText(from: String, to: String) = stringResource(R.string.room_problem_range, from, to)

@Composable
private fun MaterialPicker(@StringRes label: Int, selected: Material, onSelect: (Material) -> Unit) {
    Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Material.entries.forEach { material ->
            FilterChip(
                selected = material == selected,
                onClick = { onSelect(material) },
                label = { Text(stringResource(material.nameRes())) },
            )
        }
    }
}

@Composable
private fun PlanCard(form: RoomForm) {
    SectionCard(R.string.room_plan_title) {
        when (val result = form.toRoom(id = "preview")) {
            is RoomForm.Result.Valid -> {
                RoomPlan(result.room)
                Text(stringResource(R.string.room_plan_hint), style = MaterialTheme.typography.bodySmall)
            }
            is RoomForm.Result.Invalid -> Text(stringResource(R.string.room_plan_missing), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SavedRoomsCard(saved: List<SavedRoom>, openId: String?, onEvent: (UiEvent) -> Unit, onDelete: (String) -> Unit) {
    SectionCard(R.string.room_saved_title) {
        if (saved.isEmpty()) Text(stringResource(R.string.room_saved_empty), style = MaterialTheme.typography.bodyMedium)
        saved.forEach { room ->
            when (room) {
                is SavedRoom.Readable -> SavedRoomRow(room.summary, isOpen = room.id == openId, onOpen = { onEvent(UiEvent.OpenRoom(room.id)) }, onDelete = { onDelete(room.id) })
                is SavedRoom.Damaged -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.room_saved_damaged), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onDelete(room.id) }) { Text(stringResource(R.string.room_delete)) }
                }
            }
        }
    }
}

@Composable
private fun SavedRoomRow(summary: RoomSummary, isOpen: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 8.dp)) {
            Text(summary.name, style = MaterialTheme.typography.titleSmall, fontWeight = if (isOpen) FontWeight.Bold else null)
            val size = stringResource(R.string.room_saved_size, summary.widthM.formatMetres(), summary.lengthM.formatMetres())
            val seats = pluralStringResource(R.plurals.room_saved_seats, summary.seatCount, summary.seatCount)
            val measured = pluralStringResource(R.plurals.room_saved_measured, summary.measuredSeats, summary.measuredSeats)
            Text("$size · $seats · $measured", style = MaterialTheme.typography.bodySmall)
            if (isOpen) Text(stringResource(R.string.room_saved_open_now), style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.room_delete)) }
    }
}

@Composable
private fun SectionCard(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) = ChartCard(title, content)

@StringRes
private fun Material.nameRes(): Int = when (this) {
    Material.CONCRETE -> R.string.material_concrete
    Material.TILE -> R.string.material_tile
    Material.GLASS -> R.string.material_glass
    Material.WOOD -> R.string.material_wood
    Material.CURTAIN -> R.string.material_curtain
    Material.SOFT_BOARD -> R.string.material_soft_board
}

@StringRes
private fun RoomNotice.messageRes(): Int = when (this) {
    RoomNotice.SAVED -> R.string.room_notice_saved
    RoomNotice.SAMPLE_LOADED -> R.string.room_notice_sample_loaded
    RoomNotice.SAVE_FAILED -> R.string.room_notice_save_failed
    RoomNotice.OPEN_FAILED -> R.string.room_notice_open_failed
    RoomNotice.DELETED -> R.string.room_notice_deleted
}

@Preview(showBackground = true, heightDp = 1400)
@Preview(showBackground = true, heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RoomScreenPreview() {
    val summary = RoomSummary(SampleData.ROOM_ID, "Sample classroom", 9.0, 7.0, 30, 3, savedAtMillis = 0)
    ClearRoomTheme {
        RoomScreen(
            state = RoomUiState(
                saved = listOf(SavedRoom.Readable(summary), SavedRoom.Damaged("abc")),
                form = RoomForm.from(SampleData.room),
                editingId = SampleData.ROOM_ID,
                notice = RoomNotice.SAMPLE_LOADED,
            ),
            onEvent = {},
        )
    }
}
