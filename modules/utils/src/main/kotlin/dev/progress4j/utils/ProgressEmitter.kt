package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import java.lang.Long.min

// TODO(refactor): Make this non-thread safe, require users to wrap in a ProgressTrackerInvoker to get that, for consistency.
// TODO(refactor): Use a mutable progress report to increase performance.

/**
 * A thread safe accumulator of work done.
 *
 * Start with a base report and call [increment] to invoke [callback] with a report that has accumulated the specified amount of work
 * done. If [increment] is called more times than the difference between `baseReport.completed` and `baseReport.expectedTotal` i.e. you
 * do more work than you expected, the excess reports will be ignored.
 *
 * Closing the work emitter ensures a completion report is delivered (with `completed = baseReport.expectedTotal`). On construction the
 * base report is immediately passed to the [callback].
 */
class ProgressEmitter(baseReport: ProgressReport, private val callback: ProgressReport.Tracker) : AutoCloseable {
    /** The last report that was submitted. */
    private var lastReport: ProgressReport = baseReport.immutableReport()
        @Synchronized get
        @Synchronized set

    init {
        callback.report(baseReport)
    }

    /**
     * Returns a progress report based on this one with completed incremented by [amount], capped to the expected total, optionally with
     * the given [message].
     *
     * @return a derivative of this report with an equal or higher completed count.
     */
    @JvmOverloads
    @Synchronized
    fun increment(amount: Int = 1, message: String? = null) {
        val lastReport = lastReport
        if (lastReport.complete) return
        val total = lastReport.expectedTotal
        val completed = lastReport.completed
        val report = lastReport
            .withCompleted(min(completed + amount, total))
            .let { if (message != null) it.withMessage(message) else it }
        this.lastReport = report
        callback.report(this.lastReport)
    }

    /**
     * Emits a new progress report with the same completed and expected total as the last one, but with the given [message].
     */
    @set:Synchronized
    var message: String?
        get() = lastReport.message
        set(value) {
            lastReport = lastReport.withMessage(value)
            callback.report(this.lastReport)
        }

    /**
     * If the last emitted report wasn't a completion, emits a completion of the expected total.
     */
    @Synchronized
    override fun close() {
        val r = lastReport
        if (lastReport.completed < lastReport.expectedTotal) {
            lastReport = r.withCompleted(r.expectedTotal)
            callback.report(lastReport)
        }
    }
}
