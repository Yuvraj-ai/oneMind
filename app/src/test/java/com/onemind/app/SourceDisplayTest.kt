package com.onemind.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.onemind.app.domain.model.SourceType
import com.onemind.app.ui.feed.resolveSourceImpl
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceDisplayTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `MANUAL with no package returns null — nothing to show`() {
        assertNull(resolveSourceImpl(context, SourceType.MANUAL, null))
    }

    @Test
    fun `SCREENSHOT with no package shows fallback label`() {
        val info = resolveSourceImpl(context, SourceType.SCREENSHOT, null)
        assertNotNull(info)
        assertEquals("Screenshot", info!!.label)
        assertNull(info.icon)
    }

    @Test
    fun `CLIPBOARD with no package shows fallback label`() {
        val info = resolveSourceImpl(context, SourceType.CLIPBOARD, null)
        assertEquals("Clipboard", info!!.label)
    }

    @Test
    fun `SHARE with no package shows fallback label`() {
        val info = resolveSourceImpl(context, SourceType.SHARE, null)
        assertEquals("Shared", info!!.label)
    }

    @Test
    fun `uninstalled package falls back to source type label`() {
        // Package that doesn't exist in test
        val info = resolveSourceImpl(context, SourceType.SHARE, "com.nonexistent.app")
        assertNotNull(info)
        assertEquals("Shared", info!!.label)
        assertNull(info.icon)
    }

    @Test
    fun `MANUAL with a package still resolves — covers edge case of shared-then-edited`() {
        // In theory a MANUAL memory shouldn't have a sourcePackage, but if it does,
        // showing it is better than hiding it.
        val info = resolveSourceImpl(context, SourceType.MANUAL, "com.nonexistent.app")
        assertNotNull(info)
        assertEquals("Manual", info!!.label)
    }
}
