package me.tbsten.debuggable.runtime.logging

import java.nio.file.Files
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileLoggerTest {

    private fun tempFile(name: String = "debuggable-filelogger-test"): java.io.File {
        val tmp = Files.createTempFile(name, ".log")
        return java.io.File(tmp.pathString)
    }

    @Test
    fun `appends each message as a line`() {
        val file = tempFile()
        val logger = FileLogger(file)
        logger.log(null, "alpha", DebugLogger.NoValue)
        logger.log(null, "count", 42)

        val lines = file.readLines()
        assertEquals(listOf("alpha", "count: 42"), lines)
    }

    @Test
    fun `null property value is written as null string`() {
        val file = tempFile()
        val logger = FileLogger(file)
        logger.log(null, "label", null)
        assertEquals(listOf("label: null"), file.readLines())
    }

    @Test
    fun `append = true preserves existing content`() {
        val file = tempFile()
        file.writeText("prior\n")
        val logger = FileLogger(file, append = true)
        logger.log(null, "new", DebugLogger.NoValue)

        assertEquals(listOf("prior", "new"), file.readLines())
    }

    @Test
    fun `append = false truncates on construction`() {
        val file = tempFile()
        file.writeText("prior\n")
        val logger = FileLogger(file, append = false)
        logger.log(null, "fresh", DebugLogger.NoValue)

        assertEquals(listOf("fresh"), file.readLines())
    }

    @Test
    fun `creates parent directories on first write`() {
        val tmp = Files.createTempDirectory("debuggable-filelogger-dir")
        val file = java.io.File(tmp.toFile(), "nested/dir/out.log")
        assertTrue(!file.parentFile.exists(), "precondition: parent does not exist yet")
        val logger = FileLogger(file)
        logger.log(null, "created", DebugLogger.NoValue)
        assertTrue(file.exists())
        assertEquals(listOf("created"), file.readLines())
    }

    @Test
    fun `concurrent writers do not interleave lines`() {
        val file = tempFile()
        val logger = FileLogger(file)
        val total = 200
        val threads = (0 until 4).map { threadId ->
            Thread {
                repeat(total) { i ->
                    logger.log(null, "t$threadId-$i", DebugLogger.NoValue)
                }
            }.also { it.start() }
        }
        threads.forEach { it.join() }

        val lines = file.readLines()
        assertEquals(4 * total, lines.size)
        val pattern = Regex("""^t\d+-\d+$""")
        assertTrue(lines.all { pattern.matches(it) }, "unexpected line shape in output")
    }
}
