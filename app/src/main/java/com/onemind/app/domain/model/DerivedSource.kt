package com.onemind.app.domain.model

/**
 * Which representation of a Memory a piece of derived metadata came from.
 *
 * This is provenance. The same organisation might be named in text the user
 * typed, in text read off a screenshot, and in a model's description of a photo;
 * knowing which lets the UI explain itself and lets a low-trust source be
 * discounted later.
 */
enum class DerivedSource {
    /** Text the user typed or pasted. */
    USER_TEXT,

    /** Text recognised in an image by OCR. */
    OCR,

    /** A model's description of an image. */
    VISION
}
