package com.mytube.app

import com.mytube.app.data.local.SettingsDataSource
import com.mytube.app.data.remote.GatewayDataSource
import com.mytube.app.data.repository.ServerRepositoryImpl
import com.mytube.app.data.repository.StreamRepositoryImpl
import com.mytube.app.data.repository.VideoRepositoryImpl
import com.mytube.app.domain.repository.ServerRepository
import com.mytube.app.domain.repository.StreamRepository
import com.mytube.app.domain.repository.VideoPlayerFactory
import com.mytube.app.domain.repository.VideoRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The composition root: the one place the object graph is built.
 *
 * ## Why by hand
 *
 * There is no DI framework here, and that is a decision rather than an omission.
 * The rest of this system wires the same way — the Go services build everything
 * in `main.go`, the web app uses module singletons — but the stronger reason is
 * what a container would cost. Koin resolves at runtime, so a dependency nobody
 * registered is a crash on the screen that needed it. Built this way, the same
 * mistake does not compile.
 *
 * It is also small. When this file becomes hard to read, that is information: it
 * means the graph has grown a shape worth naming, and the answer then is more
 * containers, not a library that hides the shape.
 *
 * ## Why the platform hands in the settings store
 *
 * `SettingsDataSource` is the only thing here that cannot be built from common
 * code — Android needs a `Context`, iOS needs nothing at all. Passing it in
 * keeps every other decision on this side of the boundary.
 */
class AppContainer(
    settings: SettingsDataSource,
    /**
     * Builds a player for the platform this is running on.
     *
     * Handed in for the same reason as the settings store: it is the one thing
     * here that common code cannot construct. Android needs a Context, iOS needs
     * an audio session — and the composition root stays the only place either is
     * mentioned.
     */
    val playerFactory: VideoPlayerFactory,
) {

    /**
     * One client for the whole app, and Coil is given the same one.
     *
     * An `HttpClient` owns a connection pool and a thread pool. Two of them
     * means two, for a phone talking to a single server on the local network.
     */
    val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    // The gateway sends fields this app does not model, and adds
                    // more with every release. Refusing to parse an answer
                    // because it grew a key is the strictness nobody wants.
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
        install(HttpTimeout) {
            // The library is on the same wifi, so a request that has not
            // answered in this long is not slow — it is the wrong address, or
            // the Mac is asleep. Failing quickly is what lets the screen say so
            // while somebody is still looking at it.
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
        }
    }

    private val gateway = GatewayDataSource(httpClient)

    val serverRepository: ServerRepository = ServerRepositoryImpl(settings, gateway)

    val videoRepository: VideoRepository = VideoRepositoryImpl(gateway, serverRepository)

    val streamRepository: StreamRepository = StreamRepositoryImpl(gateway, serverRepository)
}
