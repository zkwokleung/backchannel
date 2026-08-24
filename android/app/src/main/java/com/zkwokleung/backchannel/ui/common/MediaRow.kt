package com.zkwokleung.backchannel.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zkwokleung.backchannel.ui.theme.Spacing

/**
 * The single list row for the whole app, rendered as a card.
 *
 * Channels, uploads, watchlists and watchlist entries each hand-rolled their own row with
 * different padding, gaps, thumbnail sizes and title styles — so equivalent content sat at
 * different heights depending on which screen you were looking at. Everything routes through
 * here now.
 *
 * The outer padding is part of the row (5dp vertical, so consecutive cards sit 10dp apart)
 * rather than the list's content padding, keeping every screen's list full-bleed.
 *
 * Height is a minimum rather than a fixed value so rows grow at large font scales instead of
 * clipping. [highlighted] draws the "this is playing" border and pairs with the equalizer badge.
 */
@Composable
fun MediaRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    titleMaxLines: Int = 2,
    highlighted: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
    badge: @Composable (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (highlighted) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
        } else {
            null
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick, onClickLabel = onClickLabel)
                    } else {
                        Modifier
                    }
                )
                .defaultMinSize(minHeight = 72.dp)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            leading?.invoke()

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    badge?.invoke()
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = titleColor,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            trailing?.invoke(this)
        }
    }
}
