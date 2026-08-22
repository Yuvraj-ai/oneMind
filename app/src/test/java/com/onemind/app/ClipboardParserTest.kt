package com.onemind.app

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.onemind.app.capture.ClipboardParser
import com.onemind.app.domain.model.ContentType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the clipboard-to-ContentBlock parsing.
 *
 * Covers: plain text, HTML coerced to text, URIs (http/https), URL detection
 * from text, empty/null/blank cases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardParserTest {

    private lateinit var context: android.content.Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    // --- plain text --------------------------------------------------------

    @Test
    fun `plain text clip produces TEXT block`() {
        val clip = ClipData.newPlainText("label", "Some copied text")

        val block = ClipboardParser.parse(clip, context)

        assertNotNull(block)
        assertEquals(ContentType.TEXT, block!!.type)
        assertEquals("Some copied text", block.content)
    }

    @Test
    fun `text is trimmed`() {
        val clip = ClipData.newPlainText("label", "  padded text  \n")

        val block = ClipboardParser.parse(clip, context)

        assertEquals("padded text", block!!.content)
    }

    // --- URL detection from text -------------------------------------------

    @Test
    fun `single URL in text produces URL block`() {
        val clip = ClipData.newPlainText("label", "https://example.com/article")

        val block = ClipboardParser.parse(clip, context)

        assertEquals(ContentType.URL, block!!.type)
        assertEquals("https://example.com/article", block.content)
    }

    @Test
    fun `http URL without s is detected`() {
        val clip = ClipData.newPlainText("label", "http://example.org")

        assertEquals(ContentType.URL, ClipboardParser.parse(clip, context)!!.type)
    }

    @Test
    fun `multi-line text with URL stays TEXT`() {
        val clip = ClipData.newPlainText("label", "Check this out:\nhttps://example.com")

        assertEquals(ContentType.TEXT, ClipboardParser.parse(clip, context)!!.type)
    }

    @Test
    fun `text with spaces is not a URL`() {
        val clip = ClipData.newPlainText("label", "https://not a real url.com")

        assertEquals(ContentType.TEXT, ClipboardParser.parse(clip, context)!!.type)
    }

    // --- URI clips ----------------------------------------------------------

    @Test
    fun `HTTP URI produces URL block`() {
        val uri = Uri.parse("https://github.com/Yuvraj-ai/oneMind")
        val clip = ClipData(
            ClipDescription("link", arrayOf("text/uri-list")),
            ClipData.Item(uri)
        )

        val block = ClipboardParser.parse(clip, context)

        assertEquals(ContentType.URL, block!!.type)
        assertEquals("https://github.com/Yuvraj-ai/oneMind", block.content)
    }

    @Test
    fun `non-HTTP URI with text falls back to TEXT`() {
        // e.g. content:// URI with coerced text
        val clip = ClipData.newPlainText("label", "Some file reference")

        val block = ClipboardParser.parse(clip, context)

        assertEquals(ContentType.TEXT, block!!.type)
    }

    // --- HTML clips ---------------------------------------------------------

    @Test
    fun `HTML clip is coerced to plain text`() {
        val clip = ClipData.newHtmlText(
            "html",
            "Visible text",
            "<b>Visible text</b>"
        )

        val block = ClipboardParser.parse(clip, context)

        assertEquals(ContentType.TEXT, block!!.type)
        // Coerced text should be the plain version.
        assertTrue(block.content.contains("Visible text"))
    }

    // --- empty / null -------------------------------------------------------

    @Test
    fun `null clip returns null`() {
        assertNull(ClipboardParser.parse(null, context))
    }

    @Test
    fun `empty clip returns null`() {
        // ClipData with zero items is not normally possible via API, but defensive.
        val clip = ClipData.newPlainText("label", "")

        assertNull(ClipboardParser.parse(clip, context))
    }

    @Test
    fun `blank text returns null`() {
        val clip = ClipData.newPlainText("label", "   \n  ")

        assertNull(ClipboardParser.parse(clip, context))
    }
}
