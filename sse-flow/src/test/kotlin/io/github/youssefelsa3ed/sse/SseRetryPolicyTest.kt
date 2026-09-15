package io.github.youssefelsa3ed.sse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SseRetryPolicyTest {

    @Test
    fun `bounded policy retries up to maxRetries then stops`() {
        val policy = SseRetryPolicy.Bounded(maxRetries = 3)

        assertTrue(policy.shouldRetry(1, RuntimeException()))
        assertTrue(policy.shouldRetry(3, RuntimeException()))
        assertFalse(policy.shouldRetry(4, RuntimeException()))
    }

    @Test
    fun `bounded policy honors stopEarly regardless of attempt count`() {
        val policy = SseRetryPolicy.Bounded(maxRetries = 5, stopEarly = { true })

        assertFalse(policy.shouldRetry(1, RuntimeException()))
    }

    @Test
    fun `bounded policy uses a fixed delay`() {
        val policy = SseRetryPolicy.Bounded(maxRetries = 5, delayMillis = 250)

        assertEquals(250, policy.delayMillisFor(1))
        assertEquals(250, policy.delayMillisFor(5))
    }

    @Test
    fun `infinite policy always retries`() {
        val policy = SseRetryPolicy.Infinite()

        assertTrue(policy.shouldRetry(1_000, RuntimeException()))
    }

    @Test
    fun `infinite policy backs off exponentially up to the cap`() {
        val policy = SseRetryPolicy.Infinite(
            initialDelayMillis = 1_000,
            maxDelayMillis = 8_000,
            factor = 2.0
        )

        assertEquals(1_000, policy.delayMillisFor(1))
        assertEquals(2_000, policy.delayMillisFor(2))
        assertEquals(4_000, policy.delayMillisFor(3))
        assertEquals(8_000, policy.delayMillisFor(4))
        assertEquals(8_000, policy.delayMillisFor(10)) // capped
    }
}
