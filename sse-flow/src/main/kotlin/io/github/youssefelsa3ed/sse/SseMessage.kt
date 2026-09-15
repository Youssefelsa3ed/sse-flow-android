package io.github.youssefelsa3ed.sse

/**
 * One parsed SSE event: everything between two blank lines in the raw `text/event-stream` body.
 *
 * [event] is `null` for servers that only ever send `data:` lines - the `event:` field is only
 * present on streams that distinguish multiple message types on the same connection.
 */
data class SseMessage(
    val id: String? = null,
    val event: String? = null,
    val data: String? = null
)
