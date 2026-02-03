import dev.progress4j.api.ProgressReport;
import dev.progress4j.terminal.TerminalProgressTracker;
import dev.progress4j.utils.OscProgressBarTracker;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static dev.progress4j.utils.ProgressGenerators.inputStreamWithProgress;
import static dev.progress4j.utils.ProgressGenerators.withProgress;

public class ProgressTrackingDemo {
    public static void main(String[] args) throws InterruptedException, IOException {
        ProgressReport.Tracker tracker = selectTracker(args);

        // A list of work.
        var items = List.of("one", "two", "three", "four", "five", "six", "seven", "eight", "nine");

        trackListProgress(items, tracker);
        trackInputStreamProgress(tracker);

        // There are also generators for iterators, iterables, arrays, Collection<CompletableFuture<?>> and more.
    }

    private static ProgressReport.Tracker selectTracker(String[] args) {
        OscProgressBarTracker osc = new OscProgressBarTracker();
        ProgressReport.Tracker terminal = TerminalProgressTracker.get();
        for (var arg : args) {
            if ("--osc".equals(arg)) {
                return progress -> {
                    terminal.report(progress);
                    osc.report(progress);
                };
            }
        }
        return terminal;
    }

    private static void trackInputStreamProgress(ProgressReport.Tracker tracker) throws IOException, InterruptedException {
        try (var input = withProgress(new FileInputStream("build.gradle.kts"), tracker)) {
            byte[] bytes = new byte[512];
            while (input.read(bytes) != -1) {
                Thread.sleep(100);
            }
        }

        // ... which for files is more easily expressed like this:
        try (var input = inputStreamWithProgress(Path.of("build.gradle.kts"), tracker, "Reading random data")) {
            // ...
        }


        try (var input = withProgress(new FileInputStream("build.gradle.kts"), tracker)) {
            byte[] bytes = new byte[512];
            while (input.read(bytes) != -1) {
                Thread.sleep(100);
            }
        }
    }

    private static void trackListProgress(List<String> items, ProgressReport.Tracker tracker) throws InterruptedException {
        // We can easily generate reports as we iterate over things. Don't change the message for each item. It could result in
        // distractingly rapid changes that aren't useful. See the style guide.
        for (var item : withProgress(items, tracker, "Doing something important")) {
            System.out.println(item);
            Thread.sleep(500);
        }
    }
}
