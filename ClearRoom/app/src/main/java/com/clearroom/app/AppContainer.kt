package com.clearroom.app

import android.content.Context
import com.clearroom.app.audio.AndroidRecorder
import com.clearroom.app.audio.AudioInput

/** Composition root: the one place that knows the concrete classes behind ClearRoom's interfaces. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val audioInput: AudioInput by lazy { AndroidRecorder(appContext) }
}
