package io.nightfish.lightnovelreader.plugin.linovelib.source

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

internal class LinovelibRequestCoordinator(
    private val minimumRequestIntervalMillis: Long = LinovelibRequestPolicy.minimumRequestIntervalMillis,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private var lastRequestStartedAtMillis = 0L
    private var blockedUntilMillis = 0L

    suspend fun <T> execute(block: suspend () -> T): T {
        mutex.lock()
        try {
            val now = clock()
            val intervalDelay = LinovelibRequestPolicy.requestDelayMillis(
                lastRequestStartedAtMillis,
                now,
                minimumRequestIntervalMillis
            )
            val cooldownDelay = (blockedUntilMillis - now).coerceAtLeast(0L)
            val waitMillis = maxOf(intervalDelay, cooldownDelay)
            if (waitMillis > 0L) delay(waitMillis)
            currentCoroutineContext().ensureActive()
            if (blockedUntilMillis <= clock()) blockedUntilMillis = 0L
            lastRequestStartedAtMillis = clock()
            return block()
        } finally {
            mutex.unlock()
        }
    }

    suspend fun scheduleCooldown(delayMillis: Long) {
        mutex.lock()
        try {
            blockedUntilMillis = maxOf(blockedUntilMillis, clock() + delayMillis)
        } finally {
            mutex.unlock()
        }
    }
}
