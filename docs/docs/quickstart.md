# Quickstart

## Documentation

Before diving in, you can explore the API in two ways:

- [Javadoc for Java users](javadoc/index.html) - Core API documentation with traditional Java interface documentation
- [KDoc for Kotlin users](dokka/index.html) - Full project documentation including Kotlin code and utilities

## Add dependencies

A minimal usage requires the core API:

=== "Gradle (Kotlin DSL)"
    ```kotlin
    dependencies {
        api("dev.progress4j:progress-api:0.1")
    }
    ```

=== "Maven"
    ```xml
    <dependency>
        <groupId>dev.progress4j</groupId>
        <artifactId>progress4j-api</artifactId>
        <version>0.1</version>
    </dependency>
    ```

The JPMS module name is `dev.progress4j.api`. This defines the core interface types `ProgressReport` and `MutableProgressReport`, which
come with default implementations.

The utilities library is convenient for generating progress reports.

=== "Gradle (Kotlin DSL)"
    ```kotlin
    dependencies {
        api("dev.progress4j:progress-utils:0.1")
    }
    ```

=== "Maven"
    ```xml
    <dependency>
        <groupId>dev.progress4j</groupId>
        <artifactId>progress4j-utils</artifactId>
        <version>0.1</version>
    </dependency>
    ```

The JPMS module name is `dev.progress4j.utils`, and it depends on the Kotlin standard library. 

For terminal rendering, you can use the terminal module:

=== "Gradle (Kotlin DSL)"
    ```kotlin
    dependencies {
        api("dev.progress4j:progress-terminal:0.1")
    }
    ```

=== "Maven"
    ```xml
    <dependency>
        <groupId>dev.progress4j</groupId>
        <artifactId>progress4j-terminal</artifactId>
        <version>0.1</version>
    </dependency>
    ```

## Understanding ProgressReport

A `ProgressReport` represents a snapshot of an operation's progress at a specific point in time. It contains:

- **Message**: A brief description of the operation (e.g., "Unpacking files")
- **Completed**: Units of work completed so far
- **ExpectedTotal**: Total units of work expected
- **Units**: The type of units being measured (ABSTRACT_CONSISTENT, ABSTRACT_INCONSISTENT, or BYTES)
- **SubReports**: Optional hierarchical sub-reports for nested operations

The API provides several factory methods to create reports:

=== "Java"
    ```java
    // Create a measurable progress report
    ProgressReport report = ProgressReport.create("Downloading files", 100, 25);
    
    // Create an indeterminate progress report (spinning wheel)
    ProgressReport indeterminate = ProgressReport.createIndeterminate("Initializing");
    
    // Create a completed report
    ProgressReport complete = ProgressReport.create("Operation complete", 100, 100);
    ```

=== "Kotlin"
    ```kotlin
    // Create a measurable progress report
    val report = ProgressReport.create("Downloading files", 100, 25)
    
    // Create an indeterminate progress report (spinning wheel)
    val indeterminate = ProgressReport.createIndeterminate("Initializing")
    
    // Create a completed report
    val complete = ProgressReport.create("Operation complete", 100, 100)
    ```

## Expose progress reports from an object

A `ProgressReport.Tracker` implementation receives reports from a `ProgressReport.Trackable`. You don't have to use these interfaces,
but it's nice to standardize.

=== "Java"
    ```java
    import dev.progress4j.api.ProgressReport;
    
    class Example implements ProgressReport.Trackable {
        private ProgressReport.Tracker progressTracker;
        
        @Override
        public Example trackProgressWith(@NotNull ProgressReport.Tracker tracker) {
            progressTracker = tracker;
            return this;
        }
        
        private void reportProgress(int completed, int total) {
            if (progressTracker != null) {
                progressTracker.report(ProgressReport.create("Processing items", total, completed));
            }
        }
    }
    ```

=== "Kotlin"
    ```kotlin
    import dev.progress4j.api.ProgressReport
    
    class Example : ProgressReport.Trackable {
        private var progressTracker: ProgressReport.Tracker? = null
        
        override fun trackProgressWith(tracker: ProgressReport.Tracker): Example {
            progressTracker = tracker
            return this
        }
        
        private fun reportProgress(completed: Int, total: Int) {
            progressTracker?.report(
                ProgressReport.create("Processing items", total, completed)
            )
        }
    }
    ```

## Using ProgressReport.Tracker

A `ProgressReport.Tracker` is the consumer of progress reports. The basic interface is simple:

```java
interface Tracker {
    void report(@NotNull ProgressReport progress);
}
```

Here's a simple tracker implementation that prints progress to standard output:

=== "Java"
    ```java
    ProgressReport.Tracker simpleTracker = progress -> {
        if (!progress.getIndeterminate()) {
            int percentage = (int) ((progress.getCompleted() * 100) / progress.getExpectedTotal());
            System.out.printf("%s: %d%%\n", progress.getMessage(), percentage);
        } else {
            System.out.printf("%s: Working...\n", progress.getMessage());
        }
    };
    ```

