package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import dev.progress4j.utils.ThreeLevelProgressEmitter.Task
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max

/**
 * Builds [ProgressReport] objects that model an operation made up of several parallel sub-operations, each of which may itself have multiple
 * steps. Such reports are often found in build systems that can execute multiple tasks in parallel, and where each task may need to do
 * multiple sequential operations.
 *
 * It produces progress reports that contain 3 levels: one main report, a number of tasks that are sub-reports of the main report,
 * and a single sub-report for each task. This is meant for visually organizing a complex task with parallel tasks in a user-friendly manner:
 * the main report represents the overall work to be done and each task is a separate workflow that contributes to that main work through
 * a sequence of subtasks.
 *
 * Each task is associated to a track, usually representing a fixed resource like a worker thread. This is so that all work for that task is
 * reported in the specific sub-report of that track, keeping visual stability for the progress of that task. Once a task is done, the track
 * goes into an idle state, which is represented by a null sub-report at the specific index for that track. Visually, this gives a clean
 * representation of what each thread is doing at the moment. Also, the completed work for the main-report is incremented.
 *
 * To use, simply create a [ThreeLevelProgressEmitter] with the message for the main report, the expectedTotal set to the number of tasks
 * you expect to need for completion of the work, and expectedTracks to the number of separate tracks (threads) where tasks will execute.
 * Then, call [startTask] for each work task selecting the track on which it will run and a message for that task, and report progress of
 * that task to the [Task] object returned. Once done with that task, [close] the [Task] object (or better yet, use [AutoCloseable.use]).
 * Once done with the overall work, [close] the [ThreeLevelProgressEmitter] (or once again use [AutoCloseable.use]).
 */
class ThreeLevelProgressEmitter(
    private val tracker: ProgressReport.Tracker,
    baseReport: ProgressReport,
    expectedTracks: Int = 1
) : AutoCloseable {
    private val lock = ReentrantLock()

    private var lastReport: ProgressReport = baseReport.immutableReport().withSubReports(List(expectedTracks) { null })
        get() = lock.withLock { field }
        set(value) = lock.withLock { field = value }

    init {
        tracker.report(lastReport)
    }

    val completedTasks: Long
        get() {
            return lock.withLock { lastReport.completed }
        }

    fun startTask(index: Int, message: String?): Task {
        return SubTask(index, message)
    }

    override fun close() {
        lock.withLock {
            if (!lastReport.complete) {
                lastReport = lastReport.withCompleted(lastReport.expectedTotal)
            }
            // Accept while holding the lock to prevent reordering of reports.
            tracker.report(lastReport)
        }
    }

    /**
     * A "track" in the multi-progress report.
     */
    sealed interface Task : ProgressReport.Tracker, AutoCloseable

    private inner class SubTask(val index: Int, message: String?) : Task {
        var subTaskReport: ProgressReport? = ProgressReport.createIndeterminate(message)
            get() = lock.withLock { field }
            set(value) = lock.withLock { field = value }

        init {
            emit()
        }

        private fun emit() {
            lock.withLock {
                if (index >= lastReport.subReports.size) {
                    // Expand list of sub-reports to accommodate this sub-task's index.
                    lastReport = lastReport.withSubReports(lastReport.subReports + List(index - lastReport.subReports.size + 1) { null })
                }
                lastReport = lastReport.withSubReport(index, subTaskReport)
                // Accept while holding the lock to prevent reordering of reports.
                tracker.report(lastReport)
            }
        }

        override fun report(progress: ProgressReport) {
            lock.withLock {
                subTaskReport = subTaskReport?.withSubReports(listOf(progress))
                emit()
            }
        }

        private var closed = false

        override fun close() {
            lock.withLock {
                if (closed)
                    return
                closed = true

                val completed = lastReport.completed + 1
                val expectedTotal = max(lastReport.expectedTotal, completed)
                lastReport = lastReport.withExpectedTotal(expectedTotal).withCompleted(completed)
                subTaskReport = null
                emit()
            }
        }
    }
}
