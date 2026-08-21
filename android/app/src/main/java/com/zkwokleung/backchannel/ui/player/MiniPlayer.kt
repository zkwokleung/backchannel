package com.zkwokleung.backchannel.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zkwokleung.backchannel.ui.common.Thumbnail
import com.zkwokleung.backchannel.ui.theme.Spacing

/**
 * Playback controls floating above the tab bar as a card.
 *
 * Without it, pausing meant navigating to the Playing tab first. It lives in the outer Scaffold's
 * `bottomBar`, so the Scaffold measures it and every screen's content padding grows to match — no
 * per-screen plumbing, and floating action buttons stay clear of it automatically. The caller
 * owns the outer margins; this card only fills the width it is given.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen, onClickLabel = "Open the player")
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Thumbnail(
                    model = state.artworkUri,
                    width = 64.dp,
                    shape = MaterialTheme.shapes.extraSmall,
                )

                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.artist?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(onClick = onSkipForward) {
                    Icon(Icons.Filled.Forward30, contentDescription = "Forward 30 seconds")
                }

                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        FilledIconButton(
                            onClick = onPlayPause,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                            )
                        }
                    }
                }
            }

            // Hairline progress along the bottom edge. No animation: it is fed by a 500ms tick,
            // and animating between samples would only add lag.
            val fraction = if (state.durationMs > 0) {
                (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { fraction },
                // Inset past the card's corner radius so the bar never pokes out of the curve.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                drawStopIndicator = {},
            )
        }
    }
}
