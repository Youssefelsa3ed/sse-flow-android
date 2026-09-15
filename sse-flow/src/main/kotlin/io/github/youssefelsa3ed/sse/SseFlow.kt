package io.github.youssefelsa3ed.sse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

/**
 * Issues a `GET` request for [url] through [client] and turns its `text/event-stream` body into a
 * cold [Flow] of [SseMessage]. A correct request is built for you, with the `Accept` and
 * `Cache-Control` headers an SSE endpoint expects - [headers] are added on top (and can override
 * either default by supplying the same header name).
 *
 * ```
 * val stream: Flow<SseMessage> = sseFlow(okHttpClient, url)
 * ```
 *
 * Reuse your app's existing [OkHttpClient] (the one with your auth/logging interceptors already
 * configured) rather than creating a one-off client just for this call. If you need a different
 * HTTP method, a request body, or anything else this overload doesn't expose, use the
 * [sseFlow] overload that takes a request lambda instead.
 *
 * Throws [SseHttpException] if the response is not successful. Completes normally when the server
 * closes the connection.
 */
fun sseFlow(client: OkHttpClient, url: String, headers: Headers = Headers.headersOf()): Flow<SseMessage> =
    sseFlow {
        val requestHeaders = Headers.Builder()
            .add("Accept", "text/event-stream")
            .add("Cache-Control", "no-cache")
            .apply { headers.forEach { (name, value) -> add(name, value) } }
            .build()
        val request = Request.Builder().url(url).headers(requestHeaders).build()
        runInterruptible { client.newCall(request).execute() }
    }

/**
 * Issues an HTTP request and turns its `text/event-stream` body into a cold [Flow] of [SseMessage].
 *
 * [request] is only invoked when the returned flow is collected, and again on every reconnect
 * driven by [SseConnectionManager] - this is what lets a retry issue a brand-new HTTP request
 * instead of replaying an already-failed response. Prefer the [sseFlow] overload that takes an
 * [OkHttpClient] and a URL directly unless you need custom request building (a different method,
 * a request body, headers beyond what that overload exposes, ...):
 *
 * ```
 * val stream: Flow<SseMessage> = sseFlow {
 *     val request = Request.Builder()
 *         .url(url)
 *         .header("Accept", "text/event-stream")
 *         .build()
 *     okHttpClient.newCall(request).execute()
 * }
 * ```
 *
 * If you already use Retrofit for the rest of your API and declared a `@Streaming` endpoint
 * returning `Response<ResponseBody>`, unwrap it with `.raw()` to get the `okhttp3.Response` this
 * function expects:
 *
 * ```
 * interface ApiService {
 *     @Streaming
 *     @GET
 *     suspend fun streamResults(@Url url: String): retrofit2.Response<ResponseBody>
 * }
 *
 * val stream: Flow<SseMessage> = sseFlow { apiService.streamResults(url).raw() }
 * ```
 *
 * Throws [SseHttpException] if the response is not successful. Completes normally when the server
 * closes the connection.
 */
fun sseFlow(request: suspend () -> Response): Flow<SseMessage> = flow {
    val response = request()
    if (!response.isSuccessful) throw SseHttpException(response)
    response.body.parseSseMessagesInto(this)
}.flowOn(Dispatchers.IO)

/**
 * Parses this [ResponseBody] as an SSE (`text/event-stream`) wire format into a cold [Flow] of
 * [SseMessage], closing the body once the flow completes or is cancelled.
 *
 * Prefer [sseFlow] when you also own the request: collecting this flow does not repeat the HTTP
 * call, so retrying it (e.g. via [SseConnectionManager]) will just replay - and fail on - the same
 * already-consumed body. Use this overload directly only when you need a [ResponseBody] you
 * already have parsed once, non-retryably (e.g. in a test, or a one-shot read).
 */
fun ResponseBody.toSseMessageFlow(): Flow<SseMessage> = flow {
    parseSseMessagesInto(this)
}.flowOn(Dispatchers.IO)

private suspend fun ResponseBody.parseSseMessagesInto(
    collector: FlowCollector<SseMessage>
) {
    source().use { source ->
        var id: String? = null
        var event: String? = null
        var data: String? = null

        while (runInterruptible { !source.exhausted() }) { // while there's data to read
            val line = source.readUtf8Line() ?: break

            when {
                line.startsWith("id:") -> id = line.removePrefix("id:").trim()
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith("data:") -> data = line.removePrefix("data:").trim()
                line.isEmpty() -> {
                    // blank line = complete event
                    if (event != null || data != null || id != null) {
                        collector.emit(SseMessage(id, event, data))
                    }
                    id = null
                    event = null
                    data = null
                }
            }
        }
    }
}
