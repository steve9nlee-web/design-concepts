package com.perimeter.attendance.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette lifted from the design file, so the app and the mock agree. */
object P {
    val Bg = Color(0xFFFBF7F0)
    val Ink = Color(0xFF17120E)
    val Orange = Color(0xFFE8590C)
    val Green = Color(0xFF0B8A52)
    val Red = Color(0xFFC02B16)
    val Muted = Color(0xFF8A807A)
    val Faint = Color(0xFFA2988F)
    val Line = Color(0xFFEFE8DC)
    val Cream = Color(0xFFFFF5EC)
    val MintText = Color(0xFFB6E6CD)
    val OnDark = Color(0xFFCDC4BB)
}

@Composable
fun PerimeterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = P.Orange,
            background = P.Bg,
            surface = P.Bg,
            onBackground = P.Ink,
            onSurface = P.Ink
        ),
        content = content
    )
}
