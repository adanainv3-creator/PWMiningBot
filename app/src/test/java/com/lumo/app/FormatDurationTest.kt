package com.lumo.app

import com.lumo.app.ui.components.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDurationTest {

    @Test
    fun `zero ms returns 0 colon 00`() {
        assertEquals("0:00", formatDuration(0L))
    }

    @Test
    fun `59 seconds formats correctly`() {
        assertEquals("0:59", formatDuration(59_000L))
    }

    @Test
    fun `exactly one minute`() {
        assertEquals("1:00", formatDuration(60_000L))
    }

    @Test
    fun `3 minutes 45 seconds`() {
        assertEquals("3:45", formatDuration(225_000L))
    }

    @Test
    fun `over an hour`() {
        assertEquals("63:04", formatDuration(3_784_000L))
    }
}
