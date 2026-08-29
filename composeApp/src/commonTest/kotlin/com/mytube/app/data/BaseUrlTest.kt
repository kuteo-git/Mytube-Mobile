package com.mytube.app.data

import com.mytube.app.data.repository.normaliseBaseUrl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What somebody types into the server address field.
 *
 * Every case here is a way a person actually enters an address, not a way a
 * parser can be provoked. The field is the first thing anyone touches in this
 * app and the only one that can leave it unable to do anything at all, so being
 * forgiving matters more here than anywhere else.
 */
class BaseUrlTest {

    @Test
    fun addsHttpWhenNoSchemeWasTyped() {
        // What the router's admin page shows, and what people copy from it.
        //
        // http and not https: this server has no TLS at all. Guessing https
        // would fail in a way that looks like the server being down, sending
        // somebody to check the Mac when the address was fine.
        assertEquals("http://10.0.0.5:8180", normaliseBaseUrl("10.0.0.5:8180"))
    }

    @Test
    fun keepsASchemeThatWasTyped() {
        assertEquals("http://10.0.0.5:8180", normaliseBaseUrl("http://10.0.0.5:8180"))
        assertEquals("https://mytube.local", normaliseBaseUrl("https://mytube.local"))
    }

    @Test
    fun dropsTheTrailingSlashABrowserLeavesBehind() {
        assertEquals("http://10.0.0.5:8180", normaliseBaseUrl("http://10.0.0.5:8180/"))
    }

    @Test
    fun ignoresTheSpacesThatComeWithAPaste() {
        assertEquals("http://10.0.0.5:8180", normaliseBaseUrl("  10.0.0.5:8180  "))
    }

    @Test
    fun leavesEmptyAlone() {
        // Empty means "not configured", and it has to survive as empty rather
        // than becoming "http://" — which would look configured and request
        // nothing.
        assertEquals("", normaliseBaseUrl(""))
        assertEquals("", normaliseBaseUrl("   "))
    }

    @Test
    fun doesNotInventAPort() {
        // Port 80 is a perfectly reasonable thing to mean, and silently
        // rewriting it to 8180 would make the field lie about what it will
        // request.
        assertEquals("http://mytube.local", normaliseBaseUrl("mytube.local"))
    }
}
