package com.mytube.app.data.remote

import com.mytube.app.data.remote.dto.FeedDto
import com.mytube.app.data.remote.dto.VideoDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

/**
 * The one place this app makes an HTTP request.
 *
 * ## Why one place
 *
 * Every call has to say who is asking. The gateway reads `X-User-Id` and falls
 * back to a configured default when the header is absent, and the web app of
 * this same system spent its whole life without sending it — so every browser in
 * the household was one person, and one id accumulated 39,583 signals. The
 * lesson it wrote down afterwards is the reason this class exists:
 *
 *   *"Attached here rather than at each call site because 'every request' is the
 *   requirement, and a rule that has to be remembered forty times is a rule that
 *   will be forgotten once."*
 *
 * ## Why the address is a parameter and not a constructor argument
 *
 * It can change while the app is running — somebody fixes a typo on the settings
 * screen — and a base URL baked in at construction would mean rebuilding the
 * object graph to correct a mistake. The repository above reads the current one
 * and hands it down.
 */
class GatewayDataSource(private val client: HttpClient) {

    suspend fun feed(
        baseUrl: String,
        userId: String,
        topic: String,
        pageToken: String,
    ): FeedDto = client.get("${baseUrl.trimEnd('/')}/api/feed") {
        identify(userId)
        if (topic.isNotBlank()) parameter("topic", topic)
        if (pageToken.isNotBlank()) parameter("pageToken", pageToken)
    }.orThrow().body()

    suspend fun video(baseUrl: String, userId: String, id: String): VideoDto =
        client.get("${baseUrl.trimEnd('/')}/api/videos/$id") {
            identify(userId)
        }.orThrow().body()

    suspend fun search(baseUrl: String, userId: String, query: String): List<VideoDto> =
        client.get("${baseUrl.trimEnd('/')}/api/search") {
            identify(userId)
            parameter("q", query)
        }.orThrow().body<FeedDto>().videos

    /**
     * Whether something that behaves like the gateway answers here.
     *
     * Asks a real endpoint rather than the root: the gateway serves the web
     * bundle at `/`, so a 200 there proves only that *a* web server exists. A
     * feed request that parses is the shortest proof that this is the right one.
     */
    suspend fun reachable(baseUrl: String): Boolean = runCatching {
        client.get("${baseUrl.trimEnd('/')}/api/feed") {
            parameter("limit", 1)
        }.status.isSuccess()
    }.getOrElse { false }

    private fun io.ktor.client.request.HttpRequestBuilder.identify(userId: String) {
        // Omitted, never sent empty. Absence is what triggers the gateway's
        // fallback to a default profile, and that fallback is what keeps a fresh
        // install working before anybody has chosen who they are. An empty
        // header would be a claim to be nobody.
        if (userId.isNotBlank()) header("X-User-Id", userId)
    }
}

/**
 * A non-2xx answer is an error, said once.
 *
 * Ktor's default configuration returns the response rather than throwing, which
 * means every call site would have to remember to look. The server charter
 * records what that costs: an upstream status of 400 or more "arrives as a
 * *successful* round trip — `err` is nil, the status carries the bad news — so
 * passing it through left no trace anywhere. A day was spent looking at the
 * player for a fault that never logged a line."
 */
private fun HttpResponse.orThrow(): HttpResponse {
    if (!status.isSuccess()) throw GatewayException(status.value, call.request.url.toString())
    return this
}

class GatewayException(val status: Int, val url: String) :
    Exception("gateway answered $status for $url")
