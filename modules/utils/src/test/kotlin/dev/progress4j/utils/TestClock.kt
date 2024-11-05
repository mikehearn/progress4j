package dev.progress4j.utils

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAmount

/**
 * A clock for unit testing that returns a stream of instants that increment by the given duration each time a time is requested.
 * Note that this is different to the clocks returned by [Clock.tick], because those are immutable. This is a _mutable_ clock which
 * therefore acts more similarly to a normal clock. After being provided to a class under test, either it may advance automatically
 * (by [tickSize]) or you may manually advance it using [tick].
 *
 * @param startingAt The base time the clock will start ticking at. Defaults to the current time.
 * @param tickSize How much the clock will advance each time [instant] is called. Defaults to 1 second.
 * @param zone The clock's time zone. Defaults to the system default.
 */
class TestClock @JvmOverloads constructor(
    startingAt: Instant = Instant.now(),
    private val tickSize: TemporalAmount = Duration.ofSeconds(1),
    private val zone: ZoneId = ZoneId.systemDefault()
) : Clock() {
    private var time: Instant = startingAt

    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = TestClock(time, tickSize, zone)

    /** Advances the clock by the given amount. If you want to advance by [tickSize] just call [instant] and discard the result. */
    @Synchronized
    fun tick(by: TemporalAmount) {
        time = time.plus(by)
    }

    /** Returns the time on the clock and then advances it. */
    @Synchronized
    override fun instant(): Instant {
        val r = time
        time = time.plus(tickSize)
        return r
    }
}
