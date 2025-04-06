# Introduction

## Overview

Progress4J aims to be for progress reporting what SLF4J is to logging. The goal is to establish a common protocol for the reporting
of progress across all JVM apps, such that libraries can easily emit progress reports in a way that's decoupled from rendering.

It consists of a tiny core module with no dependencies defining a schema for progress events, meaning it can be incorporated into
essentially any library or tool that runs on the JVM. Progress reports can be generated without imposing any policy on what is done with
them. Then there are extra modules that transform, transport or render streams of progress events. Libraries can just depend on the core
module, or also the utility module if they want to, and applications can use the renderer modules.

## Architecture

Progress4J is structured into several modules:

### `api`

The core API defines `ProgressReport` and `MutableProgressReport`. It depends only on Java 11. The types have these features:

* Reports contain counts of the `completed` and `expectedTotal` work units. Completed=0, expectedTotal=1 is a special case defined to mean "indeterminate progress" as commonly shown with spinning wheels or bouncing bar.
* They can have messages that describe the operation in progress.
* Can be arranged hierarchically, allowing the progress of complex multi-level operations to be reported. That doesn't imply progress renderers will always show all that detail - progress reporting is typically meant for humans after all - but it can at least be reported.
* Can specify the units of work: abstract consistent (meaningful to calculate an estimated time of completion), abstract inconsistent (work units have unpredictable duration) and bytes. These map well to commonly desired renderings.
* Can be serialized to JSON (see below).
* Reports can be both mutable and immutable. This enables garbage-free reporting of progress for environments where that's important.

The `ProgressReport.Trackable` interface advertises that your object can produce progress reports, and `ProgressReport.Tracker` receives
them. This is simpler than `java.util.concurrent.Flow` or reactive streams as it's rarely necessary to have more than one receiver of
a progress report from an object, and it's easy to fan out if needed (e.g. to both log and draw reports).

`api` has a JPMS module descriptor.

### `utils`

Provides the following features:

1. Utilities to generate streams of progress events as you iterate over a collection or `Stream<T>`, read an `InputStream`, read through a
   file, and more.
2. Utilities to do common progress related tasks like combine streams of reports from parallel sub-operations, accumulate statistics, relay
   reports between threads and impose pacing so other progress tracking objects aren't flooded.
3. Lightweight serialization of events to JSON. No JSON library is required as a dependency, making this a simple way to report progress
   in command line tools, or to web browsers (deserialization does still require a JSON library).
4. A renderer that just prints to stdout.

`utils` has a JPMS module descriptor and depends on the Kotlin standard library.

### `terminal`

A terminal implementation that features attractive Unicode rendering with animated progress bars. It can report up to three hierarchical
levels of detail in a way that looks good, and has been used in a commercial product for the last couple of years.

