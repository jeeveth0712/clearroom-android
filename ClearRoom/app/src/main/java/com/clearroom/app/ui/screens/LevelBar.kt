package com.clearroom.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** Bottom of the level bar: quieter than any real room, well above the -120 dB silence value. */
const val LEVEL_BAR_FLOOR_DB = -90.0

/** Maps a dBFS reading to the bar's fill (0 = floor or below, 1 = full scale). Not-a-number reads as empty. */
fun levelBarFraction(levelDb: Double): Float {
    if (levelDb.isNaN()) return 0f
    return ((levelDb - LEVEL_BAR_FLOOR_DB) / -LEVEL_BAR_FLOOR_DB).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Live level bar. [levelDb] is read inside the indicator's draw step, so 20 updates a second
 * redraw just this bar instead of recomposing the screen.
 */
@Composable
fun LevelBar(levelDb: () -> Double, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { levelBarFraction(levelDb()) },
        modifier = modifier.fillMaxWidth().height(12.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
