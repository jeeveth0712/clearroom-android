package com.clearroom.app.ui.theme

import androidx.compose.runtime.Immutable
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

/**
 * The classroom model of V8 and the Room tab's picture: grey walls and floor, desks and chairs,
 * a whiteboard, windows and a door. It follows the light or dark theme, but every colour is grey
 * or barely tinted, so a desk top coloured by its clarity band is the only strong colour in it.
 */
@Immutable
data class ClassroomColors(
    val floor: Color,
    val walls: Color,
    /** A desk top before anything is measured (the Room tab). */
    val desk: Color,
    /** Desk legs and chairs. */
    val furniture: Color,
    val teacherDesk: Color,
    val board: Color,
    val boardFrame: Color,
    val window: Color,
    val door: Color,
    val speaker: Color,
)

internal val LightClassroom = ClassroomColors(
    floor = Color(0xFFD9D9D5),
    walls = Color(0xFFF1F0EC),
    desk = Color(0xFFD3CCC1),
    furniture = Color(0xFF6E7278),
    teacherDesk = Color(0xFFB3ABA0),
    board = Color(0xFFFBFBF9),
    boardFrame = Color(0xFF9AA0A6),
    window = Color(0xFFDCE4E8),
    door = Color(0xFFA59D93),
    speaker = Color(0xFF5F6368),
)

internal val DarkClassroom = ClassroomColors(
    floor = Color(0xFF4A4E54),
    walls = Color(0xFF6C7077),
    desk = Color(0xFF8D877E),
    furniture = Color(0xFF2B2E33),
    teacherDesk = Color(0xFF7A746C),
    board = Color(0xFFE9EAE7),
    boardFrame = Color(0xFF3A3D42),
    window = Color(0xFF9AA5AB),
    door = Color(0xFF5C5750),
    speaker = Color(0xFFC9CDD4),
)
