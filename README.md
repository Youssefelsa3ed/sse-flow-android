# sse-flow

[![Version](https://img.shields.io/github/v/tag/youssefelsa3ed/sse-flow-android?label=version)](https://github.com/youssefelsa3ed/sse-flow-android/releases)

A lightweight, `Flow`-based Server-Sent Events (SSE) client for Kotlin/Android, built on
[OkHttp](https://square.github.io/okhttp/).

It's built from three small, testable pieces:

- **[`SseConnectionManager`](sse-flow/src/main/kotlin/io/github/youssefelsa3ed/sse/SseConnectionManager.kt)** -
  owns the lifecycle (start/retry/close) of a stream using coroutines, so callers don't manage a
  `CoroutineScope` themselves.
- **[`SseRetryPolicy`](sse-flow/src/main/kotlin/io/github/youssefelsa3ed/sse/SseRetryPolicy.kt)** -
  pluggable reconnect behavior: bounded retries with a fixed delay, or infinite retries with
  exponential backoff.
- **[`sseFlow` / `SseMessage`](sse-flow/src/main/kotlin/io/github/youssefelsa3ed/sse/SseFlow.kt)** -
  turns an OkHttp streaming response into a cold `Flow<SseMessage>` by parsing the raw
  `text/event-stream` wire format.

The three pieces are independent - use just the parser, just the connection manager with your own
`Flow`, or all of them together.

## Installation

The library is published to **Maven Central**. No extra repository and no credentials - every
Gradle project already has `mavenCentral()` in its repositories by default, so all you add is the
dependency:

```kotlin
// app/build.gradle.kts (or wherever you make the network call)
dependencies {
    implementation("io.github.youssefelsa3ed:sse-flow:<version>")
}
```

## Quick start

### 1. Build a `Flow<SseMessage>` and connect

```kotlin
import io.github.youssefelsa3ed.sse.SseConnectionManager
import io.github.youssefelsa3ed.sse.SseMessage
import io.github.youssefelsa3ed.sse.SseRetryPolicy
import io.github.youssefelsa3ed.sse.sseFlow

class LiveUpdatesClient(private val okHttpClient: OkHttpClient) {

    private val connectionManager = SseConnectionManager<SseMessage>()

    fun start(url: String, onResult: (SseMessage) -> Unit, onFailed: (Throwable) -> Unit) {
        connectionManager.connect(
            stream = sseFlow(okHttpClient, url),
            retryPolicy = SseRetryPolicy.Bounded(maxRetries = 1),
            onMessage = onResult,
            onError = onFailed
        )
    }

    fun stop() = connectionManager.close()
}
```

`sseFlow(client, url)` builds a `GET` request with the `Accept`/`Cache-Control` headers an SSE
endpoint expects, and issues it through the `OkHttpClient` you pass in - reuse your app's existing
client (the one with your auth/logging interceptors already configured) rather than creating a
one-off one. Pass extra headers (e.g. `Authorization`) with the `headers` parameter.

If you need a different HTTP method, a request body, or anything else that overload doesn't
expose, drop to the lower-level `sseFlow { ... }` that takes a request lambda instead - see
[Custom requests](#custom-requests) below.

### 2. Choose a retry policy

```kotlin
// A search stream that is expected to close on its own once results are final.
SseRetryPolicy.Bounded(
    maxRetries = 1,
    delayMillis = 1_500,
    stopEarly = { searchIsAlreadyComplete }
)

// A long-lived background channel (notifications, live gameplay, ...) that should
// keep trying to reconnect indefinitely, backing off up to 10s between attempts.
SseRetryPolicy.Infinite(
    initialDelayMillis = 1_000,
    maxDelayMillis = 10_000,
    factor = 2.0
)
```

### 3. `connect` callbacks

| Callback | Called when |
|---|---|
| `onStart` | the stream (re)starts collecting - fires again on every reconnect |
| `onMessage` | every emitted `SseMessage` |
| `onRetry` | right before a retry attempt is evaluated, with the `Throwable` that caused it |
| `onError` | retries are exhausted, or `SseRetryPolicy.shouldRetry` returned `false` |
| `onCompleted` | the flow finishes for any reason: normal completion, an unretried error, or `close()` |

```kotlin
connectionManager.connect(
    stream = sseFlow(okHttpClient, url),
    retryPolicy = SseRetryPolicy.Bounded(maxRetries = 3),
    onStart = { Logger.log("SSE started") },
    onMessage = { message -> handle(message) },
    onRetry = { cause -> Logger.log("Retrying (attempt ${connectionManager.currentAttempt}): $cause") },
    onError = { cause -> Logger.logE(cause) },
    onCompleted = { Logger.log("SSE finished") }
)
```

### 4. Clean up

Call `close()` when the screen/viewmodel/manager that owns the stream is torn down (e.g.
`onCleared()` / `onDestroy()`). `SseConnectionManager` uses its own internal `SupervisorJob`-backed
`CoroutineScope`, so nothing leaks as long as `close()` is called - it does **not** tie itself to
Android's lifecycle automatically.

```kotlin
override fun onCleared() {
    connectionManager.close()
    super.onCleared()
}
```

`connect()` can safely be called again on the same `SseConnectionManager` instance (e.g. to start
a new search) - it cancels the previous stream first.

## Custom requests

When `sseFlow(client, url)` isn't enough - a different HTTP method, a request body, headers it
doesn't expose - use the lower-level `sseFlow { ... }` overload and build the `okhttp3.Request`
yourself:

```kotlin
val stream: Flow<SseMessage> = sseFlow {
    val request = Request.Builder()
        .url(url)
        .header("Accept", "text/event-stream")
        .header("Cache-Control", "no-cache")
        .build()
    okHttpClient.newCall(request).execute()
}
```

`request` is only invoked when the flow is collected, and again on every reconnect driven by
`SseConnectionManager` - this is what lets a retry issue a brand-new HTTP request instead of
replaying an already-failed response. **Always build your stream this way** (or via your own
`flow { ... }` builder) rather than passing an already-executed `okhttp3.Response`.

## Using only the parser

If you already manage your own coroutine scope/retry logic and just need the wire-format parsing:

```kotlin
import io.github.youssefelsa3ed.sse.toSseMessageFlow

val response: okhttp3.Response = call.execute()
val messages: Flow<SseMessage> = response.body.toSseMessageFlow()
```

Note this overload does not repeat the HTTP call on collection - see the KDoc on
[`toSseMessageFlow`](sse-flow/src/main/kotlin/io/github/youssefelsa3ed/sse/SseFlow.kt) for when
`sseFlow { ... }` is the better fit.

## Using only the connection manager

`SseConnectionManager<T>` is generic over the emitted type and has no knowledge of HTTP or the SSE
wire format - pair it with any `Flow<T>` (e.g. a WebSocket, gRPC stream, or your own parser):

```kotlin
val connectionManager = SseConnectionManager<MyEvent>()
connectionManager.connect(
    stream = myOwnEventFlow(),
    retryPolicy = SseRetryPolicy.Infinite(),
    onMessage = { event -> ... }
)
```

## `SseMessage`

```kotlin
data class SseMessage(
    val id: String? = null,
    val event: String? = null,
    val data: String? = null
)
```

One parsed SSE event: everything between two blank lines in the raw `text/event-stream` body.
`event` is `null` for servers that only ever send `data:` lines - only use it when your stream
distinguishes multiple message types on the same connection (e.g. `event: heartbeat` vs.
`event: result`).

## Publishing a new version

Releases are published to Maven Central automatically by
[`.github/workflows/publish.yml`](.github/workflows/publish.yml) whenever a tag matching `v*.*.*`
is pushed:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow derives the published version from the tag (`v1.0.0` -> `1.0.0`) and runs
`./gradlew publishAndReleaseToMavenCentral`, which builds, signs, uploads, and auto-releases the
artifacts in one step via the [Vanniktech Maven Publish
plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/). It needs these repo
secrets configured under *Settings > Secrets and variables > Actions* (one-time setup, from a
[Central Portal](https://central.sonatype.com) user token and a GPG key):

- `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` - a Central Portal user token (Account ->
  Generate User Token), *not* your login password
- `GPG_SIGNING_KEY` - your GPG private key, ASCII-armored (`gpg --export-secret-keys --armor <key-id>`)
- `GPG_SIGNING_KEY_ID` - the key's short id (`gpg --list-secret-keys --keyid-format short`)
- `GPG_SIGNING_PASSWORD` - the GPG key's passphrase

To publish manually from your machine instead, put the same four values in
`~/.gradle/gradle.properties` as `mavenCentralUsername`, `mavenCentralPassword`,
`signingInMemoryKey`/`signingInMemoryKeyId`, and `signingInMemoryKeyPassword`, then run:

```bash
./gradlew publishAndReleaseToMavenCentral -PlibraryVersion=1.0.0
```

A freshly released version can take a few minutes to show up in search/dependency resolution after
the workflow finishes.

## Design notes / migrating from an in-app copy of this code

This library was extracted from an app-internal SSE handling package. If you're migrating similar
in-app code:

- The retry loop lives in `SseConnectionManager.connect` via Kotlin Flow's `retryWhen` - it
  re-collects the *entire* upstream flow on retry, which is why the HTTP request itself must
  happen lazily inside the flow (see [Custom requests](#custom-requests) above).
  A `Flow` built from an already-fetched `Response` will "retry" by replaying a response that has
  already failed.
- `SseConnectionManager` intentionally does not depend on `ViewModel`, `Activity`, or any other
  Android lifecycle type - callers are responsible for calling `close()`.
- `currentAttempt` (1-based, 0 before the first attempt) is exposed so callers can show retry
  state in the UI (e.g. "still searching...") without duplicating counters.

## Requirements

- Kotlin 2.x, JVM target 21
- `kotlinx-coroutines-core` (brought in transitively)
- `okhttp` 5.x (brought in transitively)

## License

```
Apache License, Version 2.0 - see LICENSE
```
