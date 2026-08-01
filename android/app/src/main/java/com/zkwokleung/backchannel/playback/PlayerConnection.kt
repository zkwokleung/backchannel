package com.zkwokleung.backchannel.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-side handle to the playback service. Builds a [MediaController] lazily and exposes it as
 * a StateFlow so composables can react when the connection is ready.
 *
 * The connection is deliberately releasable. A live controller binds the service with
 * BIND_AUTO_CREATE, which keeps it alive even after `stopSelf()`, so the UI hands the binding
 * back when it goes away — active playback keeps running because the service is also started
 * and in the foreground, while idle playback can finally shut down.
 */
class PlayerConnection(private val context: Context) {

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private var pending: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    @Synchronized
    fun connect() {
        if (pending != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    // A dead controller silently no-ops play/prepare, which looks like the app
                    // ignoring taps; drop it so the next connect() rebuilds.
                    forget(controller)
                }
            })
            .buildAsync()
        pending = future
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null) {
                    // Build failed — clear state so a later connect() can retry.
                    forget(null)
                } else {
                    _controller.value = controller
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    @Synchronized
    private fun forget(controller: MediaController?) {
        if (controller == null || _controller.value === controller) _controller.value = null
        pending = null
    }

    /** Releases the controller, unbinding the service. Safe to call when not connected. */
    @Synchronized
    fun disconnect() {
        pending?.let(MediaController::releaseFuture)
        pending = null
        _controller.value = null
    }
}
