package dev.progress4j.utils


import dev.progress4j.api.ProgressReport
import dev.progress4j.utils.impl.asJSON
import java.io.Writer

/**
 * A [ProgressReport.Tracker] that writes JSON lines encoding the progress report to the given [Writer].
 *
 * The schema of the emitted JSON follows this structure:
 * ```json
 * {
 *   "type": "progress",           // Always "progress"
 *   "expectedTotal": 100,         // Required, >= 1
 *   "completed": 1,              // Required, >= 0
 *   "message": "Processing...",   // Optional string
 *   "units": "BYTES",            // Optional, one of the Units enum values
 *   "subReports": [              // Optional array of nested progress reports
 *     {
 *       "type": "progress",
 *       ...
 *     }
 *   ]
 * }
 * ```
 * 
 * See `progress-schema.json` in the docs for the full JSON Schema definition.
 * 
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
