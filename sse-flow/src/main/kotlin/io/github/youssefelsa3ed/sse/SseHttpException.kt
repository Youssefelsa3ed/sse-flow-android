package io.github.youssefelsa3ed.sse

import okhttp3.Response

/**
 * Thrown by [sseFlow] when the HTTP response's status code is outside the 200..299 range.
 */
class SseHttpException(val response: Response) :
    RuntimeException("HTTP ${response.code}: ${response.message}") {

    val code: Int get() = response.code
}
