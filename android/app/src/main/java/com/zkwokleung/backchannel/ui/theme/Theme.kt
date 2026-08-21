package com.zkwokleung.backchannel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — the "Amp" direction: the violet family from the launcher icon plus a lime
// co-accent that marks actions and the currently-playing item.
val VioletDeep = Color(0xFF4C2A9E)
val VioletSoft = Color(0xFFA78BFA)
val GlyphTint = Color(0xFFDDD6FE)
val Lime = Color(0xFFD8F26A)
val OnLime = Color(0xFF1A2005)

/**
 * Video-surface colours. These deliberately do not follow the theme: letterbox bars must be
 * black whatever the app theme is, and controls overlay arbitrary video frames, so they need a
 * fixed tint plus a scrim rather than a theme colour that might vanish against the picture.
 */
val PlayerSurface = Color(0xFF000000)
val PlayerControlTint = Color(0xFFFFFFFF)

/**
 * Colors are deliberately fixed rather than derived from the wallpaper: the app should look like
 * Backchannel on every device, and stay in the launcher icon's violet family.
 *
 * Role mapping for the Amp scheme:
 * - `primary` is the lime — transport controls, FABs, the mini-player action.
 * - `secondary`/`secondaryContainer` is the violet family — the nav pill and tonal chips pick
 *   this up through Material defaults.
 * - `tertiary` stays reserved for "this is playing" accents, and is lime here so playing rows
 *   and the equalizer read as live.
 * - `surfaceTint` is the violet, not `primary`: lime elevation overlays would wash the
 *   violet-toned surfaces green.
 *
 * Every role is specified, including the tonal `surfaceContainer` ladder. Left unset, Material
 * fills those with its baseline purple, which is what made app bars and the FAB read as generic
 * despite the brand primary.
 */
private val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = OnLime,
    primaryContainer = Color(0xFF38420F),
    onPrimaryContainer = Color(0xFFEAF6B0),
    inversePrimary = Color(0xFF5A6B14),

    secondary = VioletSoft,
    onSecondary = Color(0xFF2E1065),
    secondaryContainer = VioletDeep,
    onSecondaryContainer = GlyphTint,

    tertiary = Lime,
    onTertiary = OnLime,
    tertiaryContainer = Color(0xFF343E10),
    onTertiaryContainer = Color(0xFFEAF6B0),

    background = Color(0xFF131022),
    onBackground = Color(0xFFF0EDFA),
    surface = Color(0xFF131022),
    onSurface = Color(0xFFF0EDFA),
    surfaceVariant = Color(0xFF2A2444),
    onSurfaceVariant = Color(0xFF8F89A8),
    surfaceTint = VioletSoft,

    surfaceContainerLowest = Color(0xFF0E0B18),
    surfaceContainerLow = Color(0xFF171331),
    surfaceContainer = Color(0xFF1D1834),
    surfaceContainerHigh = Color(0xFF241E42),
    surfaceContainerHighest = Color(0xFF2C2450),
    surfaceBright = Color(0xFF332B58),
    surfaceDim = Color(0xFF100D1C),

    inverseSurface = Color(0xFFF0EDFA),
    inverseOnSurface = Color(0xFF1D1834),

    outline = Color(0xFF4A4370),
    outlineVariant = Color(0xFF2A2444),
    scrim = Color(0xFF000000),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// The light scheme keeps the same role mapping; the lime darkens to an olive that holds 4.5:1
// on white, since the dark theme's lime fails contrast as a light-mode primary.
private val LightColors = lightColorScheme(
    primary = Color(0xFF4F6205),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4F293),
    onPrimaryContainer = OnLime,
    inversePrimary = Lime,

    secondary = Color(0xFF6D28D9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF312E81),

    tertiary = Color(0xFF4F6205),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4F293),
    onTertiaryContainer = OnLime,

    background = Color(0xFFFAF9FC),
    onBackground = Color(0xFF201B33),
    surface = Color(0xFFFAF9FC),
    onSurface = Color(0xFF201B33),
    surfaceVariant = Color(0xFFE9E6F2),
    onSurfaceVariant = Color(0xFF6B6684),
    surfaceTint = Color(0xFF6D28D9),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F4FA),
    surfaceContainer = Color(0xFFF2EFF8),
    surfaceContainerHigh = Color(0xFFECE9F4),
    surfaceContainerHighest = Color(0xFFE2DEED),
    surfaceBright = Color(0xFFFAF9FC),
    surfaceDim = Color(0xFFDDD8EA),

    inverseSurface = Color(0xFF322D46),
    inverseOnSurface = Color(0xFFF2EFF8),

    outline = Color(0xFFACA7C2),
    outlineVariant = Color(0xFFDCD8EA),
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
