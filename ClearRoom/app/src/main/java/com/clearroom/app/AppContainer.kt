package com.clearroom.app

import android.content.Context
import com.clearroom.app.audio.AndroidRecorder
import com.clearroom.app.audio.AudioInput
import com.clearroom.app.audio.YamnetLabeller
import com.clearroom.app.data.JsonRoomStore
import com.clearroom.app.data.RoomRepository
import com.clearroom.app.daylog.CsvLogStore
import com.clearroom.app.daylog.LogStore
import com.clearroom.app.daylog.SoundLabeller
import java.io.File

/** Composition root: the one place that knows the concrete classes behind ClearRoom's interfaces. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val audioInput: AudioInput by lazy { AndroidRecorder(appContext) }

    /** App-private storage: needs no permission and other apps can't read it. */
    val roomRepository: RoomRepository by lazy { JsonRoomStore(File(appContext.filesDir, "rooms")) }

    /** Day logs sit beside the rooms, one plain-text file per log (FR-9). */
    val logStore: LogStore by lazy { CsvLogStore(File(appContext.filesDir, "logs")) }

    /** A fresh sound labeller for each log; null if the model can't load on this phone. */
    fun newSoundLabeller(): SoundLabeller? = YamnetLabeller.create(appContext)
}