Depends on the Kotlin standard library and [Mordant](https://www.github.com/ajalt/mordant), a terminal rendering library.

## Key Concepts

### Progress Reports

A progress report represents the state of an operation at a specific point in time. It includes:

- **Message**: A human-readable description of what's happening
- **Completed**: How many work units have been completed so far
- **ExpectedTotal**: How many work units are expected in total
- **Units**: What kind of units we're talking about (abstract consistent, abstract inconsistent, or bytes)
- **SubReports**: Optional hierarchical progress information for sub-operations

=== "Java"
    ```java
    // Create a measurable progress report
    ProgressReport report = ProgressReport.create("Downloading files", 100, 25);
    
    // You can check properties
    long completed = report.getCompleted();        // 25
    long expected = report.getExpectedTotal();     // 100
    String message = report.getMessage();          // "Downloading files"
    boolean isComplete = report.getComplete();     // false (25 < 100)
    ProgressReport.Units units = report.getUnits(); // ABSTRACT_CONSISTENT by default
    ```

=== "Kotlin"
    ```kotlin
    // Create a measurable progress report
    val report = ProgressReport.create("Downloading files", 100, 25)
    
    // You can check properties
    val completed = report.completed        // 25
    val expected = report.expectedTotal     // 100
    val message = report.message            // "Downloading files"
    val isComplete = report.complete        // false (25 < 100)
    val units = report.units                // ABSTRACT_CONSISTENT by default
    ```

### Indeterminate Progress

When you don't know how much work remains, you can use an "indeterminate" progress report:

=== "Java"
    ```java
    ProgressReport indeterminate = ProgressReport.createIndeterminate("Working...");
    boolean isIndeterminate = indeterminate.getIndeterminate(); // true
    ```

=== "Kotlin"
    ```kotlin
    val indeterminate = ProgressReport.createIndeterminate("Working...")
    val isIndeterminate = indeterminate.indeterminate // true
    ```

This is represented as a report with `completed = 0` and `expectedTotal = 1`, signaling to renderers that they should show an indeterminate spinner or animation.

### Trackers

A `ProgressReport.Tracker` is the consumer of progress reports. It receives reports and does something with them - typically rendering to a UI. The interface is intentionally simple:

=== "Java"
    ```java
    ProgressReport.Tracker tracker = new ProgressReport.Tracker() {
        @Override
        public void report(@NotNull ProgressReport progress) {
            // Do something with the progress report
            System.out.printf("%s: %.1f%%\n", 
                progress.getMessage(),
                (100.0 * progress.getCompleted()) / progress.getExpectedTotal());
        }
    };
    
    // Or with a lambda
    ProgressReport.Tracker lambdaTracker = progress -> {
        // Handle the progress report
    };
    ```

=== "Kotlin"
    ```kotlin
    val tracker = object : ProgressReport.Tracker {
        override fun report(progress: ProgressReport) {
            // Do something with the progress report
            println("${progress.message}: ${(100.0 * progress.completed) / progress.expectedTotal}%")
        }
    }
    
    // Or with a lambda
    val lambdaTracker = ProgressReport.Tracker { progress ->
        // Handle the progress report
    }
    ```

### Hierarchical Progress

For complex operations with multiple steps or parallel tasks, you can nest progress reports using subReports:

=== "Java"
    ```java
    // Create a top-level report for an operation with 3 steps
    ProgressReport mainTask = ProgressReport.create("Main task", 3, 1);
    
    // Create a report for the current subtask (step 1 of 3)
    ProgressReport subTask = ProgressReport.create("Sub task 1/3", 100, 50);
    
    // Add the subtask to the main task
    mainTask = mainTask.withSubReport(0, subTask);
    
    // Send the combined report
    tracker.report(mainTask);
    ```

=== "Kotlin"
    ```kotlin
    // Create a top-level report for an operation with 3 steps
    var mainTask = ProgressReport.create("Main task", 3, 1)
    
    // Create a report for the current subtask (step 1 of 3)
    val subTask = ProgressReport.create("Sub task 1/3", 100, 50)
    
    // Add the subtask to the main task
    mainTask = mainTask.withSubReport(0, subTask)
    
    // Send the combined report
    tracker.report(mainTask)
    ```

This allows rich, detailed progress reporting for complex operations.

### Common Patterns

1. **Collection Processing**: Use `withProgress` to automatically track iteration through collections

    === "Java"
        ```java
        // Track progress while iterating through a list
        for (String item : withProgress(items, tracker, "Processing items")) {
            // Process each item
        }
        ```

    === "Kotlin"
        ```kotlin
        // Track progress while iterating through a list
        for (item in items.withProgress(tracker, "Processing items")) {
            // Process each item
        }
        ```

2. **Stream Processing**: Use input stream wrappers to track bytes read

    === "Java"
        ```java
        try (var input = inputStreamWithProgress(Path.of("large-file.dat"), tracker, "Reading file")) {
            // Read from the stream, progress is automatically reported
        }
        ```

    === "Kotlin"
        ```kotlin
        Path.of("large-file.dat").inputStreamWithProgress(tracker, "Reading file").use { input ->
            // Read from the stream, progress is automatically reported
        }
        ```

3. **Parallel Tasks**: Track completable futures using `withProgressTrackedFutures`

    === "Java"
        ```java
        List<CompletableFuture<Result>> futures = tasks.stream()
            .map(task -> CompletableFuture.supplyAsync(() -> processTask(task)))
            .collect(Collectors.toList());
            
        futures.withProgressTrackedFutures(tracker, "Processing tasks in parallel")
               .forEach(CompletableFuture::join);
        ```

    === "Kotlin"
        ```kotlin
        val futures = tasks.map { task ->
            CompletableFuture.supplyAsync { processTask(task) }
        }
        
        futures.withProgressTrackedFutures(tracker, "Processing tasks in parallel")
               .forEach { it.join() }
        ```

4. **High-Frequency Updates**: Use `ProgressPacer` to avoid UI flooding

    === "Java"
        ```java
        ProgressPacer pacer = new ProgressPacer(tracker, 30.0f); // Max 30 updates per second
        
        for (int i = 0; i < 1_000_000; i++) {
            // This would generate too many updates without pacing
            pacer.report(ProgressReport.create("Processing", 1_000_000, i));
        }
        ```

    === "Kotlin"
        ```kotlin
        val pacer = ProgressPacer(tracker, 30.0f) // Max 30 updates per second
        
        for (i in 0 until 1_000_000) {
            // This would generate too many updates without pacing
            pacer.report(ProgressReport.create("Processing", 1_000_000, i))
        }
        ```

## Best Practices

### Progress Report Messages

- Be concise and descriptive
- Avoid including "Processing" or "Working on" - these are implied
- Don't include the progress percent or count - the tracker will handle that
- Don't change messages too frequently - it makes them hard to read
- Use hierarchical reports for detailed step-by-step information

### Performance Considerations

- For frequently updating progress, use `ProgressPacer` to limit update frequency
- Consider using `MutableProgressReport` for high-performance scenarios to reduce object creation
- The `ProgressReport.Tracker` interface is designed to be fast, but implementations may have overhead

### Thread Safety

- Progress reports are immutable by default, making them thread-safe
- Trackers should be prepared to receive reports from multiple threads
- Use `ProgressStreamCombiner` when combining progress from parallel operations

## Next Steps

- Check out the [Quickstart Guide](quickstart.md) for examples and basic usage
- Explore the [JSON Schema](json.md) for serializing progress reports
