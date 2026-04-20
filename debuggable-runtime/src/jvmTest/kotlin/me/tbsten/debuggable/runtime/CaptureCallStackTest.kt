package me.tbsten.debuggable.runtime

import me.tbsten.debuggable.runtime.stack.captureCallStack
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi::class)
class CaptureCallStackTest {

    // captureCallStack() strips all frames whose class starts with "me.tbsten.debuggable"
    // (including this test class itself) and "java.lang.Thread". On JVM, the JUnit runner
    // frames survive — so we verify those appear rather than the test class itself.
    @Test fun `result contains at least one JUnit runner frame`() {
        val stack = helperCapture()
        // Frames from the JUnit runner (org.junit, com.intellij, java/sun) should be visible.
        assertTrue(
            stack.contains("  at "),
            "expected at least one '  at ' line after stripping internal frames, got:\n$stack",
        )
    }

    @Test fun `debuggable-stack package is stripped`() {
        val stack = helperCapture()
        assertFalse(
            stack.contains("me.tbsten.debuggable.runtime.stack"),
            "captureCallStack itself should not appear in output, got:\n$stack",
        )
    }

    @Test fun `maxDepth limits the number of frames`() {
        val stack = captureCallStack(maxDepth = 2)
        val lines = stack.lines().filter { it.isNotBlank() }
        assertTrue(lines.size <= 2, "expected at most 2 frames, got:\n$stack")
    }

    @Test fun `empty string returned for maxDepth 0`() {
        val stack = captureCallStack(maxDepth = 0)
        assertTrue(stack.isEmpty(), "maxDepth=0 should return empty string, got: '$stack'")
    }

    private fun helperCapture(): String = captureCallStack(maxDepth = 20)
}
