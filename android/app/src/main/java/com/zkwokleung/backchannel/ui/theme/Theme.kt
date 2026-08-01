package com.zkwokleung.backchannel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — same family as the launcher icon
val IndigoDeep = Color(0xFF312E81)
val VioletMid = Color(0xFF4C2A9E)
val VioletBright = Color(0xFF7C3AED)
val VioletSoft = Color(0xFFA78BFA)
val GlyphTint = Color(0xFFDDD6FE)

private val DarkColors = darkColorScheme(
    primary = VioletSoft,
    onPrimary = Color(0xFF2E1065),
    primaryContainer = VioletMid,
    onPrimaryContainer = GlyphTint,
    secondary = GlyphTint,
    onSecondary = Color(0xFF2E1065),
    secondaryContainer = Color(0xFF3B3555),
    onSecondaryContainer = GlyphTint,
    background = Color(0xFF17151F),
    onBackground = Color(0xFFECE9F6),
    surface = Color(0xFF201D2B),
    onSurface = Color(0xFFECE9F6),
    surfaceVariant = Color(0xFF2A2638),
    onSurfaceVariant = Color(0xFF9D97B5),
    outline = Color(0xFF544E6B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = IndigoDeep,
    secondary = VioletMid,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E0F5),
    onSecondaryContainer = Color(0xFF232136),
    background = Color(0xFFFAF9FD),
    onBackground = Color(0xFF232136),
    surface = Color.White,
    onSurface = Color(0xFF232136),
    surfaceVariant = Color(0xFFEDEAF6),
    onSurfaceVariant = Color(0xFF6E6A86),
    outline = Color(0xFFB0AAC6),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

@Composable
fun BackchannelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
