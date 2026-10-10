package com.photonne.app.ui.map

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web-Mercator projection helpers shared by the map composable and the
 * view-model. World pixel coordinates are at zoom 0 a single 256-pixel
 * tile that wraps the globe; each zoom level doubles the world.
 */
internal const val TILE_SIZE_PX = 256

internal data class WorldPx(val x: Double, val y: Double)

internal data class LatLng(val latitude: Double, val longitude: Double)

internal fun lonToWorldX(lon: Double, zoom: Int): Double {
    val n = (1L shl zoom).toDouble()
    return (lon + 180.0) / 360.0 * n * TILE_SIZE_PX
}

internal fun latToWorldY(lat: Double, zoom: Int): Double {
    val n = (1L shl zoom).toDouble()
    val sinLat = kotlin.math.sin(lat * PI / 180.0)
    val y = 0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)
    return y * n * TILE_SIZE_PX
}

internal fun worldXToLon(worldX: Double, zoom: Int): Double {
    val n = (1L shl zoom).toDouble()
    return worldX / (n * TILE_SIZE_PX) * 360.0 - 180.0
}

internal fun worldYToLat(worldY: Double, zoom: Int): Double {
    val n = (1L shl zoom).toDouble()
    val y = worldY / (n * TILE_SIZE_PX)
    val latRad = atan(sinh(PI - 2.0 * PI * y))
    return latRad * 180.0 / PI
}

internal fun project(latLng: LatLng, zoom: Int): WorldPx =
    WorldPx(
        x = lonToWorldX(latLng.longitude, zoom),
        y = latToWorldY(latLng.latitude, zoom)
    )

internal fun unproject(worldPx: WorldPx, zoom: Int): LatLng =
    LatLng(
        latitude = worldYToLat(worldPx.y, zoom),
        longitude = worldXToLon(worldPx.x, zoom)
    )

/** Inverse of latToWorldY; used so the equator-aware formula above stays
 * close to the canonical OSM definition. Kept for completeness even if
 * unused at the moment. */
@Suppress("unused")
internal fun latToWorldYAlt(lat: Double, zoom: Int): Double {
    val n = (1L shl zoom).toDouble()
    val latRad = lat * PI / 180.0
    val sinhInv = asinh(tan(latRad))
    return (1.0 - sinhInv / PI) / 2.0 * n * TILE_SIZE_PX
}
