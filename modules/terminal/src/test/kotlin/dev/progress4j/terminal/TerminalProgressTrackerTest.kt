package dev.progress4j.terminal

import dev.progress4j.api.ProgressReport
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalProgressTrackerTest {
    @Test
    fun `output factory does not replace or write standard output`() {
        val originalOut = System.out
        val stdout = ByteArrayOutputStream()
        val progress = ByteArrayOutputStream()
        System.setOut(PrintStream(stdout))
        try {
            val tracker = TerminalProgressTracker.forOutput(PrintStream(progress))
            tracker.report(ProgressReport.create("Downloading", 100, 1, ProgressReport.Units.BYTES))
            Thread.sleep(75)
            (tracker as AutoCloseable).close()

            assertEquals(0, stdout.size())
            assertTrue(progress.size() > 0)
        } finally {
            System.setOut(originalOut)
        }
    }
}
