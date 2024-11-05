package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProgressStreamCombinerTest {
    @Test
    fun basic() {
        var result: ProgressReport? = null
        val collector = ProgressReport.Tracker { result = it }
        val combiner = ProgressStreamCombiner(false, collector)

        // Constructors don't set anything.
        assertEquals(null, result)

        // Primary report is passed through.
        val base = ProgressReport.createIndeterminate("Complex thing")
        combiner.report(base)
        assertEquals(base, result)

        // Merely adding sub-tasks doesn't emit anything.
        val st1 = combiner.addSubTask()
        val st2 = combiner.addSubTask()
        assertEquals(base, result)

        // This will emit a report with a first sub-task.
        val em1 = ProgressEmitter(ProgressReport.create("ST1", 3), st1)
        assertEquals("ST1", result!!.subReports.single()!!.message)

        // The second ....
        ProgressEmitter(ProgressReport.create("ST2", 5), st2)
        assertEquals(2, result!!.subReports.size)
        assertEquals("ST1", result!!.subReports[0]!!.message)
        assertEquals("ST2", result!!.subReports[1]!!.message)
        assertEquals(3, result!!.subReports[0]!!.expectedTotal)
        assertEquals(5, result!!.subReports[1]!!.expectedTotal)
        assertEquals(0, result!!.subReports[0]!!.completed)
        assertEquals(0, result!!.subReports[1]!!.completed)

        // Will re-emit with the second un-changed.
        em1.increment()
        assertEquals(2, result!!.subReports.size)
        assertEquals("ST1", result!!.subReports[0]!!.message)
        assertEquals("ST2", result!!.subReports[1]!!.message)
        assertEquals(3, result!!.subReports[0]!!.expectedTotal)
        assertEquals(5, result!!.subReports[1]!!.expectedTotal)
        assertEquals(1, result!!.subReports[0]!!.completed)
        assertEquals(0, result!!.subReports[1]!!.completed)
    }

    @Test
    fun summation() {
        var result: ProgressReport? = null
        val collector = ProgressReport.Tracker { result = it }
        val combiner = ProgressStreamCombiner(true, collector)

        val st1 = ProgressEmitter(ProgressReport.create("ST1", 3, 0, ProgressReport.Units.BYTES), combiner.addSubTask())
        assertEquals(3, result!!.expectedTotal)
        val st2 = ProgressEmitter(ProgressReport.create("ST2", 5, 0, ProgressReport.Units.BYTES), combiner.addSubTask())
        assertEquals(8, result!!.expectedTotal)
        st1.increment()
        assertEquals(8, result!!.expectedTotal)
        assertEquals(1, result!!.completed)
        st2.increment()
        assertEquals(2, result!!.completed)
        assertEquals(ProgressReport.Units.BYTES, result!!.units)
        // Complete the first progress, check that the combiner still counts it.
        st1.increment(2)
        assertEquals(4, result!!.completed)
        st2.increment(1)
        assertEquals(5, result!!.completed)
    }
}