=== "Kotlin"
    ```kotlin
    val simpleTracker = ProgressReport.Tracker { progress ->
        if (!progress.indeterminate) {
            val percentage = ((progress.completed * 100) / progress.expectedTotal).toInt()
            println("${progress.message}: $percentage%")
        } else {
            println("${progress.message}: Working...")
        }
    }
    ```

For terminal display, use the `TerminalProgressTracker`:

=== "Java"
    ```java
    ProgressReport.Tracker tracker = TerminalProgressTracker.get();
    ```

=== "Kotlin"
    ```kotlin
    val tracker = TerminalProgressTracker.get()
    ```

## Generate progress reports

### Iterating Collections

Work often comes in the form of iterable things like lists. There's a utility method to make iterators automatically emit progress
as each item is traversed:

=== "Java"
    ```java
    void trackListProgress(List<String> items, ProgressReport.Tracker tracker) {
        for (var item : withProgress(items, tracker, "Processing items")) {
            // Do some slow work with each item
        }
    }
    ```

=== "Kotlin"
    ```kotlin
    fun trackListProgress(items: List<String>, tracker: ProgressReport.Tracker) {
        for (item in items.withProgress(tracker, "Processing items")) {
            // Do some slow work with each item
        }
    }
    ```

### Processing InputStreams

When reading files or streams, you can track the progress based on bytes read:

=== "Java"
    ```java
    // Track progress on a file input stream
    try (var input = withProgress(new FileInputStream("build.gradle.kts"), tracker)) {
        byte[] bytes = new byte[512];
        while (input.read(bytes) != -1) {
            // Process bytes
        }
    }
    
    // Or more concisely for files:
    try (var input = inputStreamWithProgress(Path.of("some-file.txt"), tracker, "Reading file")) {
        // Process the stream
    }
    ```

=== "Kotlin"
    ```kotlin
    // Track progress on a file input stream
    FileInputStream("build.gradle.kts").withProgress(tracker).use { input ->
        val bytes = ByteArray(512)
        while (input.read(bytes) != -1) {
            // Process bytes
        }
    }
    
    // Or more concisely for files:
    Path.of("some-file.txt").inputStreamWithProgress(tracker, "Reading file").use { input ->
        // Process the stream
    }
    ```

### Working with Futures

Track progress when working with asynchronous operations:

=== "Java"
    ```java
    List<CompletableFuture<Result>> futures = /* your futures */;
    futures.withProgressTrackedFutures(tracker, "Processing tasks in parallel")
          .forEach(CompletableFuture::join);
    ```

=== "Kotlin"
    ```kotlin
    val futures: List<CompletableFuture<Result>> = /* your futures */
    futures.withProgressTrackedFutures(tracker, "Processing tasks in parallel")
          .forEach { it.join() }
    ```

## Working with Mutable Reports

For cases where you want to update a report in place (to reduce garbage creation):

=== "Java"
    ```java
    MutableProgressReport report = MutableProgressReport.create("Loading", 100);
    tracker.report(report);
    
    for (int i = 0; i < 100; i++) {
        // Do work
        report.setCompleted(i + 1);
        tracker.report(report);
    }
    ```

=== "Kotlin"
    ```kotlin
    val report = MutableProgressReport.create("Loading", 100)
    tracker.report(report)
    
    for (i in 0 until 100) {
        // Do work
        report.completed = i + 1L
        tracker.report(report)
    }
    ```

## Hierarchical Progress Reports

Progress4j supports hierarchical progress reporting for complex operations:

=== "Java"
    ```java
    // Create a top-level report
    MutableProgressReport mainReport = MutableProgressReport.create("Main operation", 3);
    tracker.report(mainReport);
    
    // First subtask
    MutableProgressReport subTask1 = MutableProgressReport.create("Subtask 1", 100);
    List<ProgressReport> subReports = new ArrayList<>();
    subReports.add(subTask1);
    mainReport.setSubReports(subReports);
    
    // Update subtask progress
    for (int i = 0; i < 100; i++) {
        // Do work
        subTask1.setCompleted(i + 1);
        tracker.report(mainReport);
    }
    
    // Main task progress update
    mainReport.setCompleted(1);
    tracker.report(mainReport);
    
    // Second subtask (replacing the first in the reports list)
    MutableProgressReport subTask2 = MutableProgressReport.create("Subtask 2", 100);
    subReports.set(0, subTask2);
    mainReport.setSubReports(subReports);
    
    // ...and so on
    ```

