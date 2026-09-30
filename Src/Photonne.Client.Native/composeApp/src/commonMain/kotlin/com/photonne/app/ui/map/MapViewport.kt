package com.photonne.app.ui.map

import com.photonne.app.data.models.MapPoint
import kotlin.math.abs

/*
 * Lógica pura de la hoja persistente del mapa: qué fotos caen en el viewport,
 * en qué orden se enseñan y dónde se colocan los anclajes de la hoja. Sin
 * Compose, para poder probarla en commonTest.
 */

/** Del más reciente al más antiguo; el id desempata para que el orden sea
 *  estable entre recálculos (la rejilla usa el id como clave). */
internal fun List<MapPoint>.newestFirst(): List<MapPoint> =
    sortedWith(compareByDescending<MapPoint> { it.date }.thenBy { it.id })

/**
 * Puntos cuyo marcador cae dentro del rectángulo visible del mapa
 * ([widthPx] × [heightPx] centrado en [centerLat]/[centerLng] a [zoom]), del más
 * reciente al más antiguo. Sin tamaño medido todavía no hay viewport que mirar
 * y se devuelve vacío.
 */
internal fun pointsInViewport(
    points: List<MapPoint>,
    centerLat: Double,
    centerLng: Double,
    zoom: Int,
    widthPx: Int,
    heightPx: Int
): List<MapPoint> {
    if (widthPx <= 0 || heightPx <= 0 || points.isEmpty()) return emptyList()
    val center = project(LatLng(centerLat, centerLng), zoom)
    val halfW = widthPx / 2.0
    val halfH = heightPx / 2.0
    return points.filter { p ->
        val w = project(LatLng(p.latitude, p.longitude), zoom)
        abs(w.x - center.x) <= halfW && abs(w.y - center.y) <= halfH
    }.newestFirst()
}

/** Anclajes de la hoja persistente del mapa. */
enum class MapSheetValue { Peek, Half, Expanded }

/** Fracción del alto del mapa que ocupa la hoja a media altura. */
internal const val MAP_SHEET_HALF_FRACTION = 0.45f

/**
 * Posición (y del borde superior de la hoja, en px desde arriba del mapa) de
 * cada anclaje. Media altura se omite cuando no cabe con aire entre los otros
 * dos (ventanas muy bajas de escritorio): la hoja salta entonces de asomada a
 * desplegada.
 *
 * @param heightPx alto del mapa.
 * @param expandedTopPx dónde acaba el cromo de arriba: la hoja desplegada no lo tapa.
 * @param peekVisiblePx cuánto asoma la hoja en reposo (asa + resumen + la nav
 *   flotante, que flota por encima de la hoja).
 */
internal fun mapSheetAnchorPositions(
    heightPx: Float,
    expandedTopPx: Float,
    peekVisiblePx: Float,
    halfFraction: Float = MAP_SHEET_HALF_FRACTION,
    minGapPx: Float = 48f
): Map<MapSheetValue, Float> {
    val peek = (heightPx - peekVisiblePx).coerceAtLeast(expandedTopPx)
    val half = heightPx * (1f - halfFraction)
    return buildMap {
        put(MapSheetValue.Peek, peek)
        if (half - expandedTopPx >= minGapPx && peek - half >= minGapPx) {
            put(MapSheetValue.Half, half)
        }
        if (peek - expandedTopPx >= minGapPx) put(MapSheetValue.Expanded, expandedTopPx)
    }
}

/**
 * Centro del mapa que deja el centroide de [points] en mitad del hueco visible
 * entre [visibleTopPx] y [visibleBottomPx] (el cromo de arriba y la hoja a
 * media altura), sin cambiar el zoom. Null si no hay puntos o el mapa no se ha
 * medido.
 */
internal fun centerToRevealCluster(
    points: List<MapPoint>,
    zoom: Int,
    mapHeightPx: Int,
    visibleTopPx: Float,
    visibleBottomPx: Float
): Pair<Double, Double>? {
    if (points.isEmpty() || mapHeightPx <= 0) return null
    val worldX = points.sumOf { lonToWorldX(it.longitude, zoom) } / points.size
    val worldY = points.sumOf { latToWorldY(it.latitude, zoom) } / points.size
    val targetScreenY = (visibleTopPx + visibleBottomPx) / 2.0
    // El centro del mapa está en mapHeight/2: desplázalo lo que le falta al
    // clúster para caer en targetScreenY.
    val centerWorldY = worldY + (mapHeightPx / 2.0 - targetScreenY)
    val latLng = unproject(WorldPx(worldX, centerWorldY), zoom)
    return latLng.latitude to latLng.longitude
}
