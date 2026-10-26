package com.clearroom.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The 3D room's own palette: a light "architect's model" that looks the same in light and dark
 * themes, so the clarity band colours always sit on the same background. The speaker is a neutral
 * grey because any hue could be mistaken for a band colour.
 */
object SceneColors {
    val Floor = Color(0xFFE4DFD7)
    val SeatTile = Color(0xFFC9C1B4)
    val Walls = Color(0xFFF5F3EF)
    val Speaker = Color(0xFF5F6368)
    val LabelText = Color(0xFF26282B)
    val LabelBackground = Color(0xF2FFFFFF)
}
