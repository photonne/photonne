package com.photonne.app.data.onboarding

import com.russhwolf.settings.Settings

/**
 * Whether each account on this device has already seen the welcome after
 * signing in. Per user id: another account signing in here sees it too.
 */
class WelcomeStore(private val settings: Settings) {
    fun shouldShow(userId: String): Boolean = !settings.getBoolean(key(userId), false)

    fun markSeen(userId: String) = settings.putBoolean(key(userId), true)

    private fun key(userId: String) = "welcome_seen_$userId"
}
