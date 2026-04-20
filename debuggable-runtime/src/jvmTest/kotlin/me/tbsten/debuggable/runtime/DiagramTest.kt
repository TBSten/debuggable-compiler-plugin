@file:OptIn(me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi::class)

package me.tbsten.debuggable.runtime

import me.tbsten.debuggable.runtime.diagram.DiagramCapture
import me.tbsten.debuggable.runtime.diagram.buildDiagramString
import me.tbsten.debuggable.runtime.diagram.logDiagram
import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger
import me.tbsten.debuggable.runtime.logging.InMemoryLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramTest {

    @Test
    fun `buildDiagramString with two captures`() {
        val result = buildDiagramString(
            "process",
            "h + f",
            DiagramCapture("h", 123),
            DiagramCapture("f", 456),
        )
        assertEquals("process(h + f)  // h=123, f=456", result)
    }

    @Test
    fun `buildDiagramString with no captures omits comment`() {
        val result = buildDiagramString("tick", "")
        assertEquals("tick()", result)
    }

    @Test
    fun `buildDiagramString with null value`() {
        val result = buildDiagramString("check", "x", DiagramCapture("x", null))
        assertEquals("check(x)  // x=null", result)
    }

    @Test
    fun `logDiagram routes to provided logger`() {
        val logger = InMemoryLogger()
        logDiagram(
            null,
            "add",
            "a + b",
            DiagramCapture("a", 1),
            DiagramCapture("b", 2),
            logger = logger,
        )
        assertEquals(1, logger.messages.size)
        assertTrue("add(a + b)" in logger.messages[0])
        assertTrue("a=1" in logger.messages[0])
        assertTrue("b=2" in logger.messages[0])
    }

    @Test
    fun `logDiagram uses DefaultDebugLogger by default`() {
        val saved = DefaultDebugLogger.current
        val logger = InMemoryLogger()
        DefaultDebugLogger.current = logger
        try {
            logDiagram(null, "f", "x", DiagramCapture("x", 42))
            assertTrue(logger.messages.isNotEmpty())
        } finally {
            DefaultDebugLogger.current = saved
        }
    }
}
