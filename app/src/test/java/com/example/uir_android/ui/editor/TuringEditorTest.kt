package com.example.uir_android.ui.editor

import com.example.uir_android.domain.model.TmExecutionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuringEditorTest {
    @Test
    fun `special symbols have one shared display conversion`() {
        val display = TuringEditor.displaySymbols("\\l\\o\\d")

        assertEquals("λΩ∂", display)
        assertEquals("\\l\\o\\d", TuringEditor.parserTokens(display))
    }

    @Test
    fun `row movement is bounded and preserves rows`() {
        val first = TuringEditor.newRow(1, "S0 a", "b R S0")
        val second = TuringEditor.newRow(2, "S0 b", "a R S0")

        val moved = TuringEditor.moveRow(listOf(first, second), rowId = 1, delta = 1)

        assertEquals(listOf(2L, 1L), moved.map { it.id })
        assertEquals(moved, TuringEditor.moveRow(moved, rowId = 1, delta = 5))
    }

    @Test
    fun `tape window supports negative positions`() {
        val state = TmExecutionState(tape = mapOf(-2 to 'a'), headPos = -2)

        val window = TuringEditor.buildTapeWindow(state, offset = 0, radius = 2)

        assertEquals((-4..0).toList(), window.map { it.index })
        assertTrue(window.single { it.index == -2 }.isHead)
        assertEquals('a', window.single { it.index == -2 }.symbol)
    }
}
