package com.clearroom.app

import android.content.Context
import com.clearroom.app.audio.AndroidRecorder
import com.clearroom.app.audio.AudioInput
import com.clearroom.app.data.JsonRoomStore
import com.clearroom.app.data.RoomRepository
import java.io.File

/** Composition root: the one place that knows the concrete classes behind ClearRoom's interfaces. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val audioInput: AudioInput by lazy { AndroidRecorder(appContext) }

    /** App-private storage: needs no permission and other apps can't read it. */
    val roomRepository: RoomRepository by lazy { JsonRoomStore(File(appContext.filesDir, "rooms")) }
}
