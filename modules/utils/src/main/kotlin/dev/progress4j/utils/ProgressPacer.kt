package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Receives progress reports on any arbitrary thread, and forwards them on a separate single thread at the given rate.
 * This is useful for disconnecting the speed of progress report delivery from the frequency at which they're printed or rendered.
 *
 * The pacer must be closed when no longer needed. Indeterminate or complete reports are always forwarded regardless of the pacing, so a
 * large flood of indeterminate progress tasks won't be throttled.
 *
 * @param hz How many times per second to forward a progress report.
 * @param callback Destination that will receive the paced reports on another thread.
 * @param alwaysTick If true, then the last received progress report will always be sent at the given frequency even if it hasn't changed,
 * i.e. the download [callback] must be ready to receive redundant reports. This can be useful when the pacing thread is used to drive an
 * animation.
 */
class ProgressPacer(
    private val callback: ProgressReport.Tracker,
    private val hz: Float = 30.0f,
    private val alwaysTick: Boolean = false
) : ProgressReport.Tracker, AutoCloseable {
    private val targetGapMsec = (1000.0 / hz).toLong()

    private val lock = ReentrantLock()
    private val cond = lock.newCondition()

    private var lastReport: ProgressReport? = null
    private var lastForwardedReport: ProgressReport? = null
    private var lastReportInSequence: ProgressReport? = null

    // Completion reports that we don't want downstream to miss.
    private var guaranteedForwarding = LinkedList<ProgressReport>()
    private var closed: Boolean = false

    private inner class PacerThread : Runnable {
        override fun run() {
            var running = true
            while (running) {
                val toForward: List<ProgressReport> = lock.withLock {
                    cond.await(targetGapMsec, TimeUnit.MILLISECONDS)

                    if (closed)
                        running = false

                    // Get a list of the report(s) we need to forward to the delegate. Might be >1 if there were completion reports too.
                    // If we're always ticking then we may repeatedly re-forward the last received report over and over, which can be useful
                    // if the downstream progress tracker is driving an animation.
                    val last = lastReport
                    val reportToForward = if (last != lastForwardedReport) {
                        // It's changed since our last tick.
                        last
                    } else {
                        // We got nothing new in the lastReport variable.
                        null
                    }

                    (guaranteedForwarding + listOfNotNull(reportToForward)).also { guaranteedForwarding.clear() }
                }

                try {
                    if (toForward.isEmpty() && alwaysTick) {
                        lastForwardedReport?.let(callback::report)
                    } else {
                        for (p in toForward) {
                            callback.report(p)
                            lastForwardedReport = p
                        }
                    }
                    lastReportInSequence = lastForwardedReport
                } catch (e: InterruptedException) {
                    running = false
                } catch (e: Exception) {
                    // Give up progress tracking if we take an exception from the delegate.
                    logger.log(System.Logger.Level.ERROR, null as String?, e)
                    running = false
                }
            }
        }
    }

    private val pacerThread = Thread(PacerThread(), "Progress report pacer").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }

    override fun report(progress: ProgressReport) {
        lock.withLock {
            // Figure out if any of the reports in the progress report hierarchy are considered 'important' i.e. a change of state that
            // isn't the mere reporting of further work done (which can feasibly be skipped) but e.g. completion, becoming indeterminate.
            val lastDifferent = lastReportInSequence?.let { progress.structuralComparison(it) } != true
            lastReport = if (lastDifferent) {
                guaranteedForwarding.add(progress.immutableReport())
                null
            } else {
                progress.immutableReport()
            }
            lastReportInSequence = progress.immutableReport()
        }
    }

    override fun close() {
        lock.withLock {
            if (closed)
                return
            closed = true
            if (guaranteedForwarding.size > 0)
                logger.log(System.Logger.Level.TRACE) { "Closing with ${guaranteedForwarding.size} reports in queue" }
            cond.signal()
        }
        if (Thread.currentThread() != pacerThread)
            pacerThread.join()
    }

    internal companion object {
        private val logger by lazy { System.getLogger(ProgressPacer::class.java.name) }
        /**
         * Returns true if the other progress object differs only in the amount of work done, or if completion status has changed,
         * recursively.
         */
        internal fun ProgressReport.structuralComparison(other: ProgressReport): Boolean =
            other.message == message && other.expectedTotal == expectedTotal && other.units == units && other.complete == complete
                && subReports.zip(other.subReports).all { (a: ProgressReport?, b: ProgressReport?) ->
                    when {
                        a != null && b != null -> a.structuralComparison(b)
                        a == null && b == null -> true
                        else -> false
                    }
                }
    }
}
