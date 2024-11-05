# Progress4J

Progress4J aims to be for progress reporting what SLF4J is to logging. The goal is to establish a common protocol for the reporting
of progress across all JVM apps so that libraries can easily emit progress reports in a way that's decoupled from rendering.

It consists of a tiny core module with no dependencies defining a schema for progress events, meaning it can be incorporated into
essentially any library or tool that runs on the JVM. Progress reports can be generated without imposing any policy on what is done with
them.

## Features and documentation

See [the documentation](docs/docs/index.md).

## Demo

```shell
./gradlew :progress4j-demo:installDist
./demo/build/install/progress4j-demo/bin/progress4j-demo
```

You need to run it outside Gradle to see the nice progress bar, because Gradle doesn't allow sub-processes direct terminal access when
using the `run` task.

## Usage for progress producers

To report progress of an operation your code should take a `ProgressReport.Tracker` object. You could take this as a method parameter, or by
implementing `ProgressReport.Trackable`. Once you have a progress tracker you can create `ProgressReport` objects using the static `create`
methods and pass them to the tracker's `report` method.

This will start an indeterminate progress operation: 

```kotlin
fun doSomething(progress: ProgressReport.Tracker) {
    progress.accept(ProgressReport.create("Mangling the wangle"))
}
```

A better way is like this, because it ensures the operation will always complete even in case of an exception: 

```kotlin
fun doSomething(progress: ProgressReport.Tracker) {
    progress.indeterminate("Mangling the wangle") {
        // ... stuff
    }
}
```

Often, you will know how much work there is to do:

```kotlin
fun doSomething(strings: List<String>, progress: ProgressReport.Tracker) {
    for (str in strings.withProgress(progress, "Mangling the strings")) {
        // ... stuff
    }
}

fun readStream(bytes: InputStream, progress: ProgressReport.Tracker) {
    bytes.withProgress(progress).transferTo(OutputStream.nullOutputStream())
}

// or ...
Path("foo.bin").inputStreamWithProgress(progress, "Reading foo.bin").use { stream -> /* ... */ }
```

Many of these utilities actually take `ProgressReport.Tracker?`, where null means "don't bother tracking progress".
By making progress consumers nullable you can therefore avoid the overhead in cases where the caller doesn't care. 
There's also the `IgnoreProgress` tracker which just throws away any reports it gets.

A common pattern is to start with a _base report_ and then modify it slightly each time. In this example the 
progress message is provided by the caller, and the code figures out the expected/total amounts by deriving modified 
versions of the base report.

```kotlin
fun doSomething(progress: ProgressReport.Tracker, baseReport: Progress) {
    var lastReport = baseReport.withExpectedTotal(thingsToDo.size())
    progress.accept(lastReport)
    try {
        while (thingsToDo) {
            lastReport = lastReport.withIncremented(1)
            progress.accept(lastReport)
        }
    } finally {
        progress.accept(lastReport.withExpected(lastReport.withCompleted))
    }
}

doSomething(listOf("a", "b", "c"), IgnoreProgress, ProgressReport.create("Mangling wangles"))
```

The `ProgressEmitter` simplifies this very common pattern:

```kotlin
fun doSomething(progress: ProgressReport.Tracker?) {
    ProgressEmitter(Progress.create("Mangling the wangle"), progress).use { emitter ->
        while (thingsToDo) {
            // ... do stuff ...
            emitter.increment()
        }
    }
}
```

## Remaining tasks

* Fix the InputStreamFilter to send a completion event when closed.
* Write docsite
* Upgrade to latest Mordant and fix terminal resize support.
* Implement an HTML web component that listens to a WebSocket for tracking progress.
* Implement Swing/JavaFX/Compose Desktop trackers.
* Consider making the library use the `Flow` interfaces added in Java 9.

## License

Apache 2
