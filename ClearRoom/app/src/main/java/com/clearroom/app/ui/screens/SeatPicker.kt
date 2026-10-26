package com.clearroom.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.clearroom.app.R
import com.clearroom.app.data.SeatId

/** Seat cell size: as big as the width allows, never above 44 dp so small grids don't look bloated. */
fun seatCellSize(availableWidth: Dp, cols: Int, gap: Dp): Dp {
    require(cols >= 1) { "need at least one column, got $cols" }
    return min(MAX_CELL, (availableWidth - gap * (cols - 1)) / cols)
}

/**
 * The seat grid as tappable cells, front row at the top (where the speaker is). Measured seats are
 * filled, the selected one is outlined; each cell tells TalkBack its row, seat and status.
 */
@Composable
fun SeatPicker(
    rows: Int,
    cols: Int,
    measured: Set<SeatId>,
    selected: SeatId?,
    onSelect: (SeatId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measuredLabel = stringResource(R.string.seat_measured)
    BoxWithConstraints(modifier) {
        val cell = seatCellSize(maxWidth, cols, GAP)
        Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
            repeat(rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    repeat(cols) { col ->
                        val seat = SeatId(row, col)
                        val label = stringResource(R.string.seat_label, row + 1, col + 1)
                        SeatCell(
                            size = cell,
                            isMeasured = seat in measured,
                            isSelected = seat == selected,
                            description = if (seat in measured) "$label, $measuredLabel" else label,
                            onClick = { onSelect(seat) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeatCell(size: Dp, isMeasured: Boolean, isSelected: Boolean, description: String, onClick: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isMeasured) colours.primaryContainer else colours.surfaceVariant)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) colours.primary else colours.outlineVariant,
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                selected = isSelected
            },
    ) {
        if (isMeasured) Text("✓", color = colours.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
    }
}

private val GAP = 4.dp
private val MAX_CELL = 44.dp
