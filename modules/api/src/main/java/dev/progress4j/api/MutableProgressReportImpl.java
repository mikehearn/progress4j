package dev.progress4j.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

class MutableProgressReportImpl implements ProgressReport, MutableProgressReport {
    private @Nullable String message;
    private long expectedTotal;
    private long completed;
    private final Units units;
    private final ArrayList<@Nullable MutableProgressReport> subReports;

    public MutableProgressReportImpl(@Nullable String message, long expectedTotal, long completed, @NotNull ProgressReport.Units units, @NotNull Collection<? extends @Nullable ProgressReport> subReports) {
        if (expectedTotal < 1) {
            throw new IllegalArgumentException("expectedTotal must be >= 1");
        }
        if (completed < 0) {
            throw new IllegalArgumentException("completed must be >= 0");
        }
        this.message = message;
        this.expectedTotal = expectedTotal;
        this.completed = completed;
        this.units = units;
        this.subReports = new ArrayList<>(subReports.stream().map((it) -> {
            if (it == null) return null;
            return it.mutableReport();
        }).toList());
    }

    public MutableProgressReportImpl(@Nullable String message, long expectedTotal, long completed, @NotNull ProgressReport.Units units) {
        this(message, expectedTotal, completed, units, new ArrayList<>());
    }

    public MutableProgressReportImpl(@Nullable String message, long expectedTotal, long completed) {
        this(message, expectedTotal, completed, Units.ABSTRACT_CONSISTENT, new ArrayList<>());
    }

    public MutableProgressReportImpl(@Nullable String message, long expectedTotal) {
        this(message, expectedTotal, 0L, Units.ABSTRACT_CONSISTENT, new ArrayList<>());
    }

    public MutableProgressReportImpl(@Nullable String message) {
        this(message, 1L, 0L, Units.ABSTRACT_CONSISTENT, new ArrayList<>());
    }

    public MutableProgressReportImpl() {
        this(null, 1L, 0L, Units.ABSTRACT_CONSISTENT, new ArrayList<>());
    }

    /**
     * A convenience constructor that takes ints instead of longs.
     */
    public MutableProgressReportImpl(@Nullable String message, int expectedTotal, int completed, @NotNull ProgressReport.Units units, @NotNull List<@Nullable ProgressReport> subReports) {
        this(message, (long) expectedTotal, (long) completed, units, subReports);
    }

    public MutableProgressReportImpl(@Nullable String message, int expectedTotal, int completed, @NotNull ProgressReport.Units units) {
        this(message, expectedTotal, completed, units, new ArrayList<>());
    }

    public MutableProgressReportImpl(@Nullable String message, int expectedTotal, int completed) {
        this(message, expectedTotal, completed, Units.ABSTRACT_CONSISTENT, new ArrayList<>());
    }

    public MutableProgressReportImpl(@Nullable String message, int expectedTotal) {
        this(message, expectedTotal, 0L, Units.ABSTRACT_CONSISTENT, new ArrayList<>());
    }

    @Nullable
    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public long getExpectedTotal() {
        return expectedTotal;
    }

    @Override
    public long getCompleted() {
        return completed;
    }

    @NotNull
    @Override
    public ProgressReport.Units getUnits() {
        return units;
    }

    @NotNull
    @Override
    public List<? extends @Nullable ProgressReport> getSubReports() {
        return subReports;
    }

    /**
     * Snapshots the contents of this progress report into a new, immutable report.
     */
    @Override
    public @NotNull ProgressReport immutableReport() {
        return new ProgressReportImpl(message, expectedTotal, completed, units, subReports);
    }

    /** Simply returns `this`, because this class is already mutable. */
    @Override
    public @NotNull MutableProgressReport mutableReport() {
        return this;
    }

    @Override
    public void setCompleted(long value) {
        completed = value;
    }

    @Override
    public void increment(long value) {
        completed = Math.min(expectedTotal, completed + value);
    }

    @Override
    public void setExpectedTotal(long value) {
        expectedTotal = value;
        completed = Math.min(completed, value);
    }

    @Override
    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    @Override
    public void setSubReports(@NotNull Collection<? extends @Nullable ProgressReport> subReports) {
        this.subReports.clear();
        this.subReports.addAll(subReports.stream().map(it -> {
            if (it == null) return null;
            return it.mutableReport();
        }).toList());
    }

    @Override
    public void setSubReport(int index, @Nullable ProgressReport subReport) {
        MutableProgressReport mutableSubReport;
        if (subReport == null) {
            mutableSubReport = null;
        } else mutableSubReport = subReport.mutableReport();
        subReports.set(index, mutableSubReport);
    }

    @Override
    public String toString() {
        return  message + " [" + completed + " of " + expectedTotal + (units == Units.BYTES ? " bytes" : "") + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MutableProgressReport that = (MutableProgressReport) o;
        return expectedTotal == that.getExpectedTotal() && completed == that.getCompleted() && Objects.equals(message, that.getMessage()) && units == that.getUnits() && Objects.equals(subReports, that.getSubReports());
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, expectedTotal, completed, units, subReports);
    }

    @Override
    public @NotNull ProgressReport withCompleted(long value) {
        setCompleted(value);
        return this;
    }

    @Override
    public @NotNull ProgressReport withIncremented(long value) {
        increment(value);
        return this;
    }

    @Override
    public @NotNull ProgressReport withExpectedTotal(long value) {
        setExpectedTotal(value);
        return this;
    }

    @Override
    public @NotNull ProgressReport withMessage(@Nullable String message) {
        setMessage(message);
        return this;
    }

    @Override
    public @NotNull ProgressReport withSubReports(@NotNull Collection<? extends @Nullable ProgressReport> subReports) {
        setSubReports(subReports);
        return this;
    }

    @Override
    public @NotNull ProgressReport withSubReport(int index, @Nullable ProgressReport subReport) {
        setSubReport(index, subReport);
        return this;
    }
}
