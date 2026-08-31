package com.zkwokleung.backchannel.download

import com.zkwokleung.backchannel.engine.EngineException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class DownloadFailureTest {

    @Test
    fun `a cancelled download is reported as cancelled, not as an error`() {
        assertEquals(DownloadFailure.Cancelled, DownloadFailure.from(DownloadCancelledException()))
    }

    @Test
    fun `disk exhaustion is recognised from the pre-flight check and from yt-dlp tracebacks`() {
        assertEquals(DownloadFailure.DiskFull, DownloadFailure.from(DiskFullException()))
        val traceback = EngineException(
            "Extraction failed.",
            IOException("OSError: [Errno 28] No space left on device: '/data/x.part'"),
        )
        assertEquals(DownloadFailure.DiskFull, DownloadFailure.from(traceback))
    }

    @Test
    fun `engine errors keep the engine's own wording`() {
        val failure = DownloadFailure.from(EngineException("That video is private."))
        assertEquals(DownloadFailure.Engine("That video is private."), failure)
    }

    @Test
    fun `unknown throwables get the generic extraction message`() {
        val failure = DownloadFailure.from(IllegalStateException("boom")) as DownloadFailure.Engine
        assertEquals("Extraction failed. If this keeps happening, update yt-dlp in Settings.", failure.message)
    }
}