=== "Kotlin"
    ```kotlin
    // Create a top-level report
    val mainReport = MutableProgressReport.create("Main operation", 3)
    tracker.report(mainReport)
    
    // First subtask
    val subTask1 = MutableProgressReport.create("Subtask 1", 100)
    val subReports = mutableListOf<ProgressReport?>(subTask1)
    mainReport.subReports = subReports
    
    // Update subtask progress
    for (i in 0 until 100) {
        // Do work
        subTask1.completed = (i + 1).toLong()
        tracker.report(mainReport)
    }
    
    // Main task progress update
    mainReport.completed = 1
    tracker.report(mainReport)
    
    // Second subtask (replacing the first in the reports list)
    val subTask2 = MutableProgressReport.create("Subtask 2", 100)
    subReports[0] = subTask2
    mainReport.subReports = subReports
    
    // ...and so on
    ```

For simplified hierarchical progress generation, use the `ThreeLevelProgressEmitter` from the utils package:

=== "Kotlin"
    ```kotlin
    val emitter = ThreeLevelProgressEmitter(
        tracker, 
        "Main operation", 
        expectedTasks = 3,
        units = ProgressReport.Units.ABSTRACT_CONSISTENT
    )
    
    emitter.startTask("First subtask")
    // Do work for first subtask
    emitter.completeTask()
    
    emitter.startTask("Second subtask")
    // Do work for second subtask, possibly emitting subtask progress
    emitter.emitDetail("Processing file", completed = 5, expected = 10)
    // More work...
    emitter.completeTask()
    
    emitter.startTask("Third subtask")
    // Do work for third subtask
    emitter.completeTask()
    
    // Mark overall operation as complete
    emitter.complete()
    ```

## Controlling Progress Report Frequency

For operations that might generate too many progress updates, use a `ProgressPacer` to throttle them:

=== "Java"
    ```java
    // Create a pacer that emits a maximum of 30 updates per second
    ProgressPacer pacer = new ProgressPacer(tracker, 30.0f);
    
    // Use the pacer instead of the original tracker
    for (int i = 0; i < 1000000; i++) {
        // Very frequent updates that would overwhelm UI
        pacer.report(ProgressReport.create("Fast operation", 1000000, i));
    }
    ```

=== "Kotlin"
    ```kotlin
    // Create a pacer that emits a maximum of 30 updates per second
    val pacer = ProgressPacer(tracker, 30.0f)
    
    // Use the pacer instead of the original tracker
    for (i in 0 until 1000000) {
        // Very frequent updates that would overwhelm UI
        pacer.report(ProgressReport.create("Fast operation", 1000000, i))
    }
    
    // Optionally close the pacer when done
    pacer.close()
    ```

## TerminalProgressTracker 

The `TerminalProgressTracker` provides an attractive terminal UI with animated progress bars:

=== "Java"
    ```java
    // Get the global terminal tracker instance
    ProgressReport.Tracker tracker = TerminalProgressTracker.get();
    
    // Use it for your operations
    processItems(items, tracker);
    
    // If you need to get a reference to close it when done:
    if (tracker instanceof AutoCloseable) {
        try {
            ((AutoCloseable) tracker).close();
        } catch (Exception e) {
            // Handle exception
        }
    }
    ```

=== "Kotlin"
    ```kotlin
    // Get the global terminal tracker instance
    val tracker = TerminalProgressTracker.get()
    
    // Use it for your operations
    processItems(items, tracker)
    
    // If you need to get a reference to close it when done:
    (tracker as? AutoCloseable)?.close()
    ```

The terminal tracker features:
- Animated Unicode spinner
- Color-coded progress bars
- Time elapsed display
- Support for hierarchical progress (up to 3 levels)
- Automatic handling of terminal output (System.out)

## Complete Example

=== "Java"
    ```java
    import dev.progress4j.api.ProgressReport;
    import dev.progress4j.terminal.TerminalProgressTracker;
    import java.util.List;
    import static dev.progress4j.utils.ProgressGenerators.withProgress;
    
    public class Example {
        public static void main(String[] args) throws InterruptedException {
            ProgressReport.Tracker tracker = TerminalProgressTracker.get();
            
            List<String> items = List.of("one", "two", "three", "four", "five");
            
            // Process items with progress tracking
            for (var item : withProgress(items, tracker, "Processing items")) {
                System.out.println("Processing: " + item);
                Thread.sleep(500); // Simulate work
            }
        }
    }
    ```

=== "Kotlin"
    ```kotlin
    import dev.progress4j.api.ProgressReport
    import dev.progress4j.terminal.TerminalProgressTracker
    import dev.progress4j.utils.withProgress
    
    fun main() {
        val tracker = TerminalProgressTracker.get()
        
        val items = listOf("one", "two", "three", "four", "five")
        
        // Process items with progress tracking
        for (item in items.withProgress(tracker, "Processing items")) {
            println("Processing: $item")
            Thread.sleep(500) // Simulate work
        }
    }
    ```