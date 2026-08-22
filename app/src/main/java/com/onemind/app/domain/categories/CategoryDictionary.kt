package com.onemind.app.domain.categories

/**
 * The categories oneMind ships with.
 *
 * This is a **controlled vocabulary**: the application owns the taxonomy and a
 * language model only ever performs assignment against it. That distinction is
 * the whole point of the ticket. If models could name their own categories, a
 * thousand Memories would end up under a thousand near-synonyms — "AI", "A.I.",
 * "Artificial Intelligence", "ai stuff" — and browsing by category would be
 * worthless, which is precisely the disorder the app exists to fix.
 *
 * Chosen to be broad rather than precise. A category's job here is to narrow a
 * feed from a thousand Memories to fifty; search and entities do the fine-grained
 * work. Names are kept short and recognisable so a small model matches them
 * reliably.
 *
 * Order is fixed, so seeding assigns the same row ids on a fresh install as it
 * does on an upgrade.
 */
object CategoryDictionary {

    val ALL: List<String> = listOf(
        "Technology",
        "AI & Machine Learning",
        "Software Development",
        "Design",
        "Business",
        "Finance",
        "Investing",
        "Career & Work",
        "Education & Learning",
        "Science",
        "Health & Fitness",
        "Mental Health",
        "Food & Cooking",
        "Travel",
        "Shopping",
        "Home & Living",
        "Automotive",
        "Entertainment",
        "Music",
        "Film & TV",
        "Books & Reading",
        "Gaming",
        "Sports",
        "News & Politics",
        "Art & Photography",
        "Writing",
        "Productivity",
        "Relationships",
        "Family & Parenting",
        "Pets & Animals",
        "Nature & Outdoors",
        "Fashion & Beauty",
        "Legal",
        "Real Estate",
        "Events",
        "Personal"
    )
}
