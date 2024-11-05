package dev.progress4j.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A specialization of {@link ProgressReport} that allows changing the contents of the report, which is useful for managing progress in tight loops
 * without flooding the GC with new small objects.
 */
public interface MutableProgressReport extends ProgressReport {
    /**
     * Sets the specified value for completed.
     */
    void setCompleted(long value);

    /**
     * Increments the completed work by {@code value}, capped to {@link #getExpectedTotal()}}.
     */
    void increment(long value);

    /**
     * Sets the specified expected total. If {@link #getCompleted()} is higher than the new expected total, it is set
     * to the new value (i.e. 100% and thus {@link #getComplete()} by default would return true).
     */
    void setExpectedTotal(long value);

    /**
     * Sets the specified message, or null to indicate no message.
     */
    void setMessage(@Nullable String message);

    /**
     * Sets the specified sub-reports.
     */
    void setSubReports(@NotNull Collection<? extends @Nullable ProgressReport> subReports);

    /**
     * Replaces the specified sub-report.
     * The {@code index} must be between 0 (inclusive) and the size of {@link #getSubReports()} (exclusive).
     */
    void setSubReport(int index, @Nullable ProgressReport subReport);
}
