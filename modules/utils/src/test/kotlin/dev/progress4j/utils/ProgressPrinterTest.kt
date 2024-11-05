package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import org.junit.jupiter.api.Assertions.assertLinesMatch
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Duration

class ProgressPrinterTest {
    @Test
    fun rendering() {
        val bytesOut = ByteArrayOutputStream()
        val printOut = PrintStream(bytesOut)
        val printer = ProgressPrinter(printOut, "│")

        val detWork = ProgressReport.create("Determinate work", 5)
        val bytesWork = ProgressReport.create("Bytes work", 50000, 10000, ProgressReport.Units.BYTES)
        val reports = listOf(
            ProgressReport.createIndeterminate(),

            // Indeterminate.
            ProgressReport.createIndeterminate("Some work"),
            ProgressReport.createIndeterminate("Some work").withCompleted(1),

            detWork,
            detWork.withIncremented(1),
            detWork.withIncremented(2),
            detWork.withIncremented(3),
            detWork.withIncremented(4),
            detWork.withIncremented(5),

            bytesWork,
            bytesWork.withIncremented(10000),
            ProgressReport.createIndeterminate("Some work"),
            ProgressReport.createIndeterminate("Some work").withCompleted(1),
            bytesWork.withIncremented(20000),
            bytesWork.withIncremented(30000),
            bytesWork.withIncremented(40000),
            bytesWork.withIncremented(50000),
        )

        val clock = TestClock(tickSize = Duration.ofMillis(10))
        printer.clock = clock
        for (r in reports)
            printer.report(r)

        val result = String(bytesOut.toByteArray())
        assertLinesMatch("""
           >  0.01 secs │        │ Working
           >  0.02 secs │        │ Some work
           >  0.03 secs │  done  │ Some work
           >  0.04 secs │   0.0% │   0 /   5 │ Determinate work
           >  0.05 secs │  20.0% │   1 /   5 │ Determinate work
           >  0.06 secs │  40.0% │   2 /   5 │ Determinate work
           >  0.07 secs │  60.0% │   3 /   5 │ Determinate work
           >  0.08 secs │  80.0% │   4 /   5 │ Determinate work
           >  0.09 secs │ 100.0% │   5 /   5 │ Determinate work
           >  0.10 secs │  20.0% │ 0.01 MB / 0.05 MB │ Bytes work
           >  0.11 secs │  40.0% │ 0.02 MB / 0.05 MB │ Bytes work
           >  0.12 secs │        │                   │ Some work
           >  0.13 secs │  done  │                   │ Some work
           >  0.14 secs │  60.0% │ 0.03 MB / 0.05 MB │ Bytes work
           >  0.15 secs │  80.0% │ 0.04 MB / 0.05 MB │ Bytes work
           >  0.16 secs │ 100.0% │ 0.05 MB / 0.05 MB │ Bytes work
           >  0.17 secs │ 100.0% │ 0.05 MB / 0.05 MB │ Bytes work
           >
        """.trimMargin(">").split("\n"), result.replace("\r", "").split("\n")
        )
    }
}
