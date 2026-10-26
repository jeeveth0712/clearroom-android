package com.clearroom.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.clearroom.app.acoustics.StiBand

/**
 * Fixed colour for each clarity band, used by 2D charts, 3D scenes and the PDF alike: the doc
 * requires that "a red seat is red everywhere", so these never follow the theme or dynamic colour.
 * The red → blue ramp stays distinguishable with red-green colour blindness. Charts still pair it
 * with labels or outlines, never colour alone.
 */
fun StiBand.color(): Color = when (this) {
    StiBand.POOR -> Color(0xFFD7301F)
    StiBand.FAIR -> Color(0xFFFC8D59)
    StiBand.GOOD -> Color(0xFF91BFDB)
    StiBand.EXCELLENT -> Color(0xFF4575B4)
}
