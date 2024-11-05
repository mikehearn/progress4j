@file:JvmName("ProgressGenerators")
package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import java.io.*
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.name

/**
 * Wraps an input stream with a filter that emits progress events as the stream is read.
 *
 * If [baseReport] is set, progress reports are derived from it using [ProgressReport.withIncremented]. Otherwise, a message-less base report
 * is created that uses the size of the stream directly if it's a [FileInputStream] or via [InputStream.available] if not. Therefore,
 * if you specify your own base report, try to specify the total expected size in bytes if known.
 *
 * When reading from a file via a [Path] use [Path.inputStreamWithProgress], which reads the file size and name (for the message), and
 * also opens the stream for you.
 *
 * Completion occurs when the stream receives an end-of-stream return code (-1) from calling `stream.read` or is closed. Reading
 * more bytes than were expected just results in new progress reports with a new higher value for [ProgressReport.getExpectedTotal].
 *
 * @param callback The callback to invoke with progress reports. If null, returns 'this'
 * @param baseReport The report object to copy for each new progress report sent.
 * @return the newly wrapped stream or 'this' if callback is null.
 */
@JvmOverloads
fun InputStream.withProgress(
    callback: ProgressReport.Tracker?,
    baseReport: ProgressReport? = null
): InputStream {
    if (callback == null) return this

    val report = if (baseReport != null) {
        baseReport
    } else {
        // Try and figure out how big the stream might be.
        val r = ProgressReport.createIndeterminate(null)
        if (this is FileInputStream && channel.size() > 0)
            r.withExpectedTotal(channel.size())
        else if (this.javaClass.name == "sun.nio.ch.ChannelInputStream")
            throw IllegalArgumentException("Don't write path.newInputStream().withProgress(), write path.withProgress().")
        else if (available().toLong() > 0)
            r.withExpectedTotal(available().toLong())
        else
            r  // Unknown or zero size.
    }
    return InputStreamFilter(this, callback, report)
}

/**
 * Wraps an input stream with a filter that emits progress events as the stream is read.
 *
 * When reading from a file via a [Path] use [Path.inputStreamWithProgress], which reads the file size and name (for the message), and
 * also opens the stream for you.
 *
 * Completion occurs when the stream receives an end-of-stream return code (-1) from calling `stream.read` or is closed. Reading
 * more bytes than were expected just results in new progress reports with a new higher value for [ProgressReport.getExpectedTotal].
 *
 * @param callback The callback to invoke with progress reports. If null, returns 'this'
 * @param progressMessage The message that will be sent in the progress reports.
 * @return the newly wrapped stream or 'this' if callback is null.
 */
fun InputStream.withProgress(
    callback: ProgressReport.Tracker?,
    progressMessage: String
): InputStream = withProgress(callback, ProgressReport.createIndeterminate(progressMessage))

/**
 * Returns a tracked input stream initialized with the size of the file, byte units and if no base report is specified,
 * the file name as the message.
 *
 * @see InputStream.withProgress
 */
@JvmOverloads
fun Path.inputStreamWithProgress(
    callback: ProgressReport.Tracker?,
    message: String? = null
): InputStream {
    if (callback == null) return inputStream()
    val size = fileSize()
    if (size == 0L)
        return inputStream()
    val report = ProgressReport.create(message ?: fileName.toString(), size, 0, ProgressReport.Units.BYTES)
    return inputStream().withProgress(callback, report)
}

/**
 * Returns a progress tracking iterator for the array that reports for each item iterated.
 *
 * @param tracker A callback that receives progress reports on the same thread as that which calls the iterator, or null if
 * progress reports should be discarded.
 * @param baseReport The report that will be incremented, defaulting to the size of the array.
 */
@JvmOverloads
fun <T> Array<T>.withProgress(
    tracker: ProgressReport.Tracker?,
    baseReport: ProgressReport = ProgressReport.create(null, size.toLong().coerceAtLeast(1))
): Iterator<T> = if (isEmpty()) iterator() else IteratorWithProgress(iterator(), tracker, baseReport)

/**
 * Returns a progress tracking iterator for the array that reports for each item iterated.
 *
 * @param tracker A callback that receives progress reports on the same thread as that which calls the iterator, or null if
 * progress reports should be discarded.
 * @param progressMessage The message that will be sent in the progress reports.
 */
fun <T> Array<T>.withProgress(
    tracker: ProgressReport.Tracker?,
    progressMessage: String
): Iterator<T> = if (isEmpty()) iterator() else IteratorWithProgress(
    iterator(), tracker,
    ProgressReport.create(progressMessage, size.toLong())
)

