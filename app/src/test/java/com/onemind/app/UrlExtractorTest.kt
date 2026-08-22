package com.onemind.app

import com.onemind.app.domain.model.DerivedSource
import com.onemind.app.domain.processing.UrlExtractor
import org.junit.Assert.*
import org.junit.Test

/**
 * Link extraction, which runs with no provider configured and so is the one part
 * of metadata extraction every user gets. Worth pinning down properly, because
 * URLs in real text are surrounded by punctuation that is easy to swallow.
 */
class UrlExtractorTest {

    private fun extract(text: String) = UrlExtractor.extract(memoryId = 1L, text = text)

    // --- finding links ----------------------------------------------------

    @Test
    fun `finds a bare url`() {
        val urls = extract("see https://github.com/example/project")

        assertEquals(1, urls.size)
        assertEquals("https://github.com/example/project", urls.first().rawUrl)
    }

    @Test
    fun `finds several links in order`() {
        val urls = extract("first https://a.com then https://b.com")

        assertEquals(listOf("a.com", "b.com"), urls.map { it.domain })
    }

    @Test
    fun `finds http as well as https`() {
        assertEquals(1, extract("http://example.com/page").size)
    }

    @Test
    fun `ignores text with no links`() {
        assertTrue(extract("just some notes about nothing").isEmpty())
    }

    @Test
    fun `ignores blank text`() {
        assertTrue(extract("").isEmpty())
        assertTrue(extract("   \n ").isEmpty())
    }

    @Test
    fun `does not treat a bare domain as a link`() {
        // Without a scheme it is too ambiguous to store as a URL.
        assertTrue(extract("visit github.com for details").isEmpty())
    }

    // --- trailing punctuation, the fiddly part -----------------------------

    @Test
    fun `drops a sentence-ending full stop`() {
        val urls = extract("Read https://example.com/article.")

        assertEquals("https://example.com/article", urls.first().rawUrl)
    }

    @Test
    fun `drops a trailing comma`() {
        val urls = extract("https://example.com/a, and more")

        assertEquals("https://example.com/a", urls.first().rawUrl)
    }

    @Test
    fun `drops trailing colon semicolon and question mark`() {
        assertEquals("https://a.com/x", extract("https://a.com/x:").first().rawUrl)
        assertEquals("https://a.com/x", extract("https://a.com/x;").first().rawUrl)
        assertEquals("https://a.com/x", extract("https://a.com/x?").first().rawUrl)
    }

    @Test
    fun `drops an unbalanced closing bracket`() {
        val urls = extract("(see https://example.com/page)")

        assertEquals("https://example.com/page", urls.first().rawUrl)
    }

    @Test
    fun `keeps a balanced bracket, which some urls genuinely contain`() {
        val urls = extract("https://en.wikipedia.org/wiki/Foo_(bar)")

        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", urls.first().rawUrl)
    }

    @Test
    fun `keeps a full stop inside a path`() {
        val urls = extract("https://example.com/file.tar.gz and more")

        assertEquals("https://example.com/file.tar.gz", urls.first().rawUrl)
    }

    // --- query strings ----------------------------------------------------

    @Test
    fun `keeps the query string, which often identifies the page`() {
        val urls = extract("https://youtube.com/watch?v=abc123")

        assertEquals("https://youtube.com/watch?v=abc123", urls.first().normalizedUrl)
    }

    @Test
    fun `two links differing only by query are different links`() {
        val urls = UrlExtractor.extractAll(
            1L,
            listOf(DerivedSource.USER_TEXT to "https://a.com/p?id=1 https://a.com/p?id=2")
        )

        assertEquals(2, urls.size)
    }

    // --- normalisation ----------------------------------------------------

    @Test
    fun `lowercases scheme and host but leaves the path alone`() {
        // Paths are case-sensitive on most servers; hosts are not.
        val normalized = UrlExtractor.normalize("HTTPS://Example.COM/MyPage")

        assertEquals("https://example.com/MyPage", normalized)
    }

    @Test
    fun `drops a trailing slash`() {
        assertEquals("https://example.com", UrlExtractor.normalize("https://example.com/"))
    }

    @Test
    fun `drops the fragment, which does not identify a different page`() {
        assertEquals(
            "https://example.com/doc",
            UrlExtractor.normalize("https://example.com/doc#section-2")
        )
    }

    @Test
    fun `drops a default port`() {
        assertEquals("https://example.com/a", UrlExtractor.normalize("https://example.com:443/a"))
        assertEquals("http://example.com/a", UrlExtractor.normalize("http://example.com:80/a"))
    }

    @Test
    fun `keeps a non-default port, which does identify a different service`() {
        assertEquals(
            "http://localhost:11434/api",
            UrlExtractor.normalize("http://localhost:11434/api")
        )
    }

    // --- domain -----------------------------------------------------------

    @Test
    fun `strips www from the domain so a site groups together`() {
        assertEquals("example.com", UrlExtractor.domainOf("https://www.example.com/a"))
    }

    @Test
    fun `keeps a subdomain that is not www`() {
        assertEquals("docs.example.com", UrlExtractor.domainOf("https://docs.example.com/a"))
    }

    @Test
    fun `strips the port from the domain`() {
        assertEquals("localhost", UrlExtractor.domainOf("http://localhost:8080/a"))
    }

    // --- de-duplication across sources ------------------------------------

    @Test
    fun `the same link in user text and in a screenshot is stored once`() {
        // A very common shape: the user pastes a link and also screenshots the page.
        val urls = UrlExtractor.extractAll(
            1L,
            listOf(
                DerivedSource.USER_TEXT to "https://example.com/page",
                DerivedSource.OCR to "https://example.com/page"
            )
        )

        assertEquals(1, urls.size)
    }

    @Test
    fun `links differing only by trailing slash are the same link`() {
        val urls = UrlExtractor.extractAll(
            1L,
            listOf(
                DerivedSource.USER_TEXT to "https://example.com/page/",
                DerivedSource.OCR to "https://example.com/page"
            )
        )

        assertEquals(1, urls.size)
    }

    @Test
    fun `links differing only by case of host are the same link`() {
        val urls = UrlExtractor.extractAll(
            1L,
            listOf(
                DerivedSource.USER_TEXT to "https://Example.com/a",
                DerivedSource.OCR to "https://example.com/a"
            )
        )

        assertEquals(1, urls.size)
    }

    @Test
    fun `distinct links across sources are all kept`() {
        val urls = UrlExtractor.extractAll(
            1L,
            listOf(
                DerivedSource.USER_TEXT to "https://a.com",
                DerivedSource.OCR to "https://b.com",
                DerivedSource.VISION to "https://c.com"
            )
        )

        assertEquals(3, urls.size)
    }

    @Test
    fun `the memory id is carried onto every extracted link`() {
        val urls = UrlExtractor.extractAll(
            42L,
            listOf(DerivedSource.USER_TEXT to "https://a.com https://b.com")
        )

        assertTrue(urls.all { it.memoryId == 42L })
    }
}
