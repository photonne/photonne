package com.photonne.app.ui.main

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectLinkTest {
    @Test
    fun `the web's QR carries the server address`() {
        assertEquals(
            "https://fotos.example.com:8443",
            ExternalNavigation.parseConnectUrl("photonne://connect?server=https%3A%2F%2Ffotos.example.com%3A8443")
        )
    }

    @Test
    fun `only http and https servers are accepted`() {
        assertNull(ExternalNavigation.parseConnectUrl("photonne://connect?server=javascript%3Aalert(1)"))
        assertNull(ExternalNavigation.parseConnectUrl("photonne://connect"))
    }

    @Test
    fun `a share link is not a connect link`() {
        assertNull(ExternalNavigation.parseConnectUrl("photonne://share/abc?server=https%3A%2F%2Fx.com"))
        assertEquals("abc", ExternalNavigation.parseShareUrl("photonne://share/abc")?.token)
    }

    @Test
    fun `the OpenID return carries a ticket or an error`() {
        assertEquals(
            OidcReturn(ticket = "a-b_c", error = null),
            ExternalNavigation.parseOidcUrl("photonne://oidc?ticket=a-b_c&server=https%3A%2F%2Fx.com")
        )
        assertEquals(OidcReturn(ticket = null, error = "no_account"), ExternalNavigation.parseOidcUrl("photonne://oidc?error=no_account"))
        assertNull(ExternalNavigation.parseOidcUrl("photonne://oidc"))
        assertNull(ExternalNavigation.parseOidcUrl("photonne://connect?server=https%3A%2F%2Fx.com"))
    }

    @Test
    fun `the memories widget opens one memory by id`() {
        val id = "3f2b6c1e-8d4a-4e1b-9c2d-5a6b7c8d9e0f"
        assertEquals(id, ExternalNavigation.parseMemoryUrl(ExternalNavigation.memoryUrl(id)))
        assertNull(ExternalNavigation.parseMemoryUrl("photonne://memory/"))
        assertNull(ExternalNavigation.parseMemoryUrl("photonne://memory/a%2F..%2Fb"))
        assertNull(ExternalNavigation.parseMemoryUrl("photonne://share/abc"))
    }
}
