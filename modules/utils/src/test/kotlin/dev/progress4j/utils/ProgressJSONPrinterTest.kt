package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import dev.progress4j.utils.ProgressJSONWriter.Companion.toJSON
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressJSONWriterTest {
    @Test
    fun golden() {
        verify(ProgressReport.createIndeterminate(), """{ "type": "progress", "expectedTotal": 1, "completed": 0 }""")
        verify(ProgressReport.createIndeterminate("Simple message"), """{ "type": "progress", "expectedTotal": 1, "completed": 0, "message": "Simple message" }""")
        verify(
            ProgressReport.createIndeterminate("Message \"with \" quotes"),
            """{ "type": "progress", "expectedTotal": 1, "completed": 0, "message": "Message \"with \" quotes" }"""
        )
        verify(
            ProgressReport.createIndeterminate(
                """Message
            with newline
        """.trimIndent()
            ), """{ "type": "progress", "expectedTotal": 1, "completed": 0, "message": "Message\n            with newline" }"""
        )
        verify(
            ProgressReport.createIndeterminate("Message with \\ backslashes"),
            """{ "type": "progress", "expectedTotal": 1, "completed": 0, "message": "Message with \\ backslashes" }"""
        )
        verify(
            ProgressReport.create("Message", 100, 50, ProgressReport.Units.BYTES),
            """{ "type": "progress", "expectedTotal": 100, "completed": 50, "units": "BYTES", "message": "Message" }"""
        )
    }

    @org.junit.jupiter.api.Test
    fun subReports() {
        val st1 = ProgressReport.createIndeterminate("ST1")
        val st2 = ProgressReport.create("ST2", 3, 1)
        val top = ProgressReport.create("Über-task", 5, 0).withSubReports(listOf(st1, st2))
        verify(top, """
            { "type": "progress", "expectedTotal": 5, "completed": 0, "message": "Über-task", "subReports": [ { "expectedTotal": 1, "completed": 0, "message": "ST1" }, { "expectedTotal": 3, "completed": 1, "message": "ST2" } ] }
        """.trimIndent())
    }

    private fun verify(progress: ProgressReport, expected: String) = assertEquals(expected, convert(progress))
    private fun convert(progress: ProgressReport): String = StringBuilder().also { progress.toJSON(it) }.toString()
}
