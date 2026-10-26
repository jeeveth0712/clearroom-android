package com.clearroom.app.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Current microphone access plus the two actions a screen can offer for it. */
class MicPermission(
    val access: MicAccess,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * Tracks RECORD_AUDIO for the calling screen. It re-checks on every resume, because the user can
 * grant or revoke the permission in Settings while ClearRoom is in the background.
 */
@Composable
fun rememberMicPermission(): MicPermission {
    val context = LocalContext.current
    var hasAsked by rememberSaveable { mutableStateOf(false) }
    var access by remember { mutableStateOf(context.currentMicAccess(hasAsked)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasAsked = true
        access = context.currentMicAccess(hasAsked = true)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = context.currentMicAccess(hasAsked)
    }

    return MicPermission(
        access = access,
        request = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
        openSettings = { context.openAppSettings() },
    )
}

private fun Context.currentMicAccess(hasAsked: Boolean): MicAccess {
    val activity = findActivity()
    return MicAccess.from(
        isGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED,
        shouldShowRationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO),
        hasAsked = hasAsked,
    )
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
