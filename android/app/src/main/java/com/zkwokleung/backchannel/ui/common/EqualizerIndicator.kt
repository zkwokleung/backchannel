package com.zkwokleung.backchannel.ui.common

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The "this is what you're hearing" marker: three rounded bars borrowed from the launcher icon,
 * rising and falling while audio plays and holding still when it is paused.
 *
 * It is the app's one piece of ambient motion, and it earns that by carrying real state. Motion
 * is dropped entirely when the system asks for reduced motion, leaving the bars static.
 */
@Composable
fun EqualizerIndicator(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 14.dp,
) {
    // recommendedTimeoutMillis with notice+control set is Compose's signal for "user prefers
    // less motion / more time"; treat a long timeout as a request to stop animating.
    val reduceMotion = LocalAccessibilityManager.current
        ?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = 1_000L,
            containsIcons = false,
            containsText = false,
            containsControls = true,
        )
        ?.let { it > 5_000L } == true

    val animate = playing && !reduceMotion
    val transition = rememberInfiniteTransition(label = "equalizer")

    // Staggered durations so the bars never move as one block.
    val heights = listOf(
        barHeight(transition, animate, durationMillis = 620, from = 0.45f, to = 1f, label = "b1"),
        barHeight(transition, animate, durationMillis = 480, from = 1f, to = 0.35f, label = "b2"),
        barHeight(transition, animate, durationMillis = 720, from = 0.6f, to = 0.9f, label = "b3"),
    )

    Canvas(modifier.size(size)) {
        val barWidth = this.size.width / 5f
        val gap = barWidth / 2f
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        heights.forEachIndexed { index, fraction ->
            val barHeight = this.size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = index * (barWidth + gap),
                    y = this.size.height - barHeight,
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun barHeight(
    transition: androidx.compose.animation.core.InfiniteTransition,
    animate: Boolean,
    durationMillis: Int,
    from: Float,
    to: Float,
    label: String,
): Float {
    val animated by transition.animateFloat(
        initialValue = from,
        targetValue = if (animate) to else from,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(durationMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = label,
    )
    return if (animate) animated else from
}
