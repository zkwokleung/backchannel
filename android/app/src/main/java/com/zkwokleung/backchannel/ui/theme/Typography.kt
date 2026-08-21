package com.zkwokleung.backchannel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zkwokleung.backchannel.R

/**
 * Space Grotesk carries the brand voice on headlines, titles and labels; body copy stays on the
 * system face, which holds up better in dense two-line rows at small sizes.
 *
 * Only W500 and W700 are bundled, mirroring the earlier Roboto rule: on API 26–27 intermediate
 * weights silently round, so styles using this family must stick to Medium and Bold.
 */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/**
 * Type scale for Backchannel — deliberate weights and tracking rather than Material's defaults.
 *
 * The most pervasive change is [Typography.bodyLarge]'s tracking — Material's 0.5sp default is a
 * large part of why stock apps read as templated. Space Grotesk needs slightly negative tracking
 * at display sizes or it sets too loose.
 */
val BackchannelTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(
            fontFamily = SpaceGrotesk,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        ),
        titleLarge = titleLarge.copy(
            fontFamily = SpaceGrotesk,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
        ),
        titleMedium = titleMedium.copy(
            fontFamily = SpaceGrotesk,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
        ),
        // Section eyebrows.
        titleSmall = titleSmall.copy(
            fontFamily = SpaceGrotesk,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        ),
        bodyLarge = bodyLarge.copy(
            lineHeight = 22.sp,
            letterSpacing = 0.15.sp,
        ),
        bodyMedium = bodyMedium.copy(
            lineHeight = 20.sp,
            letterSpacing = 0.15.sp,
        ),
        bodySmall = bodySmall.copy(
            letterSpacing = 0.2.sp,
        ),
        labelLarge = labelLarge.copy(
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Medium,
        ),
        labelMedium = labelMedium.copy(
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Medium,
        ),
        labelSmall = labelSmall.copy(
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Medium,
        ),
    )
}

/**
 * Durations, positions and versions.
 *
 * Uses the body face with tabular figures rather than a monospace family — mono digits sit badly
 * next to the body text in a row's metadata line. Where the `tnum` feature is unavailable (some
 * OEM system fonts) this degrades silently to proportional figures.
 */
val Typography.timecode: TextStyle
    get() = labelMedium.copy(fontFamily = FontFamily.Default, fontFeatureSettings = "tnum")
