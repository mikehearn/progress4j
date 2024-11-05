package dev.progress4j.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

class ProgressReportImpl implements ProgressReport {
    private final @Nullable String message;
    private final long expectedTotal;
    private final long completed;

    private final Units units;

    private final List<@Nullable ProgressReport> subReports;

    public ProgressReportImpl(@Nullable String message, long expectedTotal, long completed, @NotNull ProgressReport.Units units, @NotNull Collection<? extends @Nullable ProgressReport> subReports) {
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
        this.subReports = new ArrayList<>(subReports.size());
        for (@Nullable ProgressReport subReport : subReports) {
            this.subReports.add(subReport != null ? subReport.immutableReport() : null);
        }
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
    public List<? extends ProgressReport> getSubReports() {
        return subReports;
    }

    /** Simply returns `this`, because this class is already immutable. */
    @Override
    public @NotNull ProgressReport immutableReport() {
        return this;
    }

    /** Copies this report into a new mutable report. */
    @Override
    public @NotNull MutableProgressReport mutableReport() {
        return new MutableProgressReportImpl(message, expectedTotal, completed, units, subReports);
    }

    @Override
    public @NotNull ProgressReport withCompleted(long value) {
        return new ProgressReportImpl(message, expectedTotal, value, units, subReports);
    }

    @Override
    public @NotNull ProgressReport withIncremented(long value) {
        return new ProgressReportImpl(message, expectedTotal, Math.min(expectedTotal, completed + value), units, subReports);
    }

    @Override
    public @NotNull ProgressReport withExpectedTotal(long value) {
        return new ProgressReportImpl(message, value, Math.min(completed, value), units, subReports);
    }

    @Override
    public @NotNull ProgressReport withMessage(@Nullable String message) {
        return new ProgressReportImpl(message, expectedTotal, completed, units, subReports);
    }

    @Override
    public @NotNull ProgressReport withSubReports(@NotNull Collection<? extends @Nullable ProgressReport> subReports) {
        return new ProgressReportImpl(message, expectedTotal, completed, units, subReports);
    }

    @Override
    public @NotNull ProgressReport withSubReport(int index, @Nullable ProgressReport subReport) {
        List<@Nullable ProgressReport> newSubReports = new ArrayList<>(subReports);
        newSubReports.set(index, subReport);
        return new ProgressReportImpl(message, expectedTotal, completed, units, newSubReports);
    }

    @Override
    public String toString() {
        return  message + " [" + completed + " of " + expectedTotal + (units == Units.BYTES ? " bytes" : "") + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProgressReportImpl that = (ProgressReportImpl) o;
        return expectedTotal == that.expectedTotal && completed == that.completed && Objects.equals(message, that.message) && units == that.units && Objects.equals(subReports, that.subReports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, expectedTotal, completed, units, subReports);
    }
}
