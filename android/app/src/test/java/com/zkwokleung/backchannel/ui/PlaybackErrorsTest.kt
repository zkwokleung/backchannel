package com.zkwokleung.backchannel.ui

import com.zkwokleung.backchannel.playback.StreamResolutionException
import com.zkwokleung.backchannel.ui.player.readablePlaybackError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class PlaybackErrorsTest {

    // Stands in for ExoPlayer's wrapper, whose own constructor needs an Android runtime.
    private fun playbackError(cause: Throwable?) = RuntimeException("Source error", cause)

    @Test
    fun `extraction failures keep the engine's own wording`() {
        val error = playbackError(
            StreamResolutionException("That video is private.", null)
        )
        assertEquals("That video is private.", readablePlaybackError(error))
    }

    @Test
    fun `a DNS failure reads as a connectivity problem, not a hostname dump`() {
        val error = playbackError(
            IOException(
                "Unable to resolve host",
                UnknownHostException("Unable to resolve host \"rr2---sn-x.googlevideo.com\""),
            )
        )
        val message = readablePlaybackError(error)

        assertEquals("Can't reach YouTube — check your connection.", message)
        assertFalse(message.contains("googlevideo"))
        assertFalse(message.contains("Exception"))
    }

    @Test
    fun `unclassified IO failures still suggest an action`() {
        val message = readablePlaybackError(playbackError(IOException("boom")))
        assertTrue(message.contains("retry"))
    }

    @Test
    fun `unknown failures never surface a raw exception string`() {
        val message = readablePlaybackError(playbackError(IllegalStateException("kaboom")))
        assertFalse(message.contains("kaboom"))
        assertTrue(message.isNotBlank())
    }

    @Test
    fun `a cause-less error is still explained`() {
        assertTrue(readablePlaybackError(playbackError(null)).isNotBlank())
    }
}
