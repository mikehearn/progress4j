package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import java.util.concurrent.atomic.AtomicReference

/**
 * Writes progress out as plain lines of text, for example when stdout is redirected to a file.
 * Processes [ProgressReport] objects as structured by [ThreeLevelProgressEmitter]: One main report, with a number of indeterminate sub-reports
 * representing parallel tracks, each with a single sub-report with the progress for the current sub-task being executed in that track.
 * The [PlainParallelTaskTracker] will keep track of the main report and sub-tasks, reporting them to the printer as they change.
 */
class PlainParallelTaskTracker(private val printer: ProgressPrinter = ProgressPrinter()) : ProgressReport.Tracker {
    private val lastProgress = AtomicReference<ProgressReport?>()

    override fun report(progress: ProgressReport) {
        val last = lastProgress.getAndSet(progress.immutableReport())
        if (last == progress)
            return

        // If the main report changed, report it.
        val main = progress.immutableReport().withSubReports(emptyList())
        if (main != last?.immutableReport()?.withSubReports(emptyList())) {
            if (main.complete || main.completed == 0L) {
                printer.report(main)
            }
        }

        // Report any changes to subtasks.
        for ((index, report) in progress.subReports.withIndex()) {
            // We only report the third level of subtasks. The second level only provides an overarching message for the sequence of
            // subtasks under it.
            val subtask = report?.subReports?.firstOrNull() ?: continue
            val lastSubtask = last?.subReports?.getOrNull(index)?.subReports?.firstOrNull()
            if (lastSubtask == subtask) {
                continue
            }
            if (subtask.completed == 0L || subtask.complete) {
                var message = "${index}. ${report.message.orEmpty()}"
                if (subtask.message != null) {
                    message = "$message: ${subtask.message}"
                }

                printer.report(subtask.immutableReport().withMessage(message))
            }
        }
    }
}
