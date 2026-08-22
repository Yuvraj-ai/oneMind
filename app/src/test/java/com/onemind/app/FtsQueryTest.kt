package com.onemind.app

import com.onemind.app.domain.search.FtsQuery
import org.junit.Assert.*
import org.junit.Test

/**
 * Sanitisation of user input into FTS4 MATCH syntax.
 *
 * The important tests here are the hostile-looking ones, and they are not
 * hypothetical: `don't`, `C++`, and `AI OR ML` are things people type. Passing any
 * of them through raw is a SQLite syntax error on a keystroke, so these cases are
 * the difference between a search box and a crash.
 */
class FtsQueryTest {

    // --- ordinary queries --------------------------------------------------

    @Test
    fun `a single word becomes a prefix term`() {
        assertEquals("qwen*", FtsQuery.build("qwen"))
    }

    @Test
    fun `several words are joined with OR`() {
        assertEquals("ramen* recipe*", FtsQuery.build("ramen recipe")?.replace(" OR ", " "))
    }

    @Test
    fun `terms are ORed rather than ANDed`() {
        // With AND, one typo returns nothing at all. Coverage scoring, not
        // exclusion, is what separates strong matches from weak ones.
        assertEquals("ramen* OR recipe*", FtsQuery.build("ramen recipe"))
    }

    @Test
    fun `queries are lowercased`() {
        assertEquals("qwen*", FtsQuery.build("QWEN"))
    }

    @Test
    fun `duplicate terms are collapsed`() {
        assertEquals("ramen*", FtsQuery.build("ramen ramen ramen"))
    }

    // --- input that would otherwise be FTS syntax ---------------------------

    @Test
    fun `an apostrophe does not break the query`() {
        // "don't" is ordinary English, and a raw quote is a syntax error.
        val built = FtsQuery.build("don't panic")

        assertNotNull(built)
        assertFalse(built!!.contains("'"))
    }

    @Test
    fun `a double quote is stripped`() {
        val built = FtsQuery.build("""the "best" ramen""")

        assertNotNull(built)
        assertFalse(built!!.contains("\""))
    }

    @Test
    fun `the word OR is dropped as a stopword rather than parsed as an operator`() {
        // Two things could go wrong: FTS4 parsing it as an operator (prevented by
        // lowercasing) or it surviving as the term `or*`, which prefix-matches
        // "order", "organic", and "original".
        assertEquals("ai* OR ml*", FtsQuery.build("AI OR ML"))
    }

    @Test
    fun `the word NOT is dropped as a stopword`() {
        assertEquals("ramen*", FtsQuery.build("NOT ramen"))
    }

    @Test
    fun `the word NEAR survives as an ordinary term`() {
        // Not a stopword: "near" can be meaningful content.
        assertTrue(FtsQuery.build("NEAR tokyo")!!.contains("near*"))
    }

    // --- conversational queries --------------------------------------------

    @Test
    fun `a natural-language query keeps only the words that identify a memory`() {
        // The locked decisions centre search on queries phrased like this. Left
        // unfiltered, "show", "me", "the", "stuff", "from" and "saved" would each
        // count toward coverage as much as "ai" does.
        val built = FtsQuery.build("Show me the AI stuff I saved from Chrome last week")

        assertNotNull(built)
        val terms = built!!.split(" OR ")
        assertTrue("ai should survive", terms.contains("ai*"))
        assertTrue("chrome should survive", terms.contains("chrome*"))
        assertTrue("week should survive", terms.contains("week*"))

        listOf("show*", "me*", "the*", "stuff*", "from*", "saved*").forEach {
            assertFalse("$it should have been dropped", terms.contains(it))
        }
    }

    @Test
    fun `a query of only stopwords returns null`() {
        // Nothing to search for, so the caller should show the feed.
        assertNull(FtsQuery.build("show me the things I saved"))
    }

    @Test
    fun `stopwords are kept in documents, since only query terms are consulted`() {
        assertTrue(FtsQuery.documentTerms("the ramen from tokyo").contains("the"))
        assertFalse(FtsQuery.terms("the ramen from tokyo").contains("the"))
    }

    @Test
    fun `a column specifier colon is stripped`() {
        // "searchableText:foo" would be a column filter in FTS4.
        val built = FtsQuery.build("searchableText:foo")

        assertNotNull(built)
        assertFalse(built!!.contains(":"))
    }

    @Test
    fun `parentheses are stripped`() {
        val built = FtsQuery.build("(ramen)")

        assertNotNull(built)
        assertFalse(built!!.contains("("))
        assertFalse(built.contains(")"))
    }

    @Test
    fun `a user-supplied asterisk does not produce a double star`() {
        val built = FtsQuery.build("ram*")

        assertEquals("ram*", built)
    }

    @Test
    fun `a leading hyphen is stripped`() {
        val built = FtsQuery.build("-ramen")

        assertEquals("ramen*", built)
    }

    @Test
    fun `C plus plus does not break the query`() {
        val built = FtsQuery.build("C++ tutorial")

        assertNotNull(built)
        assertFalse(built!!.contains("+"))
    }

    @Test
    fun `a caret is stripped`() {
        assertFalse(FtsQuery.build("^ramen")!!.contains("^"))
    }

    @Test
    fun `an FTS expression typed verbatim is neutralised`() {
        // Someone pasting query syntax should get a text search for those words,
        // not an executed expression.
        val built = FtsQuery.build("""a* OR b* NEAR/3 "c d"""")

        assertNotNull(built)
        assertFalse(built!!.contains("\""))
        assertFalse(built.contains("/"))
    }

    // --- domains survive ---------------------------------------------------

    @Test
    fun `a domain stays one term`() {
        // Dots are kept so "github.com" is searchable as indexed.
        assertEquals("github.com*", FtsQuery.build("github.com"))
    }

    @Test
    fun `a trailing dot is trimmed`() {
        assertEquals("ramen*", FtsQuery.build("ramen."))
    }

    // --- nothing usable ----------------------------------------------------

    @Test
    fun `an empty query returns null`() {
        assertNull(FtsQuery.build(""))
    }

    @Test
    fun `a blank query returns null`() {
        assertNull(FtsQuery.build("   \n "))
    }

    @Test
    fun `punctuation only returns null`() {
        // Null rather than an expression matching nothing: the caller should show
        // the feed, not "no results".
        assertNull(FtsQuery.build("!!! ??? ..."))
    }

    @Test
    fun `a single character returns null`() {
        // One letter matches so much that it is noise rather than a filter.
        assertNull(FtsQuery.build("a"))
    }

    @Test
    fun `a two character term is kept`() {
        assertEquals("ai*", FtsQuery.build("ai"))
    }

    @Test
    fun `single characters are dropped from a longer query`() {
        assertEquals("ramen*", FtsQuery.build("a ramen i"))
    }

    // --- tokenize vs terms -------------------------------------------------

    @Test
    fun `tokenize keeps repeats, for scoring a document`() {
        assertEquals(listOf("ramen", "ramen"), FtsQuery.tokenize("ramen ramen"))
    }

    @Test
    fun `terms drops repeats, for building a query`() {
        assertEquals(listOf("ramen"), FtsQuery.terms("ramen ramen"))
    }

    @Test
    fun `a single character is dropped even when not a stopword`() {
        assertNull(FtsQuery.build("x"))
    }
}
