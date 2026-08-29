package com.mytube.app.domain.repository

/**
 * Where the library is, and who is asking.
 *
 * ## Why the address is a setting and not a constant
 *
 * The Mac's address comes from DHCP and can change; the server charter still
 * lists a static LAN IP as an open item. mDNS would find it automatically and
 * dies quietly on ordinary home routers, leaving nothing to type. So it is typed
 * once, the way Home Assistant asks for it, and remembered.
 *
 * ## Why the profile is here too
 *
 * Every request to the gateway must say who is asking — it reads `X-User-Id` and
 * falls back to a single default when the header is absent, which is how every
 * browser in that household was once one person with 39,583 signals under one
 * id. The header and the address are the same kind of thing: settings that must
 * be known before any other request can be made, so they are one port rather
 * than two.
 */
interface ServerRepository {

    /** The base URL, or empty when nobody has set one up yet. */
    suspend fun baseUrl(): String

    suspend fun setBaseUrl(url: String)

    /**
     * The household member this device is, or empty before anyone has chosen.
     *
     * Empty is meaningful and must survive as empty all the way to the request:
     * the gateway falls back to a default profile when the header is *absent*,
     * and that fallback is what keeps a fresh install working. An empty header
     * would be a claim to be nobody.
     */
    suspend fun profileId(): String

    suspend fun setProfileId(id: String)

    /**
     * The language tag this device reads in, or empty to follow the device.
     *
     * Empty is a real answer and not a missing one: a machine nobody has set up
     * should follow the system, and storing the resolved language on first
     * launch would freeze a phone into English because that is what it happened
     * to be that day.
     *
     * Per device, like the web app's — it belongs to whoever is holding the
     * phone, not to the household.
     */
    suspend fun language(): String

    suspend fun setLanguage(tag: String)

    /**
     * Whether a server really answers at this address.
     *
     * Takes the address rather than reading the saved one: testing what has
     * already been saved is testing something already accepted. The settings
     * screen checks before committing, which is the same rule the web app's
     * speech and proxy tests follow.
     */
    suspend fun reachable(url: String): Boolean
}
