package com.photonne.app.ui.util

import java.util.Locale

internal actual fun platformCountryName(countryCode: String, language: String): String? =
    runCatching { Locale("", countryCode).getDisplayCountry(Locale(language)) }.getOrNull()
