package io.nightfish.lightnovelreader.plugin.linovelib.source

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class LinovelibRequestCoordinator(
    private val minimumRequestIntervalMillis: Long = LinovelibRequestPolicy.minimumRequestIntervalMillis,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val nextTicket = AtomicLong(0L)
    private val servingTicket = AtomicLong(0L)
    private val cancelledTickets = ConcurrentHashMap.newKeySet<Long>()
    private val ticketLock = Any()
    private val stateLock = Any()
    private var lastRequestStartedAtMillis = 0L
    private var blockedUntilMillis = 0L

    suspend fun <T> execute(
        cooldownAfter: (Result<T>) -> Long? = { null },
        block: suspend () -> T
    ): T {
        val ticket = acquireRequestSlot()
        try {
            awaitRequestWindow()
            val result = runCatching { block() }
            cooldownAfter(result)?.takeIf { it > 0L }?.let(::scheduleCooldown)
            return result.getOrThrow()
        } finally {
            releaseRequestSlot(ticket)
        }
    }

    fun scheduleCooldown(delayMillis: Long) {
        synchronized(stateLock) {
            blockedUntilMillis = maxOf(blockedUntilMillis, clock() + delayMillis)
        }
    }

    private suspend fun acquireRequestSlot(): Long {
        val ticket = nextTicket.getAndIncrement()
        var acquired = false
        try {
            while (servingTicket.get() != ticket) delay(REQUEST_SLOT_POLL_MILLIS)
            currentCoroutineContext().ensureActive()
            acquired = true
            return ticket
        } finally {
            if (!acquired) cancelTicket(ticket)
        }
    }

    private fun releaseRequestSlot(ticket: Long) = synchronized(ticketLock) {
        if (servingTicket.get() == ticket) servingTicket.incrementAndGet()
        skipCancelledTickets()
    }

    private fun cancelTicket(ticket: Long) = synchronized(ticketLock) {
        cancelledTickets += ticket
        skipCancelledTickets()
    }

    private fun skipCancelledTickets() {
        while (cancelledTickets.remove(servingTicket.get())) servingTicket.incrementAndGet()
    }

    private suspend fun awaitRequestWindow() {
        while (true) {
            val waitMillis = synchronized(stateLock) { requestWaitMillis(clock()) }
            if (waitMillis > 0L) {
                delay(waitMillis)
                continue
            }
            currentCoroutineContext().ensureActive()
            val reserved = synchronized(stateLock) {
                val startedAt = clock()
                if (requestWaitMillis(startedAt) > 0L) {
                    false
                } else {
                    if (blockedUntilMillis <= startedAt) blockedUntilMillis = 0L
                    lastRequestStartedAtMillis = startedAt
                    true
                }
            }
            if (reserved) return
        }
    }

    private fun requestWaitMillis(now: Long): Long {
        val intervalDelay = LinovelibRequestPolicy.requestDelayMillis(
            lastRequestStartedAtMillis,
            now,
            minimumRequestIntervalMillis
        )
        val cooldownDelay = (blockedUntilMillis - now).coerceAtLeast(0L)
        return maxOf(intervalDelay, cooldownDelay)
    }

    private companion object {
        const val REQUEST_SLOT_POLL_MILLIS = 25L
    }
}
