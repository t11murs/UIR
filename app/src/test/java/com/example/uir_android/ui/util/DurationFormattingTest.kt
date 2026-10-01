package com.example.uir_android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormattingTest {
    @Test
    fun `duration below one hour names minutes and seconds explicitly`() {
        assertEquals("1 мин 40 сек", formatRemainingDuration(100))
        assertEquals("45 мин 00 сек", formatRemainingDuration(2_700))
    }

    @Test
    fun `duration above one hour names every unit explicitly`() {
        assertEquals("1 ч 40 мин 00 сек", formatRemainingDuration(6_000))
    }

    @Test
    fun `negative duration is displayed as zero`() {
        assertEquals("0 мин 00 сек", formatRemainingDuration(-1))
    }
}
