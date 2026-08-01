package com.zkwokleung.backchannel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale for Backchannel — the system face with deliberate weights and tracking rather than
 * Material's defaults.
 *
 * Only W400/W500/W700 are used: Roboto ships Regular, Medium and Bold as real faces, and on
 * API 26–27 an intermediate weight like SemiBold silently rounds to Bold, so a 600 would render
 * differently across the devices we support.
 *
 * The most pervasive change is [Typography.bodyLarge]'s tracking — Material's 0.5sp default is a
 * large part of why stock apps read as templated.
 */
val BackchannelTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp,
        ),
        titleLarge = titleLarge.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
        ),
        titleMedium = titleMedium.copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp,
        ),
        // Section eyebrows.
        titleSmall = titleSmall.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
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
    )
}

/**
 * Durations, positions and versions.
 *
 * Uses the normal face with tabular figures rather than a monospace family — mono digits sit
 * badly next to Roboto in a row's metadata line. Where the `tnum` feature is unavailable (some
 * OEM system fonts) this degrades silently to proportional figures.
 */
val Typography.timecode: TextStyle
    get() = labelMedium.copy(fontFeatureSettings = "tnum")
