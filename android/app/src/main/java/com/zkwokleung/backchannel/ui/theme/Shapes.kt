package com.zkwokleung.backchannel.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One corner scale for the whole app. Before this, thumbnails were 8dp, artwork 16dp and avatars
 * circular with no relationship between them.
 *
 * - [Shapes.small] — thumbnails and chips
 * - [Shapes.medium] — cards and the mini-player
 * - [Shapes.large] — artwork, dialogs, sheets
 */
val BackchannelShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
