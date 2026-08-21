package com.zkwokleung.backchannel.engine

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamFormatsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `video plus audio selection parses into two distinguishable tracks`() {
        val parsed = json.decodeFromString<YtVideoJson>(
            """
            {
              "id": "abc123",
              "title": "A video",
              "requested_formats": [
                {"url": "https://v.example/video", "ext": "mp4", "vcodec": "avc1.4d401f", "acodec": "none",
                 "http_headers": {"User-Agent": "ua-video"}},
                {"url": "https://v.example/audio", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2",
                 "http_headers": {"User-Agent": "ua-audio"}}
              ]
            }
            """
        )
        val video = parsed.requestedFormats.firstOrNull { it.url != null && it.hasVideo }
        val audio = parsed.requestedFormats.firstOrNull { it.url != null && it.hasAudio && !it.hasVideo }
        assertEquals("https://v.example/video", video?.url)
        assertEquals("https://v.example/audio", audio?.url)
        assertEquals("ua-audio", audio?.httpHeaders?.get("User-Agent"))
    }

    @Test
    fun `single-format selection keeps the top-level url and no requested formats`() {
        val parsed = json.decodeFromString<YtVideoJson>(
            """{"id": "abc123", "url": "https://v.example/audio", "ext": "m4a"}"""
        )
        assertEquals("https://v.example/audio", parsed.url)
        assertTrue(parsed.requestedFormats.isEmpty())
    }

    @Test
    fun `absent codec fields never classify as a playable track`() {
        val format = json.decodeFromString<YtRequestedFormat>("""{"url": "https://v.example/x"}""")
        assertTrue(!format.hasVideo && !format.hasAudio)
        assertNull(format.ext)
    }
}
