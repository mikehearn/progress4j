package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import java.lang.Long.min
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Combines multiple streams of progress reports into a single report with sub-reports and then sends it onwards to the [callback].
 *
 * A combiner vends sub-task objects: progress consumers that store the received report into the sub-reports list of the primary report,
 * optionally re-calculate the primary report's progress based on the sub-reports, and then sends the combined report onwards. This can be
 * used to model hierarchical progress. If sub-tasks receive progress reports before the [report] method is called, then the default primary
 * report is used (an indeterminate message-less report).
 *
 * Combiners are thread safe.
 *
 * @param sum If true then the [ProgressReport.getCompleted], [ProgressReport.getExpectedTotal] and [ProgressReport.getUnits] of the combined report will be
 * set from the sums of the sub-reports, i.e. the primary report will just track how far the sub-tasks are. If all the sub-tasks share
 * the same unit, that unit is used, otherwise [ProgressReport.Units.ABSTRACT_CONSISTENT] is used.
 * @param callback The progress consumer that will be invoked any time a new primary report is [report]-ed, or any of the sub-tasks
 */
class ProgressStreamCombiner(private val sum: Boolean, private val callback: ProgressReport.Tracker) : ProgressReport.Tracker {
    private val lock = ReentrantLock()
    private var primaryReport: ProgressReport = ProgressReport.createIndeterminate()
    private val subReports = ArrayList<ProgressReport?>()

    override fun report(progress: ProgressReport) {
        lock.withLock {
            primaryReport = progress.immutableReport()
            emit()
        }
    }

    private fun emit() {
        check(lock.isLocked)
        var report = primaryReport.withSubReports(subReports)

        if (sum && subReports.isNotEmpty()) {
            // Look at all the non-null sub-reports, sum them and figure out if they're in compatible non-abstract units.
            var units: ProgressReport.Units? = null
            for (r in subReports) {
                if (r == null) {
                    continue
                } else if (units == null) {
                    units = r.units
                } else if (r.units != units) {
                    units = ProgressReport.Units.ABSTRACT_CONSISTENT
                    break
                }
            }
            // If units is null it means every sub-report was null.
            if (units != null) {
                val expected = subReports.sumOf { it?.expectedTotal ?: 0 }
                val completed = min(subReports.sumOf { it?.completed ?: 0 }, expected)
                report = ProgressReport.create(
                    primaryReport.message,
                    expected,
                    completed,
                    units,
                    subReports
                )
            }
        }

        callback.report(report)
    }

    private inner class SubTask : ProgressReport.Tracker {
        private var index = -1

        override fun report(progress: ProgressReport) {
            lock.withLock {
                if (index == -1) {
                    index = subReports.size
                    subReports.add(progress)
                } else {
                    subReports[index] = progress
                }
                emit()
            }
        }
    }

    /**
     * Returns a progress consumer that will set the received reports as sub-reports of the last report passed to [report] and then pass it
     * on to the callback passed to the constructor.
     */
    fun addSubTask(): ProgressReport.Tracker = SubTask()
}
