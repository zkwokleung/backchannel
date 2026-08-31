package com.zkwokleung.backchannel.ui

import com.zkwokleung.backchannel.ui.common.formatBytes
import com.zkwokleung.backchannel.ui.common.formatDate
import com.zkwokleung.backchannel.ui.common.formatDuration
import com.zkwokleung.backchannel.ui.common.formatMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class FormatDurationTest {

    @Test
    fun `sub-hour durations omit the hour field`() {
        assertEquals("0:07", formatDuration(7))
        assertEquals("13:56", formatDuration(836))
        assertEquals("59:59", formatDuration(3599))
    }

    @Test
    fun `hour-long durations pad minutes and seconds`() {
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("2:05:09", formatDuration(7509))
    }

    @Test
    fun `unknown durations render as a placeholder rather than zero`() {
        assertEquals("–:––", formatDuration(null))
        assertEquals("–:––", formatDuration(-1))
    }

    @Test
    fun `millisecond helper truncates to whole seconds`() {
        assertEquals("0:01", formatMillis(1_999))
        assertEquals("1:40", formatMillis(100_000))
    }

    @Test
    fun `byte counts step through KB, MB and GB`() {
        assertNull(formatBytes(0))
        assertNull(formatBytes(-5))
        assertEquals("512 KB", formatBytes(512 * 1024))
        assertEquals("1.5 MB", formatBytes(1_572_864))
        assertEquals("2.25 GB", formatBytes(2_415_919_104))
    }

    @Test
    fun `download dates render as a medium local date`() {
        // 2026-08-26T12:00Z
        assertEquals("Aug 26, 2026", formatDate(1_787_745_600_000, ZoneOffset.UTC, Locale.US))
    }
}
