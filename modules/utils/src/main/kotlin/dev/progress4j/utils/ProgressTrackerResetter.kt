package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import java.util.concurrent.Callable

/**
 * A callback that delegates a stream of reports to another callback that it creates, and re-creates when a completion is delivered.
 *
 * This allows you to just send a continuous stream of reports with completion mixed into the middle, whilst maintaining the invariant
 * that a progress tracker won't receive events after an operation is considered to be completed.
 */
class ProgressTrackerResetter<T : ProgressReport.Tracker>(private val factory: Callable<T>) : ProgressReport.Tracker, AutoCloseable {
    /** The current instance that reports are being forwarded too, or null if there isn't currently one (i.e. nothing was yet received). */
    var current: T? = null
        private set

    override fun report(progress: ProgressReport) {
        if (current == null)
            current = factory.call()
        current!!.report(progress)
        if (progress.complete) {
            close()
            current = null
        }
    }

    override fun close() {
        (current as? AutoCloseable)?.close()
    }
}
