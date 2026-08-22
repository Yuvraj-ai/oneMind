package com.onemind.app

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.onemind.app.capture.ShareIntentParser
import com.onemind.app.domain.model.ContentType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests the intent-parsing seam.
 *
 * Intent data is chaotic — apps send text that is really a URL, images as content
 * URIs that may be permission-gated, multiple items in unpredictable orders — and
 * the parser absorbs all of that so the domain only sees ContentBlocks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareIntentParserTest {

    private lateinit var parser: ShareIntentParser

    @Before
    fun setup() {
        parser = ShareIntentParser(ApplicationProvider.getApplicationContext())
    }

    // --- text shares -------------------------------------------------------

    @Test
    fun `plain text intent produces a TEXT block`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Some interesting passage from an article")
        }

        val blocks = parser.parse(intent)

        assertEquals(1, blocks.size)
        assertEquals(ContentType.TEXT, blocks[0].type)
        assertEquals("Some interesting passage from an article", blocks[0].content)
    }

    @Test
    fun `blank text produces no blocks`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "   ")
        }

        assertTrue(parser.parse(intent).isEmpty())
    }

    @Test
    fun `null EXTRA_TEXT produces no blocks`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            // No EXTRA_TEXT set
        }

        assertTrue(parser.parse(intent).isEmpty())
    }

    // --- URL detection (text that is really a link) -----------------------

    @Test
    fun `a single URL in text-plain produces a URL block`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/article")
        }

        val blocks = parser.parse(intent)

        assertEquals(1, blocks.size)
        assertEquals(ContentType.URL, blocks[0].type)
        assertEquals("https://example.com/article", blocks[0].content)
    }

    @Test
    fun `a URL with path and query is detected`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/path?q=search&page=2")
        }

        assertEquals(ContentType.URL, parser.parse(intent)[0].type)
    }

    @Test
    fun `multi-line text containing a URL is TEXT, not URL`() {
        // A message that happens to include a link is text about a link, not just
        // the link — classifying it as URL would lose the commentary.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out this article\nhttps://example.com")
        }

        assertEquals(ContentType.TEXT, parser.parse(intent)[0].type)
    }

    @Test
    fun `http without s is still a URL`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "http://legacy-site.org/page")
        }

        assertEquals(ContentType.URL, parser.parse(intent)[0].type)
    }

    @Test
    fun `text that only resembles a URL but has spaces is TEXT`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://not a real url.com")
        }

        assertEquals(ContentType.TEXT, parser.parse(intent)[0].type)
    }

    // --- image shares (structural, cannot actually read URIs in test) ------

    @Test
    fun `image intent without a readable URI returns empty`() {
        // Content URI pointing to nothing — the parser should handle gracefully.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://fake/image.jpg"))
        }

        // Parser returns empty because it cannot open the stream.
        assertTrue(parser.parse(intent).isEmpty())
    }

    @Test
    fun `image intent with caption text produces TEXT block alongside`() {
        // Even though the image URI is unreadable, the text should still be saved.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://fake/image.jpg"))
            putExtra(Intent.EXTRA_TEXT, "Look at this!")
        }

        val blocks = parser.parse(intent)
        assertTrue(blocks.any { it.type == ContentType.TEXT && it.content == "Look at this!" })
    }

    // --- multiple images ---------------------------------------------------

    @Test
    fun `multiple image URIs that are all unreadable return empty`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(
                    Uri.parse("content://fake/1.jpg"),
                    Uri.parse("content://fake/2.jpg")
                )
            )
        }

        // All URIs unreadable, but no crash.
        assertTrue(parser.parse(intent).isEmpty())
    }

    @Test
    fun `multiple image intent with text still extracts the text`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(Uri.parse("content://fake/1.jpg"))
            )
            putExtra(Intent.EXTRA_TEXT, "Photos from the trip")
        }

        val blocks = parser.parse(intent)
        assertEquals(1, blocks.size)
        assertEquals(ContentType.TEXT, blocks[0].type)
    }

    // --- unknown action / type ---------------------------------------------

    @Test
    fun `unknown action returns empty`() {
        val intent = Intent("com.example.CUSTOM_ACTION")
        assertTrue(parser.parse(intent).isEmpty())
    }

    @Test
    fun `unknown MIME type with text falls back to TEXT`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_TEXT, "See attached")
        }

        val blocks = parser.parse(intent)
        assertEquals(1, blocks.size)
        assertEquals(ContentType.TEXT, blocks[0].type)
    }

    // --- source detection --------------------------------------------------

    @Test
    fun `source detected from referrer extra`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_REFERRER, "android-app://com.android.chrome/")
        }

        assertEquals("com.android.chrome", parser.detectSource(intent))
    }

    @Test
    fun `source is null when referrer is absent`() {
        val intent = Intent(Intent.ACTION_SEND)

        assertNull(parser.detectSource(intent))
    }

    @Test
    fun `source handles referrer without trailing slash`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_REFERRER, "android-app://com.whatsapp")
        }

        assertEquals("com.whatsapp", parser.detectSource(intent))
    }

    // --- with real file URIs (temp file created for test) ------------------

    @Test
    fun `file URI pointing to a real image produces an IMAGE block`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val tempFile = File(context.cacheDir, "test_image.jpg")
        // Write a minimal valid JPEG (magic bytes + minimal structure).
        tempFile.writeBytes(
            byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00,
                0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0xFF.toByte(), 0xD9.toByte()
            )
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(tempFile))
        }

        val blocks = parser.parse(intent)
        assertTrue("should produce an IMAGE block", blocks.any { it.type == ContentType.IMAGE })

        // Clean up
        blocks.filter { it.type == ContentType.IMAGE }.forEach { File(it.content).delete() }
        tempFile.delete()
    }
}
