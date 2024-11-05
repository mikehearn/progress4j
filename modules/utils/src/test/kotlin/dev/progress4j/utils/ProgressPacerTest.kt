package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import dev.progress4j.utils.ProgressPacer.Companion.structuralComparison
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressPacerTest {
    @Test
    fun structurallyEqualTest() {
        fun make(c: Int, msg: String) = ProgressReport.create(
            "Top",
            5,
            3,
            ProgressReport.Units.ABSTRACT_CONSISTENT,
            listOf(
                ProgressReport.create("Subtask 1", 10, c),
                ProgressReport.create(msg, 10, c)
            )
        )

        val p1 = make(5, "Subtask 2")
        val p2 = make(6, "Subtask 2")
        assertTrue(p1.structuralComparison(p1))
        assertTrue(p1.structuralComparison(p2))
        assertFalse(p1.structuralComparison(make(5, "Foo")))
        assertFalse(p1.structuralComparison(make(10, "Subtask 2")))
    }
}
