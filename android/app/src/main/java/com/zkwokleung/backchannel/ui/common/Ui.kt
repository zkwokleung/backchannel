package com.zkwokleung.backchannel.ui.common

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zkwokleung.backchannel.AppContainer
import com.zkwokleung.backchannel.appContainer
import java.util.Locale

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.appContainer
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

/**
 * Like [appViewModel], but scoped to the Activity rather than the current navigation entry, so
 * every caller shares one instance.
 *
 * The player needs this: the mini-player and both player screens must observe the same state and
 * register a single listener on the MediaController. Resolved per-destination (the default),
 * each screen would build its own.
 */
@Composable
inline fun <reified VM : ViewModel> appActivityViewModel(
    crossinline create: (AppContainer) -> VM,
): VM {
    val context = LocalContext.current
    val container = context.appContainer
    val factory = viewModelFactory { initializer { create(container) } }
    // Falls back to the default owner outside an Activity (previews) rather than crashing.
    val owner = context.findActivity()
    return if (owner != null) {
        viewModel(viewModelStoreOwner = owner, factory = factory)
    } else {
        viewModel(factory = factory)
    }
}

/** Unwraps the ContextWrapper chain Compose may hand us to reach the hosting Activity. */
fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

fun formatDuration(seconds: Long?): String {
    if (seconds == null || seconds < 0) return "–:––"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

fun formatMillis(millis: Long): String = formatDuration(millis / 1000)
