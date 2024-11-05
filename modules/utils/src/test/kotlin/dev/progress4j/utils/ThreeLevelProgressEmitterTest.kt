package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals

class ThreeLevelProgressEmitterTest {
    class ProgressCollector : ProgressReport.Tracker {
        private val theReports = mutableListOf<ProgressReport>()
        val reports: List<ProgressReport> = theReports
        override fun report(progress: ProgressReport) {
            theReports.add(progress)
        }
    }

    @Test
    fun `simple test with subtask`() {
        val collector = ProgressCollector()
        ThreeLevelProgressEmitter(collector, ProgressReport.create("Test", 5), 3).use { tracker ->
            tracker.startTask(0, "task 0").use {
                val report = ProgressReport.create("subtask", 2)
                it.report(report)
                it.report(report.withCompleted(2))
            }
        }
        assertContentEquals(
            listOf(
                // Initial report.
                ProgressReport.create("Test", 5).withSubReports(listOf(null, null, null)),
                // Task 0 starts.
                ProgressReport.create("Test", 5)
                    .withSubReports(listOf(ProgressReport.createIndeterminate("task 0"), null, null)),
                // Subtask starts.
                ProgressReport.create(
                    "Test",
                    5
                ).withSubReports(
                    listOf(
                        ProgressReport.createIndeterminate("task 0")
                            .withSubReports(listOf(ProgressReport.create("subtask", 2))), null, null
                    )
                ),
                // Subtask completes.
                ProgressReport.create(
                    "Test",
                    5
                ).withSubReports(
                    listOf(
                        ProgressReport.createIndeterminate("task 0")
                            .withSubReports(listOf(ProgressReport.create("subtask", 2, 2))), null, null
                    )
                ),
                // Task 0 closes.
                ProgressReport.create("Test", 5, 1).withSubReports(listOf(null, null, null)),
                // Tracker closes.
                ProgressReport.create("Test", 5, 5).withSubReports(listOf(null, null, null)),
            ), collector.reports
        )
    }

    @Test
    fun `number of tracks can expand`() {
        val collector = ProgressCollector()
        ThreeLevelProgressEmitter(collector, ProgressReport.create("Test", 5), 2).use { tracker ->
            tracker.startTask(3, "task 0").use {
                val report = ProgressReport.create("subtask", 2)
                it.report(report)
                it.report(report.withCompleted(2))
            }
        }
        assertContentEquals(
            listOf(
                // Initial report with 2 tracks.
                ProgressReport.create("Test", 5).withSubReports(listOf(null, null)),
                // Task 0 starts in track 3, expanding number of tracks.
                ProgressReport.create("Test", 5)
                    .withSubReports(listOf(null, null, null, ProgressReport.createIndeterminate("task 0"))),
                // Subtask starts.
                ProgressReport.create(
                    "Test",
                    5,
                ).withSubReports(
                    listOf(
                        null, null, null, ProgressReport.createIndeterminate("task 0")
                            .withSubReports(listOf(ProgressReport.create("subtask", 2)))
                    )
                ),
                // Subtask completes.
                ProgressReport.create(
                    "Test",
                    5
                ).withSubReports(
                    listOf(
                        null, null, null, ProgressReport.createIndeterminate("task 0")
                            .withSubReports(listOf(ProgressReport.create("subtask", 2, 2)))
                    )
                ),
                // Task 0 closes.
                ProgressReport.create("Test", 5, 1).withSubReports(listOf(null, null, null, null)),
                // Tracker closes.
                ProgressReport.create("Test", 5, 5).withSubReports(listOf(null, null, null, null)),
            ), collector.reports
        )
    }
}
