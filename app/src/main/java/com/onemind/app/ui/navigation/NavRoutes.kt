package com.onemind.app.ui.navigation

/**
 * Navigation route constants for the oneMind app.
 */
object NavRoutes {
    const val FEED = "feed"
    const val EVENTS = "events"
    const val COMPOSER = "composer"
    const val COMPOSER_EDIT = "composer/{memoryId}"
    const val MEMORY_DETAIL = "memory/{memoryId}"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"

    fun composerEdit(memoryId: Long) = "composer/$memoryId"
    fun memoryDetail(memoryId: Long) = "memory/$memoryId"
}
