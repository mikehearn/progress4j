package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import java.lang.System.Logger.Level
import java.time.Duration
import java.time.Instant

/**
 * A progress callback that logs the start and end of the operation. Not reusable.
 */
class ProgressLogger(private val level: Level = Level.DEBUG) : ProgressReport.Tracker {
    private var started: Instant? = null
    private var firstMessage: String? = null

    override fun report(progress: ProgressReport) {
        val completed = progress.complete

        if (started == null && !completed) {
            val m = progress.message
            val prefix = if (m != null) {
                firstMessage = m
                "Starting operation: $m"
            } else {
                "Starting operation ..."
            }
            val suffix = if (progress.expectedTotal > 1) {
                " [${progress.expectedTotal}]"
            } else ""
            logger.log(level, prefix + suffix)
            started = Instant.now()
        } else if (started != null && completed) {
            val duration = Duration.between(started!!, Instant.now())
            val secs = duration.toMillis().toDouble() / 1000.0
            logger.log(level, "Finished after $secs seconds" + if (firstMessage != null) ": $firstMessage" else "")
        }
        // else received an intermediate progress report which we don't want to log.
    }

    companion object {
        private val logger by lazy { System.getLogger(ProgressLogger::class.java.name) }
    }
}
