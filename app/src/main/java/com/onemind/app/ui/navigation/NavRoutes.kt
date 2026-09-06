package com.onemind.app.ui.navigation

/**
 * Navigation route constants for the oneMind app.
 */
object NavRoutes {
    const val FEED = "feed"

    /**
     * Chronological view of the same Memories the feed shows.
     *
     * A destination rather than a mode on the feed. It was a `ViewMode` toggle inside
     * `FeedScreen`, which meant it had no route, could not be linked to, and lost its
     * selection on process death.
     */
    const val TIMELINE = "timeline"

    const val EVENTS = "events"

    /**
     * Unified retrieval, behind one bar.
     *
     * Also a destination rather than a mode, and for a stronger reason than Timeline:
     * search had its own debounced state living in `FeedViewModel`, so the feed's view
     * model was recreated with a search subsystem attached whether or not anyone
     * searched.
     */
    const val SEARCH = "search"
    const val COMPOSER = "composer"
    const val COMPOSER_EDIT = "composer/{memoryId}"
    const val MEMORY_DETAIL = "memory/{memoryId}"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"

    fun composerEdit(memoryId: Long) = "composer/$memoryId"
    fun memoryDetail(memoryId: Long) = "memory/$memoryId"
}
