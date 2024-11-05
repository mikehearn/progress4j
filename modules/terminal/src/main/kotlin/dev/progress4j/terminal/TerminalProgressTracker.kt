package dev.progress4j.terminal

import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.mordant.animation.Animation
import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.grid
import com.github.ajalt.mordant.terminal.PrintRequest
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.ajalt.mordant.widgets.EmptyWidget
import com.github.ajalt.mordant.widgets.ProgressBar
import com.github.ajalt.mordant.widgets.Spinner
import com.github.ajalt.mordant.widgets.Text
import com.github.ajalt.mordant.widgets.progress.*
import dev.progress4j.api.ProgressReport
import dev.progress4j.utils.ProgressPacer
import dev.progress4j.utils.ProgressStatsAccumulator
import dev.progress4j.utils.ProgressTrackerInvoker
import dev.progress4j.utils.ProgressTrackerResetter
import java.io.PrintStream
import kotlin.concurrent.thread
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.min
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.toJavaDuration

/**
 * A fancy unicode+colors based terminal progress tracker that handles hierarchical progress reports.
 *
 * The tracker will show a unicode spinner with the primary report message and progress bar, along with a timer that tracks how long the
 * tracker has been started for, displayed on the right-hand side. The tracker starts automatically when the first report arrives, and
 * should be closed when you're done with it.
 *
 * Any sub-reports of the received report will be mapped to additional bars, which are added as needed to match the size of the sub-reports
 * list. A sub-report of null is rendered as 'idle' which allows you to avoid annoying visual instability as tasks come and go.
 * If a sub-report itself has sub-reports, the bar will render a combination of the bolded message of the sub-report with the rest of the
 * bar representing the progress of the first of its sub-reports.
 *
 * That is, progress with a structure like this:
 *
 * ```
 *   Main report
 *     |- Subtask
 *         |- Inner task
 * ```
 *
 * Will be rendered something like this:
 *
 * ```
 * **Main report**  ===(overall progress)===
 * > **Subtask**  Inner task ===(progress of Inner task)===
 * ```
 *
 * A good way to create reports with the expected structure is by using a [dev.progress4j.utils.ThreeLevelProgressEmitter].
 *
 * Note that all other terminal output should be routed via [Terminal] whilst this tracker is in operation, and there should only be one
 * tracker for any terminal. If you use the app support framework this is done for you.
 *
 * A tracker can only be used once. After the top level report reaches the completed state it calls [close] automatically and disappears.
 *
 * @param terminal A Mordant Terminal object that will be used to render the bars.
 */
@OptIn(ExperimentalTime::class)
class TerminalProgressTracker(val terminal: Terminal) : ProgressReport.Tracker, AutoCloseable {
    private val startTime = TimeSource.Monotonic.markNow()
    private val shutdownHook = thread(name = "Terminal progress tracker shutdown hook", start = false) {
        close(dueToJVMShutdown = true)
    }
    private val elapsedDuration get() = startTime.elapsedNow().toJavaDuration()

    private val elapsedHumanized: String
        get() {
            val seconds = elapsedDuration.toSeconds()
            return if (seconds >= 60)
                String.format("%d:%02d:%02d", (seconds / 3600), (seconds / 60) % 60, seconds % 60)
            else
                String.format("%.1fs", elapsedDuration.toMillis() / 1000.0)
        }

    private class Bar(private val main: Boolean) {
        class Accumulator {
            private val stats = ProgressStatsAccumulator()
            val averageProgress: Double get() = stats.averageProgress
            var report: ProgressReport? = null
                private set

            fun update(progress: ProgressReport?) {
                report = progress?.takeUnless { it.complete }
                if (progress != null) {
                    stats.report(progress)
                }
            }
        }

        val mainReport = Accumulator()
        val subReport = Accumulator()
        fun update(progress: ProgressReport?) {
            mainReport.update(progress)
            if (!main) {
                subReport.update(progress?.subReports?.firstOrNull())
            }
        }
    }

    private val state = Locker(object {
        var running = true
        var started = false
        val bars = ArrayList<Bar>()
    })

