package dev.progress4j.utils

import dev.progress4j.api.ProgressReport
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.fail

@Suppress("ControlFlowWithEmptyBody")
class ProgressGeneratorsTest {
    @Test
    fun sequence() {
        var c = 0
        val results = ArrayList<ProgressReport>()
        // Progress framework can't see through the lambda to know the size, so it's indeterminate.
        generateSequence { if (c == 10) null else c++ }.asIterable().withProgress({ progress -> results.add(progress.immutableReport()) })
            .sum()
        assertEquals(listOf<ProgressReport>(
            ProgressReport.createIndeterminate().withExpectedTotal(1),
            ProgressReport.createIndeterminate().withExpectedTotal(1).withCompleted(1)
        ), results.map { it.immutableReport() })
    }

    @Test
    fun range() {
        val results = ArrayList<ProgressReport>()
        for (i in (1..5).withProgress({ results.add(it.immutableReport()) })) {
        }
        assertEquals(
            listOf<ProgressReport>(
                ProgressReport.createIndeterminate().withExpectedTotal(5).withCompleted(0),
                ProgressReport.createIndeterminate().withExpectedTotal(5).withCompleted(1),
                ProgressReport.createIndeterminate().withExpectedTotal(5).withCompleted(2),
                ProgressReport.createIndeterminate().withExpectedTotal(5).withCompleted(3),
                ProgressReport.createIndeterminate().withExpectedTotal(5).withCompleted(4),
                ProgressReport.createIndeterminate().withExpectedTotal(5).withCompleted(5),
            ),
            results.map { it.immutableReport() }
        )
    }

    @Test
    fun list() {
        val results = ArrayList<ProgressReport>()
        for (i in listOf(1, 2, 3).withProgress({ results.add(it.immutableReport()) })) {
        }
        assertEquals(listOf<ProgressReport>(
            ProgressReport.createIndeterminate().withExpectedTotal(3),
            ProgressReport.createIndeterminate().withExpectedTotal(3).withCompleted(1),
            ProgressReport.createIndeterminate().withExpectedTotal(3).withCompleted(2),
            ProgressReport.createIndeterminate().withExpectedTotal(3).withCompleted(3),
        ), results.map { it.immutableReport() }
        )
    }

    @Test
    fun futures() {
        val futures = mutableListOf<CompletableFuture<Int>>()
        val expected = listOf(1, 2, 3, 4)
        for (i in expected) {
            futures.add(CompletableFuture.supplyAsync { i })
        }

        val results = ArrayList<ProgressReport>()
        val actual = mutableListOf<Int>()
        futures.withProgressTrackedFutures({ results.add(it.immutableReport()) }, "testing").forEach { actual.add(it.get()) }
        assertEquals(actual, expected)
        assertEquals(listOf(
            ProgressReport.create("testing", 4, 0),
            ProgressReport.create("testing", 4, 1),
            ProgressReport.create("testing", 4, 2),
            ProgressReport.create("testing", 4, 3),
            ProgressReport.create("testing", 4, 4),
        ), results.map { it.immutableReport() })
    }

    @Test
    fun emptyFutures() {
        val futures = listOf<CompletableFuture<Int>>()
        futures.withProgressTrackedFutures({ fail("Should not have any progress") }, "testing")
            .forEach { _ -> fail("Should not have any items") }
    }
}
