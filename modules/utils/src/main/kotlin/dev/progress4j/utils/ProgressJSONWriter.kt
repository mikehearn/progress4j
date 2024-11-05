package dev.progress4j.utils


import dev.progress4j.api.ProgressReport
import dev.progress4j.utils.impl.asJSON
import java.io.Writer

/**
 * A [ProgressReport.Tracker] that writes JSON lines encoding the progress report to the given [Writer].
 *
 * The schema of the emitted JSON is:
 *
 * `{ "type": "progress", "expectedTotal": 100, "completed": 1, "message": "str", "progressUnits": "BYTES", "operation": "123abc" }`
 *
 * - `message`: Optional.
 * - `units`: Optional. If missing units are [ProgressReport.Units.ABSTRACT_CONSISTENT].
 * - `operation`: Optional. If missing there is no associated operation object, if present, it's an arbitrary hex encoded number identifying
 *   the operation object. The operation itself is not serialized in any way.
 */
class ProgressJSONWriter(private val output: Writer) : ProgressReport.Tracker {
    // Re-use the same builder over and over to improve performance.
    private val builder = StringBuilder()

    @Synchronized
    override fun report(progress: ProgressReport) {
        builder.clear()
        progress.toJSON(builder)
        builder.appendLine()
        output.append(builder)
        output.flush()
    }

    companion object {
        /**
         * Renders the progress report to a single line of JSON.
         *
         * @see ProgressJSONWriter for the format.
         */
        val ProgressReport.json: String get() = StringBuilder().also { toJSON(it) }.toString()

        /**
         * Renders the progress report to a single line of JSON.
         *
         * @see ProgressJSONWriter for the format.
         */
        @JvmStatic
        fun ProgressReport.toJSON(builder: StringBuilder, base: Map<String, Any> = mutableMapOf("type" to "progress")) {
            builder.append(populateMapFromReport(LinkedHashMap(base)).asJSON)
        }

        private fun ProgressReport.populateMapFromReport(map: MutableMap<String, Any?>): Map<String, Any?> {
            map["expectedTotal"] = expectedTotal
            map["completed"] = completed
            if (units != ProgressReport.Units.ABSTRACT_CONSISTENT)
                map["units"] = units.name
            map["message"] = message
            if (subReports.isNotEmpty())
                map["subReports"] = subReports.map { it?.populateMapFromReport(HashMap()) }
            return map
        }
    }
}
