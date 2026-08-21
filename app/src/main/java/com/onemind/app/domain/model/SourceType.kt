package com.onemind.app.domain.model

/**
 * The origin of a Memory — how content entered oneMind.
 */
enum class SourceType {
    /** Created via the in-app manual composer */
    MANUAL,

    /** Captured via screen capture (MediaProjection) */
    SCREENSHOT,

    /** Received via Android Share/Import intent */
    SHARE,

    /** Saved via Quick Settings clipboard tile */
    CLIPBOARD
}
