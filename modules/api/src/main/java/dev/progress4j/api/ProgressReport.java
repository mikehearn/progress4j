package dev.progress4j.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <p>Exposes the progress of some operation independent of the details of how it's presented.</p>
 *
 * <p>
 * A progress report may have a message, a measurement of completed vs expected total work units, a unit of measure for that work, and
 * zero or more subreports. Together this lets you generate sophisticated hierarchical progress reports, potentially wrapping progress
 * from a sub-operation or library you rely on. Reports are passed to trackers which consume them, usually to inform the user of what's
 * going on. All the properties are optional (see below).
 *
 * <p>
 * Usually you will call {@link #createIndeterminate()} or a variant to get an implementation representing the progress as of some moment in
 * time, which will then be passed to a {@link ProgressReport.Tracker} that was passed in to the operation, perhaps via {@link
 * ProgressReport.Trackable}. As the operation advances new reports will be created (or a mutable report modified in-place, see below) and
 * passed to the progress tracker again.
 *
 * <h2>Properties</h2>
 *
 * {@link #getCompleted()} and {@link #getExpectedTotal()} are measured in terms of the specific {@link #getUnits()}. It's up to progress
 * trackers to turn this into a percentage if they need it. An operation is expected to indicate completion (normally or exceptionally)
 * by delivering a progress report in which {@code completed} == {@code expectedTotal}, and progress trackers may react to such a report
 * by hiding their UI. Trackers may use the {@code units} property to decide whether to show speed indicators, ETAs and other useful
 * estimates.
 *
 * <p>
 * It's valid for one report to be followed by another in which {@code completed} goes backwards, or {@code expectedTotal} changes
 * including by going backwards. This is useful if an operation ends in an error state, or if during the operation you discover there's more
 * or less work to do.
 *
 * <p>
 * Reports may have a message. If the message is null, trackers are expected to either simply not show a message at all, or use a generic
 * message like "Working", "Please wait" etc. If set the message should briefly describe the operation, e.g. "Unpacking files". The message
 * should <i>not</i> include any of the following:
 *
 * <ol>
 *     <li>Words like "Working", "Progress", "please wait" etc. Generic boilerplate is the responsibility of the tracker to render.</li>
 *     <li>The total number of items e.g. "Unpacking 12 files". The total amount of work should be exposed via {@link #getExpectedTotal()},
 *     and it's up to the tracker implementations to render the quantity in the UI if desired.</li>
 *     <li>Text that changes with every single report e.g. "Unpacking foobar.txt". A stream of progress reports should have a consistent
 *     message. Although nothing stops you changing the message in every report, it can be distracting for messages to frequently change
 *     whilst the user is trying to read them. If you want to expose some notion of sub-tasks, see below.</li>
 * </ol>
 *
 * <h2>Indeterminate reports</h2>
 *
 * A work report that has {@code complete} = 0 and {@code expectedTotal} = 1 indicates an <i>indeterminate</i> progress report, in which how
 * long the operation will take is unknown. A progress tracker will render it by showing e.g. a spinner animation to communicate that
 * something is being done. If expectedTotal is higher than 1 then it is considered to be a measurable operation and a progress bar of some
 * sort is a likely rendering.
 *
 * <h2>Mutability</h2>
 *
 * A {@link ProgressReport} is a read only view of a potentially changing report object. For the duration of a {@link
 * Tracker#report(ProgressReport)} call the report object isn't allowed to change, but once the {@code report} callback method finishes an
 * implementation of this interface may return new values from its properties. If you need to keep a copy of a report, use {@link
 * #immutableReport()}. Alternatively if you want to get a copy that you can then start editing in place, use {@link #mutableReport()}.
 * Mutable reports are useful for when you might need to report progress very frequently and don't wish to create excess garbage. In that
 * situation it's up to the tracker to thin out reports to the frequency they need, and there are utilities in the {@code
 * dev.progress4j.utils} package to do this for you.
 *
 * <h2>Hierarchical progress</h2>
 *
 * A progress report may have zero or more sub-reports. This allows you to report the progress of an operation that is made up of a tree
 * of parallel sub-operations. There's a limit to how much detail a human can actually absorb, so most tracker implementations will elect to
 * either ignore this information or to only show one or two levels deep in the tree. However there's no limit to how much detail you
 * can report this way.
 *
 * <p>
 * Sub-reports are <i>ordered</i>. If you have two sub-operations proceeding in parallel, then the progress report from each one must always
 * be assigned to the same index in the {@link #getSubReports()} collection. If one task finishes earlier than the other then its slot
 * should be set to null. Don't move reports from the same task to different slots, as trackers are allowed to assume stability and may map
 * sub-report slots to e.g. stacked progress bars, tree view positions or other cases where it'd be annoying for tasks to jump around
 * visually. It's up to you how sub-report progress is aggregated into {@code completed} and {@code expectedTotal}, if at all. There are
 * utilities available to help you compose multiple streams of progress together using sub-reports, and it's recommended you use them if you
 * wish to report hierarchical progress.
 *
 * <h2>Extending reports with more information</h2>
 *
 * {@link ProgressReport} is an interface so you can easily create your own progress report objects that expose more information that makes
 * sense for a generic schema. Don't use sub-reports to transmit things like per-item messages or images, instead extend this interface and
 * add your own properties.
 *
 * <h2>Errors, cancellation etc</h2>
 *
 * Progress reporting is deliberately simple and isn't meant to be a general framework for controlling tasks. Use some other system to
 * implement task cancellation and error reporting.
 */
public interface ProgressReport {
    /**
     * Returns an immutable progress report. The sub-reports list is copied.
     */
    static ProgressReport create(@Nullable String message, long expectedTotal, long completed, @NotNull ProgressReport.Units units, @NotNull Collection<? extends @Nullable ProgressReport> subReports) {
        return new ProgressReportImpl(message, expectedTotal, completed, units, subReports);
    }

    /**
     * Returns an immutable progress report with no sub-reports.
     */
    static ProgressReport create(@Nullable String message, long expectedTotal, long completed, @NotNull ProgressReport.Units units) {
        return create(message, expectedTotal, completed, units, new ArrayList<>());
    }

    /**
     * Returns an immutable progress report with {@link Units#ABSTRACT_CONSISTENT} units and no sub-reports.
     */
    static ProgressReport create(@Nullable String message, long expectedTotal, long completed) {
        return create(message, expectedTotal, completed, Units.ABSTRACT_CONSISTENT, new ArrayList<>());
    }

    /**
     * Returns an immutable progress report with no completed work units, {@link Units#ABSTRACT_CONSISTENT} units and no sub-reports.
     */
    static ProgressReport create(@Nullable String message, long expectedTotal) {
        return create(message, expectedTotal, 0L, Units.ABSTRACT_CONSISTENT, new ArrayList<>());
    }

    /**
     * Returns an immutable indeterminate progress report with no sub-reports. Send this report {@link #withCompleted(long)}  = 1
     * to indicate a finished operation.
     */
    static ProgressReport createIndeterminate(@Nullable String message) {
        return create(message, 1L, 0L, Units.ABSTRACT_CONSISTENT, new ArrayList<>());
    }

    /**
     * Returns an immutable indeterminate progress report with no message or sub-reports. Such a report indicates merely that
     * something is happening. Send this report {@link #withCompleted(long)} = 1 to indicate a finished operation.
     */
    static ProgressReport createIndeterminate() {
        return create(null, 1L, 0L, Units.ABSTRACT_CONSISTENT, new ArrayList<>());
    }

    /** A convenience constructor that takes ints instead of longs. The sub-reports list is copied. */
    static ProgressReport create(@Nullable String message, int expectedTotal, int completed, @NotNull ProgressReport.Units units, @NotNull List<@Nullable ProgressReport> subReports) {
        return create(message, (long)expectedTotal, (long)completed, units, subReports);
    }

    /** A convenience constructor that takes ints instead of longs. */
    static ProgressReport create(@Nullable String message, int expectedTotal, int completed, @NotNull ProgressReport.Units units) {
        return create(message, expectedTotal, completed, units, new ArrayList<>());
    }

    /** A convenience constructor that takes ints instead of longs. */
    static ProgressReport create(@Nullable String message, int expectedTotal, int completed) {
        return create(message, expectedTotal, completed, Units.ABSTRACT_CONSISTENT, new ArrayList<>());
    }

    /** A convenience constructor that takes ints instead of longs. */
    static ProgressReport create(@Nullable String message, int expectedTotal) {
        return create(message, expectedTotal, 0L, Units.ABSTRACT_CONSISTENT, new ArrayList<>());
    }

    /**
     * What the units are for {@link #getCompleted()} and {@link #getExpectedTotal()}.
     */
    enum Units {
        /**
         * Units of work that don't have any particular meaning, but in which each unit takes roughly the same amount of time to process.
         * Trackers may react to this unit by computing an ETA.
         */
        ABSTRACT_CONSISTENT,

        /**
         * Units of work that don't have any particular meaning, and in which steps in the operation may take varying amounts of time.
         * Trackers may react to this unit by showing a simple rendering, without helpful estimates.
         */
        ABSTRACT_INCONSISTENT,

        /**
         * Work that's measured in bytes. Trackers may react to this unit by showing speed in kilo/megabytes per second, and possibly
         * an ETA.
         */
        BYTES
    }

    /**
     * String intended to be translated into the user's local language but may be a language-independent message suitable for printing next
     * to a progress indicator of some sort, e.g. a URL or file name. If null, UI should use a generic message like "Working".
     */
    @Nullable
    String getMessage();

    // TODO(low): Better localization support.

    /** The current belief about the total units for the entire operation (i.e. not how much remains). */
    long getExpectedTotal();

    /**
     * Units of work done so far in the operation.
     */
    long getCompleted();

    /**
     * What units `getCompleted` and `getExpectedTotal` are measured in.
     */
    @NotNull
    Units getUnits();

    /**
     * Returns true to indicate that this operation should be treated as of indeterminate length.
     * The default implementation returns true if {@link #getExpectedTotal()} is 1 and {@link #getCompleted()} is zero or one.
     */
    default boolean getIndeterminate() {
        return getExpectedTotal() == 1L && getCompleted() <= 1L;
    }

    /**
     * Returns true when this operation is complete.
     * The default implementation returns true when {@link #getCompleted()} &gt;= {@link #getExpectedTotal()}.
     */
    default boolean getComplete() {
        return getCompleted() >= getExpectedTotal();
    }

    /**
     * An ordered list of progress reports that provide further information on the progress of the current step represented by this report.
     * <p>
     * Sub-reports provide a way to express hierarchical progress. Although deeply nested progress reports aren't very useful
     * for visualization and will usually be truncated at one or two levels deep before display, they arise naturally when composing
     * libraries. There's no obligation for a progress tracker to consume sub-reports.
     * <p>
     * Positioning within this list is meaningful and may be used by progress trackers to provide visual stability. It's OK for entries
     * to be null: this allows an operation to allocate an array up front into which multiple parallel sub-tasks write their progress
     * reports, with null indicating a task that either hasn't started yet or which has completed. An entry should be nulled out *after*
     * sending a completion report in that case.
     */
    @NotNull
    List<? extends @Nullable ProgressReport> getSubReports();

    /**
     * Returns an immutable version of the progress report. If the implementation is already immutable, just returns 'this'.
     */
    @NotNull
    ProgressReport immutableReport();

    /**
     * Returns a mutable copy of the progress report.
     */
    @NotNull
    MutableProgressReport mutableReport();

    /**
     * Returns a copy of this report with the specified value for completed.
     */
    @NotNull
    ProgressReport withCompleted(long value);

    /**
     * Returns a copy of this report with the {@link #getCompleted()} incremented by {@code value} and capped to {@link #getExpectedTotal()}}.
     */
    @NotNull
    ProgressReport withIncremented(long value);

    /**
     * Returns a copy of this report with the specified expected total. If {@link #getCompleted()} is higher than the new expected total, it is set
     * to the new value (i.e. 100% and thus {@link #getComplete()} by default would return true).
     */
    @NotNull
    ProgressReport withExpectedTotal(long value);

    /**
     * Returns a copy of this report with the specified message, or null to indicate no message.
     */
    @NotNull
    ProgressReport withMessage(@Nullable String message);

    /**
     * Returns a copy of this report with the specified sub-reports.
     */
    @NotNull
    ProgressReport withSubReports(@NotNull Collection<? extends @Nullable ProgressReport> subReports);

    /**
     * Returns a copy of this report with the specified sub-report replaced.
     * The {@code index} must be between 0 (inclusive) and the size of {@link #getSubReports()} (exclusive).
     */
    @NotNull
    ProgressReport withSubReport(int index, @Nullable ProgressReport subReport);

    /**
     * An object that receives progress reports from some other code that generates them.
     */
    @FunctionalInterface
    interface Tracker {
        /**
         * Reports progress to the tracker.
         * <p>
         * Implementations of this interface should be fast, as performance sensitive operations might call {@link #report(ProgressReport)} from
         * hot loops.
         * <p>
         * Implementations can assume the report is immutable <i>only for the duration of the {@link #report(ProgressReport)} call</i>, as the caller
         * is allowed to use a mutable report and reuse it after this method returns.
         */
        void report(@NotNull ProgressReport progress);
    }

    /** An object that generates progress reports. This interface is optional but convenient to standardize on. */
    interface Trackable {
        /**
         * Sets the progress tracker to be used.
         * <p>
         * Progress reports may be passed to the callback in parallel. Implementations will normally narrow the return type to allow for
         * fluent usage.
         */
        @NotNull
        Trackable trackProgressWith(@NotNull Tracker tracker);
    }
}
