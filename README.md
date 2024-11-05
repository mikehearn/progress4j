# Progress4J

Progress4J aims to be for progress reporting what SLF4J is to logging. The goal is to establish a common protocol for the reporting
of progress across all JVM apps so that libraries can easily emit progress reports in a way that's decoupled from rendering and processing.

It consists of a tiny core module with no dependencies that defines a schema for progress events, meaning it can be incorporated into
essentially any library or tool that runs on the JVM. Progress reports can be generated without imposing any policy on what is done with
them. The core schema supports messages, units of measure, hierarchical task trees, arbitrary precision reporting and both mutable and 
immutable report objects. 

## Features and documentation

See [the documentation](docs/docs/index.md).

## Demo

```shell
./gradlew :progress4j-demo:installDist
./demo/build/install/progress4j-demo/bin/progress4j-demo
```

You need to run it outside Gradle to see the nice progress bar, because Gradle doesn't allow sub-processes direct terminal access when
using the `run` task.

## Remaining tasks

* Fix the InputStreamFilter to send a completion event when closed.
* Write docsite
* Upgrade to latest Mordant and fix terminal resize support.
* Implement an HTML web component that listens to a WebSocket for tracking progress.
* Implement Swing/JavaFX/Compose Desktop trackers.
* Consider making the library use the `Flow` interfaces added in Java 9.

## License

Apache 2
