package com.photonne.app.data.version

/**
 * URL de respaldo donde esta plataforma puede descargar una versión nueva del
 * cliente (si el servidor no da la de la release concreta), o null si la plataforma no se actualiza a mano (móvil va por tienda/sideload y
 * de momento no avisamos). Gatea el aviso de actualización de MoreScreen.
 */
expect val clientUpdateUrl: String?

/**
 * True si [candidate] es estrictamente más nueva que [current]. Comparación
 * major.minor.patch numérica (los sufijos tras '-' se ignoran). Servidor y
 * clientes versionan juntos desde Directory.Build.props, pero solo las
 * releases traen instaladores: compara contra la última release, no contra
 * la versión del servidor.
 */
fun isNewerVersion(candidate: String?, current: String): Boolean {
    val s = parseVersion(candidate ?: return false) ?: return false
    val c = parseVersion(current) ?: return false
    return s > c
}

private fun parseVersion(raw: String): List<Int>? {
    val core = raw.trim().removePrefix("v").substringBefore('-')
    val parts = core.split('.').map { it.toIntOrNull() ?: return null }
    if (parts.isEmpty()) return null
    return List(3) { parts.getOrElse(it) { 0 } }
}

private operator fun List<Int>.compareTo(other: List<Int>): Int {
    for (i in 0 until maxOf(size, other.size)) {
        val diff = getOrElse(i) { 0 }.compareTo(other.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}
