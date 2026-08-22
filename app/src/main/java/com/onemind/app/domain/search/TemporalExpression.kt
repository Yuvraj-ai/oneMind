package com.onemind.app.domain.search

import java.time.Instant

/**
 * A time constraint found in a query.
 *
 * [matchedText] is carried separately from the range so the caller can remove it
 * before semantic matching. That matters: embedding "AI stuff from last week"
 * pushes the vector toward the *language of asking about time* rather than toward
 * AI, and the temporal part has already been turned into a hard filter by the time
 * anyone reads it. Stripping leaves "AI stuff", which is what the user is actually
 * looking for.
 */
data class TemporalExpression(
    /** Inclusive lower bound. */
    val start: Instant,
    /** Exclusive upper bound, so day ranges do not overlap at midnight. */
    val endExclusive: Instant,
    /** The words that produced this range, exactly as they appeared. */
    val matchedText: String
) {
    operator fun contains(instant: Instant): Boolean =
        instant >= start && instant < endExclusive
}
