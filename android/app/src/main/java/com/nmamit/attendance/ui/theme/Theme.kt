package com.nmamit.attendance.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LiquidGlassColorScheme = darkColorScheme(
    primary = Foreground,
    onPrimary = BgCanvas,
    primaryContainer = TabActiveBg,
    onPrimaryContainer = Foreground,
    secondary = Emerald,
    onSecondary = BgCanvas,
    background = BgCanvas,
    surface = GlassCardSolid,
    surfaceVariant = GlassSubCardBg,
    onBackground = Foreground,
    onSurface = Foreground,
    onSurfaceVariant = ForegroundMuted,
    outline = GlassBorder
)

@Composable
fun NMAMITAttendanceTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = LiquidGlassColorScheme,
        typography = Typography,
        content = content
    )
}
