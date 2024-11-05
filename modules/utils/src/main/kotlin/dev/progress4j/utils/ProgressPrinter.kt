package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import java.io.PrintStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/**
 * Outputs plain lines. This is useful when there's no console, e.g. because we're redirected to a file or output is being captured by
 * a CI tool, IDE, etc. Each unique message is printed with a timestamp from when the first report was received.
 *
 * @param output The print stream to emit lines to, defaults to [System.out].
 * @param verticalBar The character or string to use to divide columns. Defaults to the ordinary `|` symbol on Windows and the Unicode
 * full height bar symbol otherwise.
 */
class ProgressPrinter @JvmOverloads constructor(
    private val output: PrintStream = System.out,
    private val verticalBar: String = if (System.getProperty("os.name").startsWith("Windows")) "|" else "│"
) : ProgressReport.Tracker {
    private var startTime: Instant? = null
    private var maxWidth: Int = 0   // Widest completed/actual message seen so far.
    private var hasHadDeterminate: Boolean = false

    internal var clock: Clock = Clock.systemDefaultZone()

    @Synchronized
    override fun report(progress: ProgressReport) {
        if (startTime == null)
            startTime = clock.instant()

        val elapsedSecs = Duration.between(startTime, clock.instant()).toMillis() / 1000.0
        val message = progress.message ?: "Working"
        var prefix: String

        if (progress.indeterminate) {
            val word = if (progress.complete) "  done " else "       "
            prefix = String.format("%6.2f secs $verticalBar$word", elapsedSecs)
            if (hasHadDeterminate) prefix = "$prefix $verticalBar".padEnd(maxWidth)
            output.println("$prefix $verticalBar $message")
        } else {
            hasHadDeterminate = true
            var completed: String
            val expectedTotal: String
            if (progress.units == ProgressReport.Units.BYTES) {
                expectedTotal = Megabytes(progress.expectedTotal).toString()
                completed = Megabytes(progress.completed).toString()
            } else {
                expectedTotal = progress.expectedTotal.toString()
                completed = progress.completed.toString()
            }

            completed = completed.padStart(expectedTotal.length)

            val twoCols = String.format(
                "%6.2f secs $verticalBar %5.1f%% $verticalBar ",
                elapsedSecs,
                (progress.completed.toFloat() / progress.expectedTotal.toFloat()) * 100.0,
            )
            prefix = twoCols + String.format(
                "%3s / %3s", completed, expectedTotal,
            ).padStart(max(0, maxWidth - twoCols.length))
            output.println("$prefix $verticalBar $message")
        }
        maxWidth = max(prefix.length, maxWidth)
    }
}

@JvmInline
private value class Megabytes(val bytes: Long) {
    override fun toString(): String {
        return if (bytes > 1024 * 1024 * 1024)
            String.format("%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)
        else
            String.format("%.2f MB", bytes / 1024.0 / 1024.0)
    }
}
