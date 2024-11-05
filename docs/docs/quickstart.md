# Quickstart

## Documentation

Before diving in, you can explore the API in two ways:

- [Javadoc for Java users](javadoc/index.html) - Core API documentation with traditional Java interface documentation
- [KDoc for Kotlin users](dokka/index.html) - Full project documentation including Kotlin code and utilities

## Add dependencies

A minimal usage requires the core API:

=== "Gradle"
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

=== "Gradle"
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

## Expose progress reports from an object

A `ProgressReport.Tracker` implementation receives reports from a `ProgressReport.Trackable`. You don't have to use these interfaces,
but it's nice to standardize.

```java
import dev.progress4j.api.ProgressReport;

class Example implements ProgressReport.Trackable {
    private Tracker progressTracker;
    
    Example trackProgressWith(@NotNull Tracker tracker) {
        progressTracker = tracker;
    }
}
```

## Generate progress reports

Work often comes in the form of iterable things like lists. There's a utility method to make iterators automatically emit progress
as each item is traversed. The message here describes the overall operation. Don't set the text uniquely for each item as it can
flood the user with too much information if individual items are too fast.

```java
void trackListProgress(List<String> items, ProgressReport.Tracker tracker) {
    for (var item : withProgress(items, tracker, "Doing something important")) {
        // Do some slow work.
    }
}
```

The same holds true for processing an `InputStream`:

```java
try (var input = withProgress(new FileInputStream("build.gradle.kts"), tracker)) {
    byte[] bytes = new byte[512];
    while (input.read(bytes) != -1) {
        Thread.sleep(100);
    }
}

// ... which for files is more easily expressed like this:
try (var input = inputStreamWithProgress(Path.of("some file"), tracker, "Reading random data")) {
    // ...
}
```

These utilities try to work out the size and units of the target object automatically, but sometimes you'll need to specify it manually.
Therefore you can also supply a _base report_ which will be incremented.
