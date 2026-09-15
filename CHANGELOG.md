# Changelog

All notable changes to this project are documented in this file.

## [0.1.0] - Unreleased

### Added

- `SseConnectionManager<T>`: manages the lifecycle of a `Flow<T>`-based stream (start, retry,
  close) using an internally-owned coroutine scope.
- `SseRetryPolicy`: `Bounded` (fixed delay, capped attempts, optional early stop) and `Infinite`
  (exponential backoff with a cap) reconnect strategies.
- `SseMessage`: parsed representation of one SSE event (`id`, `event`, `data`).
- `sseFlow { ... }` / `ResponseBody.toSseMessageFlow()`: parses a Retrofit `@Streaming`
  `text/event-stream` response into a cold `Flow<SseMessage>`.

Extracted from a production Android app's internal SSE handling package.
