package com.clearroom.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the installed app may do, after every library's manifest has been merged in: no going online
 * (NFR-8), even though MediaPipe's usage-statistics library asks for it (M12 audit).
 */
@RunWith(AndroidJUnit4::class)
class PermissionsTest {

    private val requested: List<String> by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        info.requestedPermissions?.toList().orEmpty()
    }

    @Test
    fun theAppCantGoOnline() {
        assertFalse("$requested", Manifest.permission.INTERNET in requested)
    }

    @Test
    fun theAppAsksForTheMicrophone() {
        assertTrue("$requested", Manifest.permission.RECORD_AUDIO in requested)
    }
}
