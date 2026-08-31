package com.zkwokleung.backchannel.data

import com.zkwokleung.backchannel.engine.StreamMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class LocalMediaSelectionTest {

    private val audio = LocalMedia(File("vid.m4a"), StreamMode.AUDIO)
    private val video = LocalMedia(File("vid.mp4"), StreamMode.VIDEO)

    @Test
    fun `nothing saved means stream it`() {
        assertNull(LocalMediaSelection.resolve(null, StreamMode.AUDIO))
        assertNull(LocalMediaSelection.resolve(null, StreamMode.VIDEO))
    }

    @Test
    fun `a saved audio track serves audio only`() {
        assertEquals(audio.file, LocalMediaSelection.resolve(audio, StreamMode.AUDIO))
        assertNull(LocalMediaSelection.resolve(audio, StreamMode.VIDEO))
    }

    @Test
    fun `a saved video carries both tracks and serves either mode`() {
        assertEquals(video.file, LocalMediaSelection.resolve(video, StreamMode.AUDIO))
        assertEquals(video.file, LocalMediaSelection.resolve(video, StreamMode.VIDEO))
    }
}
