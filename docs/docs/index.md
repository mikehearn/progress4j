# Introduction

## Overview

Progress4J aims to be for progress reporting what SLF4J is to logging. The goal is to establish a common protocol for the reporting
of progress across all JVM apps, such that libraries can easily emit progress reports in a way that's decoupled from rendering.

It consists of a tiny core module with no dependencies defining a schema for progress events, meaning it can be incorporated into
essentially any library or tool that runs on the JVM. Progress reports can be generated without imposing any policy on what is done with
them. Then there are extra modules that transform, transport or render streams of progress events. Libraries can just depend on the core
module, or also the utility module if they want to, and applications can use the renderer modules.

### `api`

The core API defines `ProgressReport` and `MutableProgressReport`. It depends only on Java 11. The types have these features:

* Reports contain counts of the completed and expected work units. Completed=0, expected=1 is a special case defined to mean "indeterminate progress" as
  commonly shown with spinning wheels or bouncing bar.
* They can have messages. For internationalization, please see below.
* Can be arranged hierarchically, allowing the progress of complex multi-level operations to be reported. That doesn't imply progress
  renderers will always show all that detail - progress reporting is typically meant for humans after all - but it can at least be reported.
* Can specify the units of work: abstract consistent (meaningful to calculate an estimated time of completion), abstract inconsistent
  (work units have unpredictable duration) and bytes. These map well to commonly desired renderings.
* Can be serialized to JSON (see below).
* Reports can be both mutable and immutable (see below). This enables garbage-free reporting of progress for environments where that's important.

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
