package dev.progress4j.utils

import dev.progress4j.api.ProgressReport

/**
 * Encapsulates exception handling and synchronization.
 *
 * The callback is wrapped with a try/catch block for all exceptions which are logged to the logger for [ProgressReport] and
 * then swallowed. Once a progress callback throws an exception it won't be invoked again until a completion report is sent.
 *
 * Additionally, this object is synchronized on before sending the progress. This ensures that if multiple threads are
 * working on a task simultaneously, the progress callback sees a serialized stream of reports.
 */
class ProgressTrackerInvoker(private val callback: ProgressReport.Tracker) : ProgressReport.Tracker, AutoCloseable {
    companion object {
        /** Wraps the callback such that it's synchronized and exceptions are logged/suppressed. If callback is already wrapped just returns it. */
        @JvmStatic
        fun wrap(callback: ProgressReport.Tracker): ProgressReport.Tracker {
            return if (callback is ProgressTrackerInvoker) callback else ProgressTrackerInvoker(callback)
        }

        private val logger by lazy { System.getLogger(ProgressTrackerInvoker::class.java.name) }
    }

    @Synchronized
    override fun close() {
        (callback as? AutoCloseable)?.close()
    }

    private var thrown = false

    @Synchronized
    override fun report(progress: ProgressReport) {
        try {
            if (!thrown || progress.complete) {
                callback.report(progress)
            }
        } catch (e: Exception) {
            thrown = true
            logger.log(System.Logger.Level.ERROR, "Exception in progress tracking callback, suppressing", e)
        }
    }
}
