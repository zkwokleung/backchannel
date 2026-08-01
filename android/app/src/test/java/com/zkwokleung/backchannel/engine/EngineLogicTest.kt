package com.zkwokleung.backchannel.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendlyMessageTest {

    @Test
    fun `network failures read as connectivity problems`() {
        val message = friendlyMessage("ERROR: Unable to download webpage: <urlopen error getaddrinfo failed>")
        assertEquals("Can't reach YouTube — check your connection.", message)
    }

    @Test
    fun `private videos are called out specifically`() {
        assertEquals("That video is private.", friendlyMessage("ERROR: [youtube] abc: Private video"))
    }

    @Test
    fun `unknown channels point at the input`() {
        val message = friendlyMessage("ERROR: [youtube:tab] Unable to recognize tab page")
        assertEquals("Couldn't find that channel — check the handle or URL.", message)
    }

    @Test
    fun `extraction breakage suggests updating yt-dlp`() {
        val message = friendlyMessage("ERROR: [youtube] nsig extraction failed: Some formats may be missing")
        assertTrue(message.contains("Update yt-dlp"))
    }

    @Test
    fun `unrecognized errors still produce actionable text`() {
        val message = friendlyMessage("kaboom")
        assertTrue(message.isNotBlank())
        assertTrue(message.contains("Settings"))
    }

    @Test
    fun `null message does not crash`() {
        assertTrue(friendlyMessage(null).isNotBlank())
    }
}
