package com.photonne.app.ui.people

import com.photonne.app.data.models.Person

private val DIACRITICS = mapOf(
    'á' to 'a', 'à' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a', 'å' to 'a',
    'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
    'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
    'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
    'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
    'ñ' to 'n', 'ç' to 'c', 'ý' to 'y', 'ÿ' to 'y'
)

/** A name as people compare it: no case, accents or extra spaces ("Martí" = "marti"). */
internal fun foldName(name: String): String =
    name.trim().lowercase()
        .map { DIACRITICS[it] ?: it }
        .joinToString("")
        .replace(Regex("\\s+"), " ")

/**
 * Someone other than [exceptId] already called [name]: giving that name again
 * probably means it's the same person, so the rename offers to merge (as the
 * web does). Null when there is nobody, or the name is empty.
 */
internal fun namesake(people: List<Person>, name: String, exceptId: String): Person? {
    val wanted = foldName(name)
    if (wanted.isEmpty()) return null
    return people.firstOrNull { it.id != exceptId && foldName(it.name.orEmpty()) == wanted }
}
