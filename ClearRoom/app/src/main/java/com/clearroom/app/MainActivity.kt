package com.clearroom.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clearroom.app.ui.ClearRoomApp
import com.clearroom.app.ui.theme.ClearRoomTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.factory((application as ClearRoomApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClearRoomTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val evaluation by viewModel.clarity.collectAsStateWithLifecycle()
                val fixes by viewModel.fixes.collectAsStateWithLifecycle()
                // Not unwrapped here: only the level bar reads it, so level updates don't recompose the app.
                val liveLevelDb = viewModel.liveLevelDb.collectAsStateWithLifecycle()
                ClearRoomApp(state = state, evaluation = evaluation, fixes = fixes, liveLevelDb = { liveLevelDb.value }, onEvent = viewModel::onEvent)
            }
        }
    }
}
