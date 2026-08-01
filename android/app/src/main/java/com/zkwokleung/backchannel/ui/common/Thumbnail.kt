package com.zkwokleung.backchannel.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

/** Standard sizes so the same YouTube image is never shown at three different aspect ratios. */
object ThumbnailDefaults {
    val Width: Dp = 112.dp
    val AspectRatio: Float = 16f / 9f
    val AvatarSize: Dp = 44.dp
}

/**
 * A video thumbnail.
 *
 * The container's own colour is the placeholder, so a slow or failed load shows a filled tile
 * rather than a hole punched in the row. [contentDescription] is null by default because these
 * sit inside rows whose title already names them — passing one makes TalkBack read the title
 * twice.
 */
@Composable
fun Thumbnail(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    width: Dp = ThumbnailDefaults.Width,
    aspectRatio: Float = ThumbnailDefaults.AspectRatio,
    shape: Shape = MaterialTheme.shapes.small,
    overlay: @Composable (BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier
            .width(width)
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        ImageOrFallback(model, contentDescription, fallbackIconSize = 20.dp)
        overlay?.invoke(this)
    }
}

/** A channel avatar. Circular, and cropped — Coil's default `Fit` letterboxes non-square art. */
@Composable
fun ChannelAvatar(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = ThumbnailDefaults.AvatarSize,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        ImageOrFallback(model, contentDescription, fallbackIconSize = 18.dp)
    }
}

@Composable
private fun ImageOrFallback(
    model: Any?,
    contentDescription: String?,
    fallbackIconSize: Dp,
) {
    var failed by remember(model) { mutableStateOf(false) }

    if (model != null && !failed) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            // onState rather than SubcomposeAsyncImage: no extra subcomposition per list row.
            onState = { state -> failed = state is AsyncImagePainter.State.Error },
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(fallbackIconSize),
            )
        }
    }
}
