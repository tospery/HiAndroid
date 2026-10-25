package com.tospery.core.content

import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextExtensionsTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun findActivityReturnsNullWithoutActivityInWrapperChain() {
        assertNull(ContextWrapper(ContextWrapper(context.applicationContext)).findActivity())
    }

    @Test
    fun copyPlainTextToClipboardWritesPrimaryClip() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(context.copyPlainTextToClipboard(label = "label", text = "copied"))
            val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
            assertEquals("copied", clip?.getItemAt(0)?.text?.toString())
        }
    }

    @Test
    fun safeStartActivityReturnsFalseWhenNothingHandlesIntent() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tospery-core-test-unhandled://target"))

        assertFalse(context.applicationContext.safeStartActivity(intent))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun appPackageVersionOrNullReadsInstalledPackage() {
        assertNotNull(context.appPackageVersionOrNull())
    }
}
