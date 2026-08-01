package com.zkwokleung.backchannel.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChannelUrlTest {

    private fun normalize(input: String) = normalizeChannelUrl(input)

    @Test
    fun `handles gain the canonical prefix`() {
        assertEquals("https://www.youtube.com/@veritasium", normalize("@veritasium"))
        assertEquals("https://www.youtube.com/@veritasium", normalize("veritasium"))
        assertEquals("https://www.youtube.com/@veritasium", normalize("  @veritasium  "))
    }

    @Test
    fun `channel ids become channel URLs`() {
        assertEquals(
            "https://www.youtube.com/channel/UCHnyfMqiRRG1u-2MsSQLbXA",
            normalize("UCHnyfMqiRRG1u-2MsSQLbXA"),
        )
    }

    @Test
    fun `a tab suffix copied from the address bar is trimmed back to the channel`() {
        // Callers append /videos themselves; leaving this on produced /videos/videos.
        assertEquals(
            "https://www.youtube.com/@veritasium",
            normalize("https://www.youtube.com/@veritasium/videos"),
        )
        assertEquals(
            "https://www.youtube.com/@veritasium",
            normalize("https://www.youtube.com/@veritasium/streams"),
        )
        assertEquals(
            "https://www.youtube.com/channel/UCHnyfMqiRRG1u-2MsSQLbXA",
            normalize("https://www.youtube.com/channel/UCHnyfMqiRRG1u-2MsSQLbXA/playlists"),
        )
    }

    @Test
    fun `query strings, fragments and trailing slashes are dropped`() {
        assertEquals(
            "https://www.youtube.com/@veritasium",
            normalize("https://www.youtube.com/@veritasium/videos/?view=0&sort=dd"),
        )
        assertEquals(
            "https://www.youtube.com/@veritasium",
            normalize("https://www.youtube.com/@veritasium#about"),
        )
    }

    @Test
    fun `a plain channel URL is left alone`() {
        assertEquals(
            "https://www.youtube.com/@veritasium",
            normalize("https://www.youtube.com/@veritasium"),
        )
    }

    @Test
    fun `video links are rejected with an explanation instead of a bogus channel fetch`() {
        listOf(
            "https://youtu.be/PqtggjVAi8M",
            "https://www.youtube.com/watch?v=PqtggjVAi8M",
            "https://www.youtube.com/shorts/abc123",
            "https://www.youtube.com/live/abc123",
        ).forEach { url ->
            val failure = assertThrows(EngineException::class.java) { normalize(url) }
            assertEquals("That's a link to a video, not a channel.", failure.message)
        }
    }
}
