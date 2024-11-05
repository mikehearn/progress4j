package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.math.roundToLong

/**
 * Sinks progress events and for determinate events, calculates how much progress was made in the past second over the given time window.
 *
 * This object is thread safe and will reset itself when a 100% complete progress report is received. It ignores sub-reports.
 *
 * @property windowSize How many seconds to accumulate stats over. Defaults to 30 seconds.
 * @property clock An injectable [Clock] useful for testing.
 */
class ProgressStatsAccumulator(
    val windowSize: Duration = Duration.ofSeconds(30),
    val clock: Clock = Clock.systemUTC()
) : ProgressReport.Tracker {
    private data class Value(val at: Instant, val value: Long) {
        override fun toString() = value.toString()
    }

    private val values = LinkedList<Value>()

    /**
     * The average rate of progress over the past second.
     */
    @get:Synchronized
    var averageProgress: Double = 0.0
        private set

    /**
     * How long we expect until completion, assuming that the rate of progress is even.
     */
    @get:Synchronized
    var estimatedTimeRemaining: Duration? = null
        private set

    private var lastReportAt: Long = 0

    private fun record(report: ProgressReport) {
        val value = report.completed
        require(value >= 0)

        // Throttle by only recording a new value every 10 milliseconds.
        val currentTimeMillis = System.currentTimeMillis()
        if (currentTimeMillis <= lastReportAt + 10)
            return
        lastReportAt = currentTimeMillis

        // Record new value.
        val now = clock.instant()
        values.add(Value(now, value))

        // Will record first and last values we have, so we can compute the delta.
        var first: Value? = null
        var last: Value? = null

        // Drop any that have fallen outside the window.
        val windowStart = now - windowSize
        val it = values.iterator()
        while (it.hasNext()) {
            val v = it.next()
            if (v.at.isBefore(windowStart)) {
                it.remove()
            } else {
                if (first == null)
                    first = v
                last = v
            }
        }

        if (first == null || last == null || first == last)
            return

        // Calculate average progress over the past second.
        val timespan = Duration.between(first.at, last.at)
        if (timespan > Duration.ofSeconds(1)) {
            val delta = last.value - first.value

            // A delta of < 0 means progress went backwards.
            averageProgress = if (delta > 0)
                delta / (timespan.toMillis() / 1000.0)
            else
                0.0

            // Guess a completion ETA.
            val remainingWork = report.expectedTotal - report.completed
            check(remainingWork >= 0)
            estimatedTimeRemaining = Duration.ofMillis(((remainingWork / averageProgress) * 1000).roundToLong())
        }
    }

    @Synchronized
    override fun report(progress: ProgressReport) {
        if (progress.complete) {
            values.clear()
            averageProgress = 0.0
            estimatedTimeRemaining = null
        } else if (!progress.indeterminate) {
            record(progress)
        }
    }
}
