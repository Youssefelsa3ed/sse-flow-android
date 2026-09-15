package io.github.youssefelsa3ed.sse

import kotlin.math.pow

/**
 * Decides whether/how long to wait before reconnecting an SSE stream after a failure.
 * Used by [SseConnectionManager] to provide configurable reconnect behavior out of the box.
 */
sealed interface SseRetryPolicy {
    /** @param attempt 1-based index of the retry about to be attempted. */
    fun shouldRetry(attempt: Int, cause: Throwable): Boolean
    fun delayMillisFor(attempt: Int): Long

    /**
     * Bounded retries with a fixed delay. Good fit for a stream that is expected to terminate on
     * its own (e.g. a search results stream that closes once results are final) and should stop
     * being retried once [stopEarly] reports the stream is done.
     */
    data class Bounded(
        val maxRetries: Int,
        val delayMillis: Long = 1_500,
        val stopEarly: () -> Boolean = { false }
    ) : SseRetryPolicy {
        override fun shouldRetry(attempt: Int, cause: Throwable) = attempt <= maxRetries && !stopEarly()
        override fun delayMillisFor(attempt: Int) = delayMillis
    }

    /**
     * Retries forever with capped exponential backoff. Good fit for a long-lived background
     * stream (e.g. a live notifications or gameplay channel) that should keep trying to reconnect
     * indefinitely.
     */
    data class Infinite(
        val initialDelayMillis: Long = 1_000,
        val maxDelayMillis: Long = 10_000,
        val factor: Double = 2.0
    ) : SseRetryPolicy {
        override fun shouldRetry(attempt: Int, cause: Throwable) = true
        override fun delayMillisFor(attempt: Int) =
            (initialDelayMillis * factor.pow(attempt - 1)).toLong().coerceAtMost(maxDelayMillis)
    }
}
