package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import java.util.concurrent.Executor

/**
 * Simply re-invokes the specified [callback] on the given [executor] with an immutable version of the report.
 *
 * If the target executor is a thread pool of some sort you may wish to wrap the target callback in an [ProgressTrackerInvoker] to stop errors in the
 * progress handler propagating out of the thread.
 *
 * A typical way to use this is by specifying a GUI event loop dispatch function as the executor, e.g. `new
 * ProgressRelay(Platform::runLater, ::updateProgressBar)` to ensure progress reports end up on the correct thread.
 */
class ProgressRelay(private val executor: Executor, private val callback: ProgressReport.Tracker) : ProgressReport.Tracker {
    override fun report(progress: ProgressReport) {
        val immutable = progress.immutableReport()
        executor.execute {
            callback.report(immutable)
        }
    }
}