    init {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

    private inner class TrackerAnimation : Animation<Unit>(terminal) {
        private val spinner = Spinner.Dots(TextColors.green)
        private var currentPrimaryProgress = -1L
        private var frame = 0L

        override fun renderData(data: Unit): Widget {
            return state.locked {
                frame++

                if (bars.isEmpty())
                    return@locked EmptyWidget

                grid {
                    column(0) { width = ColumnWidth.Expand() }

                    for ((index, bar) in bars.withIndex()) {
                        if (index == 0)
                            row(topRow(bar))
                        else
                            row(subRow(bar))
                    }
                }
            }
        }

        private fun topRow(bar: Bar) = grid {
            row {
                if (frame.mod(3) == 0)
                    spinner.advanceTick()
                cell(spinner)

                val report = bar.mainReport.report!!   // Primary bar always has a report.
                cell(TextStyles.bold(report.message ?: "Working"))
                if (!report.indeterminate) {
                    val period = 4 // this could be configurable
                    val pulsePosition = (((elapsedDuration.toMillis() / 1000.0) % period) / period)
                    val newProgress = ((report.completed.toDouble() / report.expectedTotal.toDouble()) * 100).toLong()
                    // Smoothly move towards the current progress level when we do some work
                    if (currentPrimaryProgress < newProgress) {
                        currentPrimaryProgress = if (currentPrimaryProgress < 0) {
                            min(1, newProgress)
                        } else {
                            min(newProgress, currentPrimaryProgress + ANIMATED_STEP_SIZE)
                        }
                    }

                    cell(
                        ProgressBar(
                            completed = currentPrimaryProgress,
                            total = 100,
                            completeStyle = TextStyle(color = RGB("#98c379")),
                            pendingStyle = TextColors.gray,
                            pulsePosition = pulsePosition.toFloat()
                        )
                    )
                } else {
                    cell(EmptyWidget)
                }

                cell(Text(TextStyles.dim(elapsedHumanized.padStart(5))))
            }
            column(2) { width = ColumnWidth.Expand() }
        }

        private fun subRow(bar: Bar): Widget {
            val (accumulator, label) = run {
                val mainMessage = bar.mainReport.report?.message.orEmpty()
                val subMessage = bar.subReport.report?.message.orEmpty()
                if (bar.subReport.report == null) {
                    bar.mainReport to TextStyles.bold(mainMessage)
                } else if (mainMessage != "") {
                    bar.subReport to TextStyles.bold(mainMessage) + (if (subMessage != "") "  $subMessage" else "")
                } else {
                    bar.subReport to TextStyles.bold(subMessage)
                }
            }
            val report = accumulator.report
            return grid {
                row {
                    val indicator = "❯"
                    // There must be exactly two cells emitted here.
                    if (report != null) {
                        cell(TextColors.brightBlue(indicator))
                        if (report.indeterminate)
                            cell(label)
                        else
                            cell(
                                progressBarLayout {
                                    text(label)
                                    progressBar(
                                        finishedStyle = Theme.Default.styles["progressbar.complete"],
                                        pendingStyle = TextColors.gray
                                    )
                                    if (report.units == ProgressReport.Units.BYTES)
                                        speed("B/s", style = TextStyles.dim.style)

                                    // These lines would enable Mordant's ETA calculation. They're disabled because:
                                    // - Takes up too much horizontal space.
                                    // - User probably only cares about ETA for the whole operation, which we can't know here.
                                    //
                                    // if (report.units == Progress.Units.BYTES)
                                    //    timeRemaining(style = TextStyles.dim.style)
                                }.build(
                                    report.expectedTotal,
                                    report.completed,
                                    TimeSource.Monotonic.markNow(),
                                    ProgressState.Status.Running(startTime),
                                    accumulator.averageProgress
                                )
                            )
                    } else {
                        // Completed or non-started sub-tasks are assumed to now be idle.
                        cell(Text(TextStyles.dim(indicator)))
                        cell(Text(TextStyles.dim("IDLE")))
                    }
                }
                column(1) { width = ColumnWidth.Expand() }
            }
        }
    }

    override fun report(progress: ProgressReport) {
        val running = state.locked {
            if (!started) {
                started = true
                terminal.cursor.hide(false)   // We'll show on exit ourselves.
            }
            running
        }

        if (progress.complete)
            close()
        else if (running)
            pacer.report(progress)
    }

    // Thins the stream of updates and ensures they get passed to animation.update on a single thread and at a constant rate.
    private val pacer = ProgressPacer(PacedTracker(), 30.0f, true)
    private var animation = TrackerAnimation()

    private inner class PacedTracker : ProgressReport.Tracker {
        override fun report(progress: ProgressReport) {
            // Now on the pacer thread. We'll get called here repeatedly so can drive the animation. If we take an exception the pacer
            // will log it and stop calling us.
            state.locked {
                val barCount = 1 + progress.subReports.size
                val changedSize = bars.size != barCount
                if (bars.isEmpty()) {
                    bars.add(Bar(true))
                }

                while (bars.size < barCount)
                    bars.add(Bar(false))

                for (i in 0 until barCount) {
                    val r = if (i == 0) progress else progress.subReports[i - 1]
                    bars[i].update(r)
                }

                // Clear up remaining bars if the progress has fewer sub-reports now.
                for (i in barCount until bars.size) {
                    bars[i].update(null)
                }

                while (bars.size > barCount)
                    bars.removeLast()

                if (changedSize) {
                    // If the number of bars changes, we have to reset the animation to ensure the height calculation and line clearing
                    // sequences are emitted correctly.
                    animation.clear()
                    animation = TrackerAnimation()
                }

                animation.update(Unit)
            }
        }
    }

    fun stopAnimation() {
        animation.clear()
        terminal.cursor.show()
    }

    fun resumeAnimation() {
        animation = TrackerAnimation()
        terminal.cursor.hide(false)
    }

    override fun close() {
        close(false)
    }

    private fun close(dueToJVMShutdown: Boolean) {
        state.locked {
            if (!running) {
                return
            }
            running = false
            if (!dueToJVMShutdown) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook)
                } catch (e: IllegalStateException) {
                    // Can happen if we are closed during shutdown.
                }
            }
        }
        pacer.close()    // Will block waiting for shutdown.
        //Thread.sleep(2000)
        animation.clear()
        if (dueToJVMShutdown) {
            // Ctrl-C causes the terminal to print a newline, so we have to undo that.
            terminal.cursor.move {
                up(1)
                clearLine()
            }
        }
        terminal.cursor.show()
    }

    companion object {
        /**
         * How fast to step the progress tracker when it's "catching up" to a sudden big change.
         */
        const val ANIMATED_STEP_SIZE = 5

        private var globalTracker: TerminalProgressTracker? = null

        /**
         * Returns a progress tracker that will show an animated [TerminalProgressTracker] whilst redirecting [System.out] so that it prints
         * above the animated bar. The returned tracker is thread safe.
         */
        @JvmStatic
        @Synchronized
        fun get(): ProgressReport.Tracker {
            if (globalTracker != null)
                return globalTracker!!

            // Set up Mordant. We need to customize the interface to break the loop that would otherwise occur when we override stdout.
            // And we need to create Terminal twice, because STANDARD_TERMINAL_INTERFACE is internal.
            val terminal = Terminal(terminalInterface = RedirectingTerminalInterface(System.out, System.err, Terminal().terminalInterface))

            // Ensure printing to stdout still works and pushes the messages _above_ the animation.
            System.setOut(PrintStreamWrapper(terminal, System.out))

            return ProgressTrackerInvoker.wrap(
                ProgressTrackerResetter {
                    globalTracker = TerminalProgressTracker(terminal)
                    globalTracker!!
                }
            )
        }
    }

    /**
     * Designed to wrap [System.out] to enable printing of strings to interact properly with the terminal animation.
     */
    private class PrintStreamWrapper(private val terminal: Terminal, target: PrintStream) : PrintStream(target) {
        override fun print(obj: Any?) {
            // TODO: Accept progress report objects.
            print(obj?.toString())
        }

        override fun print(s: String?) {
            // Work around a bug in Mordant.
            if (s != null && s.endsWith('\n'))
                terminal.println(s)
            else
                terminal.print(s)
        }

        override fun print(b: Boolean) {
            terminal.print(b)
        }

        override fun print(c: Char) {
            terminal.print(c)
        }

        override fun print(i: Int) {
            terminal.print(i)
        }

        override fun print(l: Long) {
            terminal.print(l)
        }

        override fun print(f: Float) {
            terminal.print(f)
        }

        override fun print(d: Double) {
            terminal.print(d)
        }

        override fun print(s: CharArray) {
            terminal.print(s)
        }

        override fun println() {
            terminal.println()
        }

        override fun println(x: Boolean) {
            terminal.println(x)
        }

        override fun println(x: Char) {
            terminal.println(x)
        }

        override fun println(x: Int) {
            terminal.println(x)
        }

        override fun println(x: Long) {
            terminal.println(x)
        }

        override fun println(x: Float) {
            terminal.println(x)
        }

        override fun println(x: Double) {
            terminal.println(x)
        }

        override fun println(x: CharArray) {
            terminal.println(x)
        }

        override fun println(x: String?) {
            terminal.println(x)
        }
    }

    private class RedirectingTerminalInterface(
        private val stdOut: PrintStream,
        private val stdErr: PrintStream,
        private val delegate: TerminalInterface
    ) : TerminalInterface by delegate {
        override fun completePrintRequest(request: PrintRequest) {
            val target = if (request.stderr) stdErr else stdOut
            if (request.trailingLinebreak) {
                if (request.text.isEmpty()) {
                    target.println()
                } else {
                    target.println(request.text)
                }
            } else {
                target.print(request.text)
            }
        }

        override fun readLineOrNull(hideInput: Boolean): String? {
            if (hideInput) {
                val console = System.console()
                if (console != null) {
                    // Workaround to a bug in macOS Terminal: if we don't send anything in the prompt to readPassword, the little "key" glyph
                    // that indicates the input isn't going to be echoed doesn't display consistently. So we send the ANSI "reset" escape, which
                    // doesn't really do anything.
                    //
                    // TODO(low): Is this still required after the upgrade to Mordant 2.6?
                    return console.readPassword("\u001B[m")?.concatToString()
                }
            }
            return readlnOrNull()
        }
    }

    // Copied from utils package
    /**
     * A wrapper class that makes it harder to forget to take a lock before accessing some shared state.
     *
     * Simply define an anonymous object to hold the data that must be grouped under the same lock, and then pass it
     * to the constructor. You can now use the [locked] method with a lambda to take the object lock in a
     * way that ensures it'll be released if there's an exception. Kotlin's scoping rules will ensure you can only
     * access the fields by using either [locked] or `__unlocked`, thus making it clear at each use-site which it is.
     * You should generally not use `__unlocked`, it is public only because [locked] is an inlined function.
     *
     * This technique is not infallible: if you capture a reference to the fields in another lambda which then
     * gets stored and invoked later, there may still be unsafe multi-threaded access going on, so watch out for that.
     * This is just a guard rail that makes it harder to slip up.
     *
     * Example:
     *
     *```
     * private val state = Locker(object { var count: Int })
     * val current = state.locked { count++ }
     * ```
     *
     * **IMPORTANT:** The above short syntax relies heavily on Kotlin's type inference. In particular, the type of the
     * `Locker` that's parameterised by an anonymous object is non-denotable and thus you _cannot_ write an explicit
     * type for it. An attempt to do so will cause the lambdas to break. If this matters (e.g. you want to pass the box
     * as a parameter), just define a named class instead of using an anonymous object.
     *
     * @param content The object to take ownership of and synchronize on.
     */
    private class Locker<out T>(content: T) {
        /** @suppress */
        @JvmSynthetic
        @PublishedApi
        internal val __unlocked: T = content

        /**
         * Holds the lock whilst executing the block as an extension function on type [T].
         *
         * @param reentrancy If false and the current thread already holds the lock, throws [IllegalStateException]
         * with a message stating that "You may not call back into this object". This method is useful if you're invoking a
         * user supplied callback and want to ensure the user doesn't re-invoke methods on your class whilst you're in
         * the middle of processing a previous call. Defaults to 'true', meaning re-entrancy is allowed (the Java default).
         */
        @OptIn(ExperimentalContracts::class)
        inline fun <R> locked(reentrancy: Boolean = true, block: T.() -> R): R {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            if (!reentrancy && isLocked) throw IllegalStateException("You may not call back into this object.")
            return synchronized(__unlocked as Any) { __unlocked.block() }
        }

        /**
         * Returns true if the current thread holds the lock i.e. is inside a [locked] block.
         */
        inline val isLocked: Boolean get() = Thread.holdsLock(__unlocked)
    }

}
