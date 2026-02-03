package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import java.io.PrintStream
import kotlin.math.roundToInt

/**
 * Emits OSC 9;4 progress bar escape codes for terminals that support them.
 *
 * Format: ESC ] 9 ; 4 ; <state> ; <progress> BEL
 * Example for 15%: "\u001B]9;4;1;15\u0007"
 *
 * @param output The print stream to write escape codes to, defaults to [System.out].
 */
class OscProgressBarTracker @JvmOverloads constructor(
    private val output: PrintStream = System.out
) : ProgressReport.Tracker {
    private var lastPercent: Int? = null
    private var lastState: Int? = null

    @Synchronized
    override fun report(progress: ProgressReport) {
        if (progress.indeterminate) {
            if (progress.complete) {
                emitState(State.HIDDEN, 0)
            } else {
                emitState(State.INDETERMINATE, 0)
            }
            return
        }

        if (progress.complete) {
            emitState(State.HIDDEN, 0)
            return
        }

        val percent = if (progress.expectedTotal > 0) {
            ((progress.completed.toDouble() / progress.expectedTotal.toDouble()) * 100.0)
                .coerceIn(0.0, 100.0)
                .roundToInt()
        } else {
            0
        }

        if (percent == lastPercent && lastState == State.DEFAULT && !progress.complete) {
            return
        }

        emitState(State.DEFAULT, percent)
    }

    private fun emitState(state: Int, percent: Int) {
        if (state == lastState && percent == lastPercent) {
            return
        }
        lastState = state
        lastPercent = percent
        output.print("\u001B]9;4;$state;$percent\u0007")
        output.flush()
    }

    private object State {
        const val HIDDEN = 0
        const val DEFAULT = 1
        const val ERROR = 2
        const val INDETERMINATE = 3
        const val WARNING = 4
    }
}
