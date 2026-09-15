# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### Changed

- **Breaking:** `sseFlow` now takes `suspend () -> okhttp3.Response` instead of
  `suspend () -> retrofit2.Response<ResponseBody>`, and throws `SseHttpException` instead of
  `retrofit2.HttpException` on a non-successful response. The library no longer depends on
  Retrofit at all - only OkHttp. Retrofit users can adapt with `.raw()` on their existing
  `@Streaming` endpoint response (see README).

## [0.1.0]

### Added

- `SseConnectionManager<T>`: manages the lifecycle of a `Flow<T>`-based stream (start, retry,
  close) using an internally-owned coroutine scope.
- `SseRetryPolicy`: `Bounded` (fixed delay, capped attempts, optional early stop) and `Infinite`
  (exponential backoff with a cap) reconnect strategies.
- `SseMessage`: parsed representation of one SSE event (`id`, `event`, `data`).
- `sseFlow { ... }` / `ResponseBody.toSseMessageFlow()`: parses a `text/event-stream` response
  into a cold `Flow<SseMessage>`.

Extracted from a production Android app's internal SSE handling package.