/**
 * Sends a progress report for each item that is iterated. Initialize [baseReport] with the expected size of the iterator
 * for best effect. The returned iterator object can be cast to [AutoCloseable] and will close the underlying iterator if
 * `close` is called.
 *
 * @param tracker A callback that receives progress reports on the same thread as that which calls the iterator, or null if
 * progress reports should be discarded.
 * @param baseReport The report that will be incremented, defaulting to an indeterminate report.
 */
@JvmOverloads
fun <T> Iterator<T>.withProgress(
    tracker: ProgressReport.Tracker?,
    baseReport: ProgressReport = ProgressReport.createIndeterminate()
): CloseableIterator<T> = IteratorWithProgress(this, tracker, baseReport)

/**
 * Sends a progress report for each item that is iterated. The returned iterator object can be cast to [AutoCloseable] and will close the
 * underlying iterator if `close` is called.
 *
 * @param tracker A callback that receives progress reports on the same thread as that which calls the iterator, or null if
 * progress reports should be discarded.
 * @param progressMessage The message that will be sent in the progress reports.
 */
fun <T> Iterator<T>.withProgress(
    tracker: ProgressReport.Tracker?,
    progressMessage: String
): CloseableIterator<T> = IteratorWithProgress(this, tracker, ProgressReport.createIndeterminate(progressMessage))

/**
 * Sends a set of progress reports for each item iterated over from the given [Iterable]. Because a stream of progress reports
 * cannot start again after completion, the returned iterable may only be iterated over once regardless of the repeatability of the
 * underlying iterable. If [baseReport] doesn't specify an expected size an attempt is made to calculate the size of the underlying
 * structure being iterated over; this works for the built-in Java collections in most reasonable cases.
 *
 * @param tracker A callback that receives progress reports on the same thread as that which calls the iterator, or null if
 * progress reports should be discarded.
 * @param baseReport The report that will be incremented, defaulting to an indeterminate report.
 */
@JvmOverloads
fun <T> Iterable<T>.withProgress(
    tracker: ProgressReport.Tracker?,
    baseReport: ProgressReport = ProgressReport.createIndeterminate()
): Iterable<T> {
    if (tracker == null)
        return this

    val (size, iterator: Iterator<T>) = iteratorAndSize()      // size can be -1 for unknown

    if (size == 0L)
        return this

    var report: ProgressReport = baseReport
    if (baseReport.indeterminate && size > 0)
        report = baseReport.withExpectedTotal(size)

    return object : Iterable<T> {
        private var consumed = false
        override fun iterator(): CloseableIterator<T> {
            check(!consumed)
            consumed = true
            return iterator.withProgress(tracker, report)
        }
    }
}

/**
 * Sends a set of progress reports for each item iterated over from the given [Iterable]. Because a stream of progress reports
 * cannot start again after completion, the returned iterable may only be iterated over once regardless of the repeatability of the
 * underlying iterable. An attempt is made to calculate the size of the underlying  structure being iterated over;
 * this works for the built-in Java collections in most reasonable cases.
 *
 * @param tracker A callback that receives progress reports on the same thread as that which calls the iterator, or null if
 * progress reports should be discarded.
 * @param progressMessage The message that will be sent in the progress reports.
 */
fun <T> Iterable<T>.withProgress(
    tracker: ProgressReport.Tracker?,
    progressMessage: String
): Iterable<T> = withProgress(tracker, ProgressReport.createIndeterminate(progressMessage))

@Suppress("UNCHECKED_CAST")
private fun <T : Any?> Iterable<T>.iteratorAndSize(): Pair<Long, Iterator<T>> {
    return when (this) {
        is List<*> -> Pair(this.size.toLong(), this.iterator())

        // We add +1 here because the last element is inclusive. We don't try and incorporate the step.
        // That's OK though because it'll get averaged out when rendered as a progress bar anyway.
        is IntProgression -> Pair(this.last.toLong() - this.first.toLong() + 1, this.iterator() as Iterator<T>)
        is LongProgression -> Pair(this.last - this.first + 1, this.iterator() as Iterator<T>)
        is UIntProgression -> Pair(this.last.toLong() - this.first.toLong() + 1, this.iterator() as Iterator<T>)
        is ULongProgression -> Pair(this.last.toLong() - this.first.toLong() + 1, this.iterator() as Iterator<T>)

        else -> {
            val spliterator = spliterator()
            val size: Long = spliterator.exactSizeIfKnown
            val iterator = Spliterators.iterator(spliterator)
            Pair(size, iterator)
        }
    }
}

/**
 * Runs the given [action] serially over the receiver collection, tracking progress and passing it to the [tracker].
 *
 * The reports use the given [message], if any, the size of the collection as the expected total and abstract units.
 *
 * @param tracker A callback that receives progress reports on the same thread as that which calls the iterator, or null if
 * progress reports should be discarded.
 * @param message The message to include in the progress report.
 */
