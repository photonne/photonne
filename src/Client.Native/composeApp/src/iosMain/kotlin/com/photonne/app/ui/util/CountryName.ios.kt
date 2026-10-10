package com.photonne.app.ui.util

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode

internal actual fun platformCountryName(countryCode: String, language: String): String? =
    NSLocale(localeIdentifier = language).displayNameForKey(NSLocaleCountryCode, countryCode)
