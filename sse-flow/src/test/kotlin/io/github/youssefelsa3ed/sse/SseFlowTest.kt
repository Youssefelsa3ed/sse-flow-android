package io.github.youssefelsa3ed.sse

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SseFlowTest {

    private val mediaType = "text/event-stream".toMediaType()

    @Test
    fun `parses multiple events separated by blank lines`() = runBlocking {
        val raw = """
            id: 1
            event: message
            data: {"a":1}

            data: no-id-or-event

            id: 3
            data: last

        """.trimIndent() + "\n"

        val messages = raw.toResponseBody(mediaType).toSseMessageFlow().toList()

        assertEquals(
            listOf(
                SseMessage(id = "1", event = "message", data = """{"a":1}"""),
                SseMessage(id = null, event = null, data = "no-id-or-event"),
                SseMessage(id = "3", event = null, data = "last")
            ),
            messages
        )
    }

    @Test
    fun `ignores a trailing incomplete event with no blank line`() = runBlocking {
        val raw = "data: only-event-without-trailing-blank-line"

        val messages = raw.toResponseBody(mediaType).toSseMessageFlow().toList()

        assertEquals(emptyList(), messages)
    }

    @Test
    fun `sseFlow emits parsed messages from a successful response`() = runBlocking {
        val raw = "data: hello\n\n"
        val response = Response.success(raw.toResponseBody(mediaType))

        val messages = sseFlow { response }.toList()

        assertEquals(listOf(SseMessage(data = "hello")), messages)
    }

    @Test
    fun `sseFlow throws HttpException for a non-successful response`() = runBlocking {
        val errorResponse = Response.error<okhttp3.ResponseBody>(
            404,
            "not found".toResponseBody("text/plain".toMediaType())
        )

        assertFailsWith<HttpException> {
            sseFlow { errorResponse }.toList()
        }
    }
}
