package com.zkwokleung.backchannel.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing scale. Every gap in the app comes from here — the screens previously used 2, 4, 6,
 * 8, 10, 12, 14, 16, 24 and 32 with no system, so equivalent rows on different screens sat at
 * different heights.
 */
object Spacing {
    /** Tight pairings: a title and the metadata directly under it. */
    val xs = 4.dp

    /** Related elements inside a row. */
    val sm = 8.dp

    /** Thumbnail to text. */
    val md = 12.dp

    /** Screen edges and row insets — the app's default. */
    val lg = 16.dp

    /** Separating groups within a screen. */
    val xl = 24.dp

    /** Empty-state and hero breathing room. */
    val xxl = 32.dp
}

/** Standard vertical inset for a list row; paired with [Spacing.lg] horizontally. */
val RowVerticalPadding = 10.dp

/** Clearance so the last list row scrolls clear of a floating action button. */
val FabListClearance = 88.dp
