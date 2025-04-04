package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicReference

/**
 * Plain but coloured text rendering for parallel tasks, suitable for redirection to a log file.
 * Formats progress in a hierarchical structure with clear task relationships and columnar alignment.
 * 
 * Sample output format:
 * ```
 * [  0.05s]  Processing files                                         
 * [  0.10s]   • Download            → Fetching package.json           
 * [  1.25s]   ✓ Download            → Fetching package.json           100.0%
 * [  1.26s]   • Build               → Compiling sources               
 * [  1.85s]   • Build               → Compiling sources               50.0%
 * [  2.30s]   ✓ Build               → Compiling sources               100.0%
 * [  2.35s]  Processing files (Done)                                  100.0%
 * ```
 * 
 * Features:
 * - Columnar layout for better visual alignment
 * - Show intermediate progress for long-running tasks
 * - Color-coded output in terminals that support it
 * - Clear visual hierarchy between main tasks and subtasks
 * - Minimal noise with sensible update thresholds
 */
class PlainTaskTracker(private val output: PrintStream = System.out) : ProgressReport.Tracker {
    private val startTime = System.currentTimeMillis()
    private val lastProgress = AtomicReference<ProgressReport?>()
    private val taskHistory = mutableMapOf<Int, String>() // Track task messages by index
    private val hasColorSupport = detectColorSupport()

    companion object {
        // ANSI color constants for basic formatting
        private const val ANSI_RESET = "\u001B[0m"
        private const val ANSI_BOLD = "\u001B[1m"
        private const val ANSI_CYAN = "\u001B[36m"
        private const val ANSI_GREEN = "\u001B[32m"
        private const val ANSI_YELLOW = "\u001B[33m"
        private const val ANSI_BLUE = "\u001B[34m"
        private const val ANSI_GRAY = "\u001B[90m"
        private const val ANSI_BRIGHT_GREEN = "\u001B[92m"

        // Progress thresholds for intermediate updates
        private const val PROGRESS_PRINT_THRESHOLD = 10.0f  // Print every 10% progress
        private const val MIN_TIME_BETWEEN_UPDATES = 2000L  // Minimum 2 seconds between updates for the same task
    }

    // Detect if terminal supports color
    private fun detectColorSupport(): Boolean {
        val colorTerm = System.getenv("COLORTERM")
        val term = System.getenv("TERM")
        val ciMode = System.getenv("CI") != null
        
        // Most CI environments support color
        if (ciMode) return true
        
        // Check for color terminals
        if (colorTerm == "truecolor" || colorTerm == "24bit") return true
        if (term?.contains("color") == true) return true
        if (term == "xterm-256color" || term == "screen-256color") return true
        
        return false
    }
    
    private fun colorize(text: String, color: String): String {
        return if (hasColorSupport) "$color$text$ANSI_RESET" else text
    }
    
    private fun bold(text: String): String {
        return if (hasColorSupport) "$ANSI_BOLD$text$ANSI_RESET" else text
    }

    // Track the last time we printed progress for each task
    private val lastProgressUpdateTime = mutableMapOf<Int, Long>()
    
    // Track significant progress made since last report
    private val lastReportedProgress = mutableMapOf<Int, Float>()

    // Column widths for better alignment
    private val TIMESTAMP_WIDTH = 10  // [  0.05s]
    private val INDENT_WIDTH = 3      // "   "
    private val STATUS_WIDTH = 2      // "• " or "✓ "
    private val GROUP_MIN_WIDTH = 15  // Minimum width for task group name
    private val ARROW_WIDTH = 3       // " → "
    
