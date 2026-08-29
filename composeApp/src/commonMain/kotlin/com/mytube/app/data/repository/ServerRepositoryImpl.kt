package com.mytube.app.data.repository

import com.mytube.app.data.local.SettingsDataSource
import com.mytube.app.data.local.SettingsKeys
import com.mytube.app.data.remote.GatewayDataSource
import com.mytube.app.domain.repository.ServerRepository

/**
 * The address and the profile, kept on the device.
 *
 * Thin on purpose: everything it does is read or write one key, and the only
 * judgement in the file is `normalise`. A repository that has nothing to decide
 * should look like it has nothing to decide.
 */
class ServerRepositoryImpl(
    private val settings: SettingsDataSource,
    private val gateway: GatewayDataSource,
) : ServerRepository {

    // The data source may answer null — a key that was never written. That null
    // stops here: everything above this line is non-null by rule.
    override suspend fun baseUrl(): String = settings.get(SettingsKeys.BASE_URL).orEmpty()

    override suspend fun setBaseUrl(url: String) =
        settings.set(SettingsKeys.BASE_URL, normalise(url))

    override suspend fun profileId(): String = settings.get(SettingsKeys.PROFILE_ID).orEmpty()

    override suspend fun setProfileId(id: String) = settings.set(SettingsKeys.PROFILE_ID, id)

    override suspend fun reachable(url: String): Boolean = gateway.reachable(normalise(url))

    private fun normalise(url: String) = normaliseBaseUrl(url)
}

/**
 * What somebody typed, turned into something that can be requested.
 *
 * A pure function, and separate so it can be asserted without a network. Every
 * rule here comes from a way a person actually types an address:
 *
 *  - `10.0.0.5:8180` — no scheme, because that is what the router's page shows.
 *    Assumed http, not https: this server has no TLS at all, and guessing https
 *    would fail in a way that looks like the server being down.
 *  - a trailing slash, because it is what a browser leaves behind when the
 *    address is copied out of the URL bar.
 *  - surrounding spaces, from pasting.
 *
 * It does not invent a port. `http://10.0.0.5` meaning port 80 is a reasonable
 * thing to want, and silently rewriting it to 8180 would make the field lie
 * about what it will request.
 */
fun normaliseBaseUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return trimmed
    val withScheme =
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "http://$trimmed"
    return withScheme
}