@JvmOverloads
fun <T> Collection<T>.forEachWithProgress(
    tracker: ProgressReport.Tracker?,
    message: String? = null,
    action: Consumer<T>
) {
    if (isEmpty())
        return
    val report = ProgressReport.create(message, size).mutableReport()
    try {
        for (item in this) {
            tracker?.report(report)
            action.accept(item)
            report.increment(1)
        }
    } finally {
        tracker?.report(report.withCompleted(report.expectedTotal))
    }
}

/**
 * Registers a listener on each future such that progress events are emitted on the given [tracker] (serially). Use this when you've
 * kicked off a bunch of tasks on a thread pool and want to track their completion.
 *
 * @param tracker the recipient of progress events, or if null, this call is a no-op.
 * @return this collection.
 */
fun <V> Collection<CompletableFuture<V>>.withProgressTrackedFutures(
    tracker: ProgressReport.Tracker?,
    baseReport: ProgressReport
): Collection<CompletableFuture<V>> {
    if (tracker == null || isEmpty())
        return this
    val emitter = ProgressEmitter(baseReport.immutableReport().withExpectedTotal(size.toLong()), tracker)
    return map {
        it.whenComplete { _, _ ->
            emitter.increment()
        }
    }
}

fun <V> Collection<CompletableFuture<V>>.withProgressTrackedFutures(tracker: ProgressReport.Tracker?, message: String): Collection<Future<V>> =
    this.withProgressTrackedFutures(tracker, ProgressReport.createIndeterminate(message))

/** An iterator that also be closed before iteration is completed. CloseableIterators auto-close themselves when done. */
interface CloseableIterator<T> : Iterator<T>, AutoCloseable

/**
 * An [AutoCloseable] iterator that wraps another and emits progress reports. On construction the base report is issued.
 */
private class IteratorWithProgress<T>(
    private val tracked: Iterator<T>,
    tracker: ProgressReport.Tracker?,
    baseReport: ProgressReport
) : CloseableIterator<T> {
    private val baseReport = baseReport.mutableReport()
    private val shouldSetMessage = baseReport.message == null
    private val cb: ProgressReport.Tracker = ProgressTrackerInvoker.wrap(tracker ?: IgnoreProgress)
    private var sentCompletion = false

    init {
        cb.report(baseReport)
    }

    override fun hasNext(): Boolean {
        val n = tracked.hasNext()
        if (!n && !sentCompletion) {
            // We may send a completion event here because it's possible the iterator exhausted itself before our estimate
            // of the size was reached, but we still want to inform progress listeners that we're done.
            sentCompletion = true
            baseReport.completed = baseReport.expectedTotal
            cb.report(baseReport)
        }
        return n
    }

    override fun next(): T {
        val n = tracked.next()
        if (!baseReport.indeterminate) {
            baseReport.increment(1)
            if (n is Path && shouldSetMessage)
                baseReport.message = n.name
            if (!baseReport.complete) {
                cb.report(baseReport)
            } else if (!sentCompletion) {
                sentCompletion = true
                cb.report(baseReport)
            }
        }
        return n
    }

    override fun close() {
        (tracked as? Closeable)?.close()
        if (!sentCompletion) {
            baseReport.completed = baseReport.expectedTotal
            cb.report(baseReport)
            sentCompletion = true
        }
    }
}

private class InputStreamFilter(
    stream: InputStream,
    callback: ProgressReport.Tracker,
    private val baseReport: ProgressReport
) : FilterInputStream(stream) {
    private val callback = ProgressTrackerInvoker.wrap(callback)
    private val read = AtomicLong()

    private fun didRead(bytes: Long) {
        val newVal = read.addAndGet(bytes)
        var rep = baseReport
        // If our estimate was bad, just keep bumping it up to match, so we sit at 99% until done.
        val givenSize = baseReport.expectedTotal
        if (givenSize < newVal)
            rep = rep.withExpectedTotal(newVal + 1)
        rep = rep.withIncremented(newVal)

        callback.report(rep)
    }

    @Throws(IOException::class)
    override fun read(): Int = `in`.read().also { if (it != -1) didRead(1) }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int = `in`.read(b).also { if (it != -1) didRead(it.toLong()) }

    @Throws(IOException::class)
    override fun read(
        b: ByteArray,
        off: Int,
        len: Int
    ): Int = `in`.read(b, off, len).also { if (it != -1) didRead(it.toLong()) }

    @Throws(IOException::class)
    override fun close() {
        `in`.close()
    }
}
