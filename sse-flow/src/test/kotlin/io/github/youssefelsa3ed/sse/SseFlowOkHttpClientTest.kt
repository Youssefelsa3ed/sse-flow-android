package io.github.youssefelsa3ed.sse

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SseFlowOkHttpClientTest {

    private val server = MockWebServer()
    private val client = OkHttpClient()

    @BeforeTest
    fun start() = server.start()

    @AfterTest
    fun shutdown() = server.shutdown()

    @Test
    fun `builds a GET request with the expected SSE headers`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody("data: hello\n\n")
                .setHeader("Content-Type", "text/event-stream")
        )

        sseFlow(client, server.url("/stream").toString()).toList()

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("text/event-stream", recorded.getHeader("Accept"))
        assertEquals("no-cache", recorded.getHeader("Cache-Control"))
    }

    @Test
    fun `adds custom headers on top of the SSE defaults`() = runBlocking {
        server.enqueue(MockResponse().setBody("data: hello\n\n"))

        sseFlow(
            client,
            server.url("/stream").toString(),
            headers = Headers.Builder().add("Authorization", "Bearer token").build()
        ).toList()

        val recorded = server.takeRequest()
        assertEquals("Bearer token", recorded.getHeader("Authorization"))
        assertEquals("text/event-stream", recorded.getHeader("Accept"))
    }

    @Test
    fun `parses messages from the mock server response`() = runBlocking {
        server.enqueue(MockResponse().setBody("id: 1\ndata: hello\n\n"))

        val messages = sseFlow(client, server.url("/stream").toString()).toList()

        assertEquals(listOf(SseMessage(id = "1", data = "hello")), messages)
    }

    @Test
    fun `throws SseHttpException for a non-successful response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val exception = assertFailsWith<SseHttpException> {
            sseFlow(client, server.url("/stream").toString()).toList()
        }
        assertEquals(500, exception.code)
    }
}
