package dev.progress4j.utils

import dev.progress4j.api.MutableProgressReport
import dev.progress4j.api.ProgressReport
import java.util.concurrent.atomic.AtomicReference

/**
 * Fixes or drops problematic progress reports e.g. where no actual work is expected.
 *
 * @property callback Destination of the fixed up reports.
 */
class ProgressFixup(private val callback: ProgressReport.Tracker) : ProgressReport.Tracker {
    private val lastReport = AtomicReference<ProgressReport?>()

    override fun report(progress: ProgressReport) {
        // Suppress accidental duplicates.
        if (lastReport.getAndSet(progress) == progress && progress !is MutableProgressReport)
            return

        // Drop reports where no actual work is expected.
        if (progress.expectedTotal <= 0)
            return

        // Clamp completed and expected totals.
        if (progress.completed > progress.expectedTotal) {
            val report = progress.immutableReport().withExpectedTotal(progress.completed)
            lastReport.set(report)
            callback.report(report)
            return
        }

        callback.report(progress)
    }
}
