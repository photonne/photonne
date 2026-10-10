package com.photonne.app.data.version

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerCompatibilityTest {

    @Test
    fun matching_versions_are_compatible() {
        assertEquals(
            ServerCompatibility.Compatible,
            serverCompatibility("1.160.0", "1.159.0", "1.160.0", "1.159.0")
        )
    }

    @Test
    fun unknown_server_version_is_not_reported_as_incompatible() {
        assertEquals(
            ServerCompatibility.Compatible,
            serverCompatibility(null, "9.0.0", "1.160.0", "9.0.0")
        )
    }

    @Test
    fun servers_without_min_client_version_only_check_the_server_side() {
        assertEquals(
            ServerCompatibility.Compatible,
            serverCompatibility("1.159.0", null, "1.160.0", "1.159.0")
        )
        assertEquals(
            ServerCompatibility.ServerTooOld("1.150.0", "1.159.0"),
            serverCompatibility("1.150.0", null, "1.160.0", "1.159.0")
        )
    }

    @Test
    fun an_old_app_is_told_to_update() {
        assertEquals(
            ServerCompatibility.ClientTooOld("1.160.0", "1.170.0"),
            serverCompatibility("1.175.0", "1.170.0", "1.160.0", "1.159.0")
        )
    }

    @Test
    fun an_old_app_wins_over_an_old_server() {
        // Imposible en la práctica, pero la app es lo que el usuario puede
        // actualizar él mismo.
        assertEquals(
            ServerCompatibility.ClientTooOld("1.160.0", "1.170.0"),
            serverCompatibility("1.150.0", "1.170.0", "1.160.0", "1.159.0")
        )
    }
}
