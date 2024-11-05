package dev.progress4j.utils

import dev.progress4j.api.ProgressReport

/**
 * Runs the given block with an indeterminate report at the start and a complete at the end (even for exceptional endings). If the callback
 * is null the block is still run, but no progress events are emitted.
 *
 * @param message The message to use for the report.
 */
inline fun <T> ProgressReport.Tracker?.indeterminate(message: String, block: () -> T): T {
    try {
        this?.report(ProgressReport.createIndeterminate(message))
        return block()
    } finally {
        this?.report(ProgressReport.create(message, 1, 1))
    }
}

/** A progress consumer that simply ignores all reports. */
object IgnoreProgress : ProgressReport.Tracker {
    override fun report(progress: ProgressReport): Unit = Unit
}
