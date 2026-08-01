package com.zkwokleung.backchannel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — the same family as the launcher icon.
val IndigoDeep = Color(0xFF312E81)
val VioletMid = Color(0xFF4C2A9E)
val VioletBright = Color(0xFF7C3AED)
val VioletSoft = Color(0xFFA78BFA)
val GlyphTint = Color(0xFFDDD6FE)

/**
 * Colors are deliberately fixed rather than derived from the wallpaper: the app should look like
 * Backchannel on every device, and match its own launcher icon.
 *
 * Every role is specified, including the tonal `surfaceContainer` ladder and `surfaceTint`. Left
 * unset, Material fills those with its baseline purple, which is what made app bars and the FAB
 * read as generic despite the brand primary.
 */
private val DarkColors = darkColorScheme(
    primary = VioletSoft,
    onPrimary = Color(0xFF2E1065),
    primaryContainer = VioletMid,
    onPrimaryContainer = GlyphTint,
    inversePrimary = Color(0xFF6D28D9),

    secondary = GlyphTint,
    onSecondary = Color(0xFF2E1065),
    secondaryContainer = Color(0xFF3B3555),
    onSecondaryContainer = GlyphTint,

    // Reserved for "this is playing" accents.
    tertiary = VioletBright,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3B2A6B),
    onTertiaryContainer = GlyphTint,

    background = Color(0xFF17151F),
    onBackground = Color(0xFFECE9F6),
    surface = Color(0xFF17151F),
    onSurface = Color(0xFFECE9F6),
    surfaceVariant = Color(0xFF2A2638),
    onSurfaceVariant = Color(0xFF9D97B5),
    surfaceTint = VioletSoft,

    surfaceContainerLowest = Color(0xFF131119),
    surfaceContainerLow = Color(0xFF1C1926),
    surfaceContainer = Color(0xFF201D2B),
    surfaceContainerHigh = Color(0xFF2A2638),
    surfaceContainerHighest = Color(0xFF332E44),
    surfaceBright = Color(0xFF3A354C),
    surfaceDim = Color(0xFF121017),

    inverseSurface = Color(0xFFECE9F6),
    inverseOnSurface = Color(0xFF201D2B),

    outline = Color(0xFF544E6B),
    outlineVariant = Color(0xFF3A3548),
    scrim = Color(0xFF000000),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = IndigoDeep,
    inversePrimary = VioletSoft,

    secondary = VioletMid,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E0F5),
    onSecondaryContainer = Color(0xFF232136),

    tertiary = VioletBright,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF2E1065),

    background = Color(0xFFFAF9FD),
    onBackground = Color(0xFF232136),
    surface = Color(0xFFFAF9FD),
    onSurface = Color(0xFF232136),
    surfaceVariant = Color(0xFFEDEAF6),
    onSurfaceVariant = Color(0xFF6E6A86),
    surfaceTint = Color(0xFF6D28D9),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F4FC),
    surfaceContainer = Color(0xFFF4F1FA),
    surfaceContainerHigh = Color(0xFFEEEAF6),
    surfaceContainerHighest = Color(0xFFE2DCEF),
    surfaceBright = Color(0xFFFAF9FD),
    surfaceDim = Color(0xFFDED8EC),

    inverseSurface = Color(0xFF322F42),
    inverseOnSurface = Color(0xFFF4F1FA),

    outline = Color(0xFFB0AAC6),
    outlineVariant = Color(0xFFDDD8EA),
    scrim = Color(0xFF000000),

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun BackchannelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BackchannelTypography,
        shapes = BackchannelShapes,
        content = content,
    )
}
