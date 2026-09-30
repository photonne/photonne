package com.photonne.app.ui.util

import androidx.compose.ui.text.intl.Locale

/**
 * Nombre del país para un código ISO 3166-1 alfa-2 ("ES" → "España"), en el
 * idioma de la app, o null si la plataforma no lo conoce.
 *
 * El servidor solo manda el código: .NET no sabe traducir nombres de país
 * (RegionInfo devuelve el nombre nativo, "日本" para JP). Las plataformas sí.
 */
fun countryDisplayName(countryCode: String?): String? {
    val code = countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 } ?: return null
    // Misma regla de idioma que el resto de la app: inglés si el dispositivo
    // está en inglés, español en cualquier otro caso (idioma por defecto).
    val language = if (Locale.current.language == "en") "en" else "es"
    return platformCountryName(code, language)?.takeIf { it.isNotBlank() && it != code }
}

/** "Girona, España"; solo la ciudad si el país no se conoce. Null sin ciudad. */
fun formatPlaceName(placeName: String?, countryCode: String?): String? {
    val city = placeName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val country = countryDisplayName(countryCode)
    return if (country != null) "$city, $country" else city
}

internal expect fun platformCountryName(countryCode: String, language: String): String?
