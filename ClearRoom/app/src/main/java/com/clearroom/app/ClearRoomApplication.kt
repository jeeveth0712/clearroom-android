package com.clearroom.app

import android.app.Application

/** Holds the [AppContainer] so it outlives activity recreation (rotation, theme change). */
class ClearRoomApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
