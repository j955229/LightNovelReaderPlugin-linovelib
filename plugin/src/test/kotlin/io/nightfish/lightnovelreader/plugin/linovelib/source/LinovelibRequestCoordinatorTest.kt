package io.nightfish.lightnovelreader.plugin.linovelib.source

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LinovelibRequestCoordinatorTest {
    @Test
    fun `does not link coroutine mutex unavailable in the host app`() {
        val classBytes = checkNotNull(
            LinovelibRequestCoordinator::class.java.getResourceAsStream("LinovelibRequestCoordinator.class")
        ).use { it.readBytes() }

        assertFalse(
            String(classBytes, Charsets.ISO_8859_1)
                .contains("kotlinx/coroutines/sync/Mutex")
        )
    }

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

    @Test
    fun `honors cooldown scheduled while waiting`() = runBlocking {
        val coordinator = LinovelibRequestCoordinator(minimumRequestIntervalMillis = 100)
        coordinator.execute { Unit }
        val startedAt = System.currentTimeMillis()
        val enteredAt = CompletableDeferred<Long>()
        val waiting = launch {
            coordinator.execute { enteredAt.complete(System.currentTimeMillis()) }
        }

        delay(30)
        coordinator.scheduleCooldown(200)
        val elapsed = withTimeout(1_000) { enteredAt.await() } - startedAt
        waiting.join()

        assertTrue("Request entered after only ${elapsed}ms", elapsed >= 190)
    }

    @Test
    fun `registers result cooldown before releasing next request`() = runBlocking {
        val coordinator = LinovelibRequestCoordinator(minimumRequestIntervalMillis = 0)
        coordinator.execute(cooldownAfter = { 120L }) { Unit }
        val startedAt = System.currentTimeMillis()

        coordinator.execute { Unit }

        val elapsed = System.currentTimeMillis() - startedAt
        assertTrue("Next request entered after only ${elapsed}ms", elapsed >= 100)
    }

    @Test
    fun `serves queued requests in arrival order`() = runBlocking {
        val coordinator = LinovelibRequestCoordinator(minimumRequestIntervalMillis = 0)
        val releaseFirst = CompletableDeferred<Unit>()
        val first = launch { coordinator.execute { releaseFirst.await() } }
        delay(30)
        val order = mutableListOf<Int>()
        val waiting = (1..5).map { index ->
            launch { coordinator.execute { order += index } }.also { yield() }
        }

        releaseFirst.complete(Unit)
        first.join()
        waiting.forEach { it.join() }

        assertEquals((1..5).toList(), order)
    }

    @Test
    fun `releases request slot when block is cancelled`() = runBlocking {
        val coordinator = LinovelibRequestCoordinator(minimumRequestIntervalMillis = 0)
        val running = launch { coordinator.execute { awaitCancellation() } }
        delay(30)
        running.cancelAndJoin()

        var entered = false
        withTimeout(500) { coordinator.execute { entered = true } }

        assertTrue(entered)
    }

    @Test
    fun `releases request slot when block throws`() = runBlocking {
        val coordinator = LinovelibRequestCoordinator(minimumRequestIntervalMillis = 0)
        runCatching { coordinator.execute<Unit> { error("expected") } }

        var entered = false
        withTimeout(500) { coordinator.execute { entered = true } }

        assertTrue(entered)
    }
}