    override fun report(progress: ProgressReport) {
        val last = lastProgress.getAndSet(progress.immutableReport())
        if (last == progress) return

        val currentTime = System.currentTimeMillis()
        val elapsedSecs = (currentTime - startTime) / 1000.0
        val timestamp = colorize(String.format("[%6.2fs]", elapsedSecs), ANSI_GRAY)

        // Main task progress
        val main = progress.immutableReport().withSubReports(emptyList())
        if (main != last?.immutableReport()?.withSubReports(emptyList())) {
            // Always print at start or completion
            val shouldPrintMain = main.complete || main.completed == 0L
            
            // For long-running tasks, print intermediate progress too
            if (!shouldPrintMain && !main.indeterminate && main.expectedTotal > 0) {
                val progressPercent = (main.completed.toFloat() / main.expectedTotal.toFloat()) * 100
                val lastReportedPercent = lastReportedProgress.getOrDefault(-1, 0.0f)
                val lastUpdateTime = lastProgressUpdateTime.getOrDefault(-1, 0L)
                
                // Print if we've made substantial progress and enough time has passed
                if ((progressPercent - lastReportedPercent >= PROGRESS_PRINT_THRESHOLD) && 
                    (currentTime - lastUpdateTime >= MIN_TIME_BETWEEN_UPDATES)) {
                    // Update tracking 
                    lastReportedProgress[-1] = progressPercent
                    lastProgressUpdateTime[-1] = currentTime
                    
                    printMainTaskLine(timestamp, main, false)
                }
            } else if (shouldPrintMain) {
                printMainTaskLine(timestamp, main, main.complete)
                
                // Reset tracking for main task
                if (main.complete) {
                    lastReportedProgress.remove(-1)
                    lastProgressUpdateTime.remove(-1)
                } else {
                    lastReportedProgress[-1] = 0.0f
                    lastProgressUpdateTime[-1] = currentTime
                }
            }
        }

        // Subtasks progress
        for ((index, report) in progress.subReports.withIndex()) {
            val subtask = report?.subReports?.firstOrNull() ?: continue
            val lastSubtask = last?.subReports?.getOrNull(index)?.subReports?.firstOrNull()
            
            if (lastSubtask == subtask) continue
            
            val reportMsg = report.message ?: ""
            val subtaskMsg = subtask.message ?: ""
            val groupName = reportMsg.takeIf { it.isNotEmpty() } ?: "Task Group $index"
            val taskInfo = subtaskMsg.takeIf { it.isNotEmpty() } ?: "Task $index"
            
            // Always print at start or completion
            val shouldPrintTask = subtask.completed == 0L || subtask.complete
            
            // For long-running tasks, print intermediate progress too
            if (!shouldPrintTask && !subtask.indeterminate && subtask.expectedTotal > 0) {
                val progressPercent = (subtask.completed.toFloat() / subtask.expectedTotal.toFloat()) * 100
                val lastReportedPercent = lastReportedProgress.getOrDefault(index, 0.0f)
                val lastUpdateTime = lastProgressUpdateTime.getOrDefault(index, 0L)
                
                // Print if we've made substantial progress and enough time has passed
                if ((progressPercent - lastReportedPercent >= PROGRESS_PRINT_THRESHOLD) && 
                    (currentTime - lastUpdateTime >= MIN_TIME_BETWEEN_UPDATES)) {
                    // Update tracking 
                    lastReportedProgress[index] = progressPercent
                    lastProgressUpdateTime[index] = currentTime
                    
                    // Update task history
                    taskHistory[index] = groupName
                    
                    printSubtaskLine(timestamp, subtask, groupName, taskInfo, subtask.complete)
                }
            } else if (shouldPrintTask) {
                // Update task history
                taskHistory[index] = groupName
                
                printSubtaskLine(timestamp, subtask, groupName, taskInfo, subtask.complete)
                
                // Reset tracking for this task
                if (subtask.complete) {
                    lastReportedProgress.remove(index)
                    lastProgressUpdateTime.remove(index)
                } else {
                    lastReportedProgress[index] = 0.0f
                    lastProgressUpdateTime[index] = currentTime
                }
            }
        }
    }
    
    private fun printMainTaskLine(timestamp: String, report: ProgressReport, isComplete: Boolean) {
        val mainMessage = report.message ?: "Working"
        val suffix = if (isComplete) colorize(" (Done)", ANSI_GREEN) else ""
        val mainInfo = bold(colorize(mainMessage, ANSI_CYAN))
        val percentIndicator = formatProgressIndicator(report)
        
        output.println("$timestamp  $mainInfo$suffix${" ".repeat(4)}$percentIndicator")
    }
    
    private fun printSubtaskLine(
        timestamp: String, 
        report: ProgressReport,
        groupName: String,
        taskInfo: String,
        isComplete: Boolean
    ) {
        val paddedIndent = " ".repeat(INDENT_WIDTH)
        
        // Format status indicator
        val statusIcon = if (isComplete) colorize("✓", ANSI_BRIGHT_GREEN) else colorize("•", ANSI_BLUE)
        val paddedStatus = statusIcon + " "
        
        // Format group name with consistent width
        val groupColor = if (isComplete) ANSI_GREEN else ANSI_CYAN
        val formattedGroup = colorize(groupName.padEnd(GROUP_MIN_WIDTH), groupColor)
        
        // Format connecting arrow
        val arrow = colorize("→", ANSI_GRAY)
        
        // Format percentage/progress indicator
        val percentIndicator = formatProgressIndicator(report)
        
        // Build the full line with consistent spacing
        output.println(
            "$timestamp$paddedIndent$paddedStatus$formattedGroup $arrow $taskInfo${" ".repeat(4)}$percentIndicator"
        )
    }
    
    private fun formatProgressIndicator(report: ProgressReport): String {
        if (report.indeterminate) {
            return ""
        }
        
        val percent = (report.completed.toFloat() / report.expectedTotal.toFloat()) * 100.0
        val percentText = String.format("%.1f%%", percent)
        
        if (report.units == ProgressReport.Units.BYTES) {
            val completed = formatByteSize(report.completed)
            val total = formatByteSize(report.expectedTotal)
            return colorize("$percentText ($completed of $total)", ANSI_YELLOW)
        }
        
        return colorize(percentText, ANSI_YELLOW)
    }
    
    private fun formatByteSize(bytes: Long): String {
        return when {
            bytes > 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)
            bytes > 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
            else -> String.format("%.1f KB", bytes / 1024.0)
        }
    }
}
