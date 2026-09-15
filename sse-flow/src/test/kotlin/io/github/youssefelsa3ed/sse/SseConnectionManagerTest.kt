package io.github.youssefelsa3ed.sse

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SseConnectionManagerTest {

    @Test
    fun `delivers every emitted message in order`() = runBlocking {
        val manager = SseConnectionManager<Int>()
        val received = mutableListOf<Int>()
        val completed = CompletableDeferred<Unit>()

        manager.connect(
            stream = flow {
                emit(1)
                emit(2)
                emit(3)
            },
            retryPolicy = SseRetryPolicy.Bounded(maxRetries = 0),
            onMessage = { received += it },
            onCompleted = { completed.complete(Unit) }
        )

        withTimeout(5_000) { completed.await() }
        assertEquals(listOf(1, 2, 3), received)
    }

    @Test
    fun `retries a failing stream according to the retry policy`() = runBlocking {
        val manager = SseConnectionManager<Int>()
        var attempts = 0
        val errors = mutableListOf<Throwable>()
        val completed = CompletableDeferred<Unit>()

        manager.connect(
            stream = flow {
                attempts++
                throw RuntimeException("boom $attempts")
            },
            retryPolicy = SseRetryPolicy.Bounded(maxRetries = 2, delayMillis = 10),
            onMessage = {},
            onError = {
                errors += it
                completed.complete(Unit)
            }
        )

        withTimeout(5_000) { completed.await() }
        assertEquals(3, attempts) // initial attempt + 2 retries
        assertEquals(1, errors.size)
    }

    @Test
    fun `close cancels the active stream`() = runBlocking {
        val manager = SseConnectionManager<Int>()
        val started = CompletableDeferred<Unit>()

        manager.connect(
            stream = flow {
                started.complete(Unit)
                delay(60_000) // never completes on its own
                emit(1)
            },
            retryPolicy = SseRetryPolicy.Bounded(maxRetries = 0),
            onMessage = {}
        )

        withTimeout(5_000) { started.await() }
        assertTrue(manager.isActive)

        manager.close()
        delay(100) // let cancellation propagate
        assertFalse(manager.isActive)
    }

    @Test
    fun `connect can be called again to reconnect without reusing a cancelled scope`() = runBlocking {
        val manager = SseConnectionManager<Int>()
        val firstCompleted = CompletableDeferred<Unit>()

        manager.connect(
            stream = flow { emit(1) },
            retryPolicy = SseRetryPolicy.Bounded(maxRetries = 0),
            onMessage = {},
            onCompleted = { firstCompleted.complete(Unit) }
        )
        withTimeout(5_000) { firstCompleted.await() }

        val received = mutableListOf<Int>()
        val secondCompleted = CompletableDeferred<Unit>()
        manager.connect(
            stream = flow { emit(2) },
            retryPolicy = SseRetryPolicy.Bounded(maxRetries = 0),
            onMessage = { received += it },
            onCompleted = { secondCompleted.complete(Unit) }
        )
        withTimeout(5_000) { secondCompleted.await() }

        assertEquals(listOf(2), received)
    }
}
