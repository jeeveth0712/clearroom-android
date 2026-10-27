package com.clearroom.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Soft, generous corners: cards 24 dp, fields and chips 12–14 dp; buttons stay full pills. */
val ClearRoomShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val LocalBrandColors = staticCompositionLocalOf { LightBrand }
private val LocalClassroomColors = staticCompositionLocalOf { LightClassroom }

/**
 * ClearRoom's own colours, light or dark with the phone. The brand colours are fixed, never the
 * wallpaper's (Android's dynamic colour), so the clarity band colours always sit on the same palette.
 */
@Composable
fun ClearRoomTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBrandColors provides if (darkTheme) DarkBrand else LightBrand,
        LocalClassroomColors provides if (darkTheme) DarkClassroom else LightClassroom,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = ClearRoomShapes,
            content = content,
        )
    }
}

object ClearRoomTheme {
    /** The hero card and navigation bar colours for the current light or dark theme. */
    val brand: BrandColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandColors.current

    /** The 3D classroom's greys for the current light or dark theme. */
    val classroom: ClassroomColors
        @Composable
        @ReadOnlyComposable
        get() = LocalClassroomColors.current
}
