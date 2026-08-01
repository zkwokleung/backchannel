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
 * App-side handle to the playback service. Builds a [MediaController] lazily and exposes it
 * as a StateFlow so composables can react when the connection is ready.
 */
class PlayerConnection(private val context: Context) {

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private var started = false

    @Synchronized
    fun connect() {
        if (started) return
        started = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { runCatching { _controller.value = future.get() } },
            MoreExecutors.directExecutor(),
        )
    }
}
