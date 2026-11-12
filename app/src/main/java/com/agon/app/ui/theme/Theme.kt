package com.agon.app.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.palette.graphics.Palette

// ── Warna utama (mirip RythimMusic: ungu violet) ──────────────
val Violet      = Color(0xFFA78BFA)
val VioletDark  = Color(0xFF7C3AED)
val PureBlack   = Color(0xFF000000)
val SurfaceDark = Color(0xFF0F0F0F)
val Card        = Color(0xFF1A1A2E)
val CardAlt     = Color(0xFF16213E)

private val DarkColors = darkColorScheme(
    primary           = Violet,
    onPrimary         = Color.Black,
    primaryContainer  = Color(0xFF3D1F8F),
    onPrimaryContainer= Color(0xFFE9DDFF),
    secondary         = Color(0xFF9ECAFF),
    onSecondary       = Color.Black,
    secondaryContainer= Color(0xFF004C73),
    onSecondaryContainer = Color(0xFFCBE6FF),
    tertiary          = Color(0xFFC9B8FF),
    background        = PureBlack,
    onBackground      = Color.White,
    surface           = SurfaceDark,
    onSurface         = Color.White,
    surfaceVariant    = Card,
    onSurfaceVariant  = Color(0xFFCAC4D0),
    error             = Color(0xFFFFB4AB),
    outline           = Color(0xFF49454F)
)

private val LightColors = lightColorScheme(
    primary           = VioletDark,
    onPrimary         = Color.White,
    primaryContainer  = Color(0xFFEDE7FF),
    onPrimaryContainer= Color(0xFF21005D),
    secondary         = Color(0xFF006494),
    onSecondary       = Color.White,
    background        = Color(0xFFFFFBFF),
    onBackground      = Color(0xFF1C1B1F),
    surface           = Color(0xFFFFFBFF),
    onSurface         = Color(0xFF1C1B1F),
    surfaceVariant    = Color(0xFFE7E0EC),
    onSurfaceVariant  = Color(0xFF49454F)
)

/** State holder untuk dynamic theme color dari album art */
object ThemeState {
    var dynamicColor by mutableStateOf(Violet)
    var pureBlack    by mutableStateOf(true)
}

@Composable
fun AgonAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> {
            if (ThemeState.pureBlack)
                DarkColors.copy(background = PureBlack, surface = Color(0xFF0A0A0A))
            else
                DarkColors
        }
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}

/** Extract dominant color dari bitmap album art */
fun Bitmap.extractThemeColor(): Color {
    val palette = Palette.from(this).maximumColorCount(8).generate()
    val argb = palette.getDominantColor(Violet.toArgb())
    return Color(argb)
}

fun Bitmap.extractGradientColors(): List<Color> {
    val palette = Palette.from(this).maximumColorCount(16).generate()
    val swatches = listOfNotNull(
        palette.vibrantSwatch, palette.mutedSwatch,
        palette.darkVibrantSwatch, palette.darkMutedSwatch
    ).sortedByDescending { Color(it.rgb).luminance() }
    return if (swatches.size >= 2)
        listOf(Color(swatches[0].rgb), Color(swatches[1].rgb))
    else
        listOf(Color(0xFF3D1F8F), Color(0xFF0F0F0F))
}
