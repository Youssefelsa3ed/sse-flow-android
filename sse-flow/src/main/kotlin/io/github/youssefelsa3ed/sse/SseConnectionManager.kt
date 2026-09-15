package io.github.youssefelsa3ed.sse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drives the lifecycle of a single SSE stream: starts collecting a [Flow] of events, retries it
 * per a [SseRetryPolicy] when it fails, and reports terminal errors and completion.
 *
 * Owns its own internal [CoroutineScope] so callers don't need to create/manage one just to use
 * this class. [connect] can safely be called more than once on the same instance (e.g. to
 * reconnect with a different URL/stream) - only the running job is canceled each time, never the
 * scope itself, since a cancelled [CoroutineScope] can never launch another coroutine again.
 *
 * This class is transport-agnostic: it does not know anything about HTTP or the SSE wire format.
 * Pair it with [sseFlow] (or your own `Flow<T>`) to get a complete client.
 */
class SseConnectionManager<T> {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    /** Number of reconnect attempts made for the current [connect] call so far. */
    var currentAttempt: Int = 0
        private set

    /** True while a stream is currently connected/retrying; false once closed or completed. */
    val isActive: Boolean
        get() = job?.isActive == true

    /**
     * Starts collecting [stream], applying [retryPolicy] whenever it throws.
     *
     * @param stream a cold [Flow] that performs the actual connection when collected - for HTTP-based
     * SSE, build it so the request itself happens inside the flow (see [sseFlow]), otherwise a retry
     * will simply replay a response that has already failed instead of issuing a new request.
     * @param onMessage called on every emitted event.
     * @param onRetry called right before a retry attempt is evaluated, with the [Throwable] that caused it.
     * @param onError called once retries are exhausted (or [SseRetryPolicy.shouldRetry] returns false).
     * @param onStart called when the stream starts collecting, including on every reconnect.
     * @param onCompleted called when the underlying flow finishes for any reason: normal completion,
     * an unretried error, or [close].
     */
    fun connect(
        stream: Flow<T>,
        retryPolicy: SseRetryPolicy,
        onMessage: (T) -> Unit,
        onRetry: ((Throwable) -> Unit)? = null,
        onError: (Throwable) -> Unit = {},
        onStart: () -> Unit = {},
        onCompleted: () -> Unit = {}
    ) {
        job?.cancel()
        currentAttempt = 0
        job = scope.launch {
            stream
                .onStart { onStart() }
                .retryWhen { cause, attempt ->
                    currentAttempt = attempt.toInt() + 1
                    onRetry?.invoke(cause)
                    retryPolicy.shouldRetry(currentAttempt, cause).also { retry ->
                        if (retry) delay(retryPolicy.delayMillisFor(currentAttempt).milliseconds)
                    }
                }
                .catch { onError(it) }
                .collect { onMessage(it) }
        }.also { it.invokeOnCompletion { onCompleted() } }
    }

    /** Cancels the current stream, if any. Safe to call even if nothing is connected. */
    fun close() {
        job?.cancel()
        job = null
    }
}
