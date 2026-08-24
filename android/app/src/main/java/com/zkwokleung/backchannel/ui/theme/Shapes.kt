package com.zkwokleung.backchannel.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One corner scale for the whole app, bumped for the Amp direction — chunky corners are part of
 * the identity, so the whole ladder moves together rather than per-component overrides.
 *
 * - [Shapes.extraSmall] — mini-player thumbnail
 * - [Shapes.small] — thumbnails and chips
 * - [Shapes.medium] — cards, list rows and the mini-player
 * - [Shapes.large] — artwork, dialogs, sheets
 * - [Shapes.extraLarge] — the transport play button
 */
val BackchannelShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
