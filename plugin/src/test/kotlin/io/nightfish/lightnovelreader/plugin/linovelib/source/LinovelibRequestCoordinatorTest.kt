package io.nightfish.lightnovelreader.plugin.linovelib.source

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LinovelibRequestCoordinatorTest {
    @Test
    fun `serializes request blocks`() = runBlocking {
        val coordinator = LinovelibRequestCoordinator(minimumRequestIntervalMillis = 0)
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)

        val jobs = List(4) {
            launch {
                coordinator.execute {
                    val count = active.incrementAndGet()
                    maximum.updateAndGet { current -> maxOf(current, count) }
                    delay(20)
                    active.decrementAndGet()
                }
            }
        }
        jobs.forEach { it.join() }

        assertEquals(1, maximum.get())
    }

    @Test
    fun `cancels while waiting for request interval`() = runBlocking {
        val coordinator = LinovelibRequestCoordinator(minimumRequestIntervalMillis = 10_000)
        coordinator.execute { Unit }
        var entered = false
        val waiting = launch {
            coordinator.execute { entered = true }
        }

        delay(30)
        assertFalse(entered)
        withTimeout(500) { waiting.cancelAndJoin() }

        assertTrue(waiting.isCancelled)
        assertFalse(entered)
    }
}
