# sse-flow

A lightweight, `Flow`-based Server-Sent Events (SSE) client for Kotlin/Android, built on
[OkHttp](https://square.github.io/okhttp/) only - no Retrofit dependency required (though it
works great alongside Retrofit if the rest of your API layer already uses it).

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

The library is published to **GitHub Packages**. Add the repository and the dependency:

```kotlin
// settings.gradle.kts (dependencyResolutionManagement.repositories),
// or the top-level repositories block in an older project layout
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            name = "sse-flow"
            url = uri("https://maven.pkg.github.com/youssefelsa3ed/sse-flow-android")
            credentials {
                // A GitHub personal access token with `read:packages` scope.
                // GitHub Packages requires authentication even for public repositories.
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

```kotlin
// app/build.gradle.kts (or wherever you make the network call)
dependencies {
    implementation("io.github.youssefelsa3ed:sse-flow:<version>")
}
```

See [Authentication for GitHub Packages](#authentication-for-github-packages) below for where
`gpr.user` / `gpr.key` come from.

## Quick start

### 1. Issue the streaming request with OkHttp

```kotlin
val call = okHttpClient.newCall(Request.Builder().url(url).build())
```

If your API layer is built on Retrofit, declare a `@Streaming` endpoint returning
`Response<ResponseBody>` and unwrap it with `.raw()` to get the plain `okhttp3.Response` this
library works with:

```kotlin
interface ApiService {
    @Streaming
    @GET
    suspend fun streamResults(@Url url: String): Response<ResponseBody>
}
```

### 2. Build a `Flow<SseMessage>` and connect

```kotlin
import io.github.youssefelsa3ed.sse.SseConnectionManager
import io.github.youssefelsa3ed.sse.SseMessage
import io.github.youssefelsa3ed.sse.SseRetryPolicy
import io.github.youssefelsa3ed.sse.sseFlow

class SearchResultsSSE(private val api: ApiService) {

    private val connectionManager = SseConnectionManager<SseMessage>()

    fun start(url: String, onResult: (SseMessage) -> Unit, onFailed: (Throwable) -> Unit) {
        connectionManager.connect(
            stream = sseFlow { api.streamResults(url).raw() },
            retryPolicy = SseRetryPolicy.Bounded(maxRetries = 1),
            onMessage = onResult,
            onError = onFailed
        )
    }

    fun stop() = connectionManager.close()
}
```

`sseFlow { ... }` wraps the request in a *cold* flow: the network call only happens when the flow
is collected, and it happens again on every retry. This is what makes retries actually re-issue
the HTTP request instead of replaying an already-failed response - **always build your stream this
way** (or via your own `flow { ... }` builder) rather than passing an already-executed
`okhttp3.Response`.

### 3. Choose a retry policy

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

### 4. `connect` callbacks

| Callback | Called when |
|---|---|
| `onStart` | the stream (re)starts collecting - fires again on every reconnect |
| `onMessage` | every emitted `SseMessage` |
| `onRetry` | right before a retry attempt is evaluated, with the `Throwable` that caused it |
| `onError` | retries are exhausted, or `SseRetryPolicy.shouldRetry` returned `false` |
| `onCompleted` | the flow finishes for any reason: normal completion, an unretried error, or `close()` |

```kotlin
connectionManager.connect(
    stream = sseFlow { api.streamResults(url).raw() },
    retryPolicy = SseRetryPolicy.Bounded(maxRetries = 3),
    onStart = { Logger.log("SSE started") },
    onMessage = { message -> handle(message) },
    onRetry = { cause -> Logger.log("Retrying (attempt ${connectionManager.currentAttempt}): $cause") },
    onError = { cause -> Logger.logE(cause) },
    onCompleted = { Logger.log("SSE finished") }
)
```

### 5. Clean up

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

## Authentication for GitHub Packages

GitHub Packages requires authentication to *read* packages, even public ones. Consumers of this
library need a GitHub [personal access token](https://github.com/settings/tokens) with the
`read:packages` scope, supplied as `gpr.user` / `gpr.key` Gradle properties (in
`~/.gradle/gradle.properties`, kept out of version control) or as `GITHUB_ACTOR` / `GITHUB_TOKEN`
environment variables (e.g. in CI):

```properties
# ~/.gradle/gradle.properties (do not commit)
gpr.user=your-github-username
gpr.key=ghp_your_personal_access_token
```

## Publishing a new version

Releases are published to GitHub Packages automatically by
[`.github/workflows/publish.yml`](.github/workflows/publish.yml) whenever a tag matching `v*.*.*`
is pushed:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow derives the published version from the tag (`v1.0.0` -> `1.0.0`) and runs
`./gradlew publish`. It uses the repository's built-in `GITHUB_TOKEN`, so no extra secrets are
required.

To publish manually from your machine instead:

```bash
./gradlew publish -PlibraryVersion=1.0.0
```

This requires `gpr.user` / `gpr.key` (or `GITHUB_ACTOR` / `GITHUB_TOKEN`) to be set, with a token
that has the `write:packages` scope - see
[Authentication for GitHub Packages](#authentication-for-github-packages).

## Design notes / migrating from an in-app copy of this code

This library was extracted from an app-internal SSE handling package. If you're migrating similar
in-app code:

- The retry loop lives in `SseConnectionManager.connect` via Kotlin Flow's `retryWhen` - it
  re-collects the *entire* upstream flow on retry, which is why the HTTP request itself must
  happen lazily inside the flow (see [`sseFlow`](#2-build-a-flowssemessage-and-connect) above).
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
- Retrofit is *not* a dependency - `sseFlow` takes a plain `okhttp3.Response`, so it works
  whether or not the rest of your app uses Retrofit

## License

```
Apache License, Version 2.0 - see LICENSE
```
