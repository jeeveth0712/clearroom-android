package com.clearroom.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.permission.MicAccess
import com.clearroom.app.permission.rememberMicPermission
import com.clearroom.app.ui.AppTab
import com.clearroom.app.ui.theme.ClearRoomTheme

/** Measure tab. For now it handles microphone access; recording arrives in milestone M2. */
@Composable
fun MeasureScreen(modifier: Modifier = Modifier) {
    val mic = rememberMicPermission()
    MeasureContent(
        micAccess = mic.access,
        onAllowMic = mic.request,
        onOpenSettings = mic.openSettings,
        modifier = modifier,
    )
}

@Composable
fun MeasureContent(
    micAccess: MicAccess,
    onAllowMic: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(AppTab.MEASURE.labelRes), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.measure_intro), style = MaterialTheme.typography.bodyLarge)
        MicAccessCard(micAccess, onAllowMic, onOpenSettings)
    }
}

@Composable
private fun MicAccessCard(micAccess: MicAccess, onAllowMic: () -> Unit, onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val message = when (micAccess) {
                MicAccess.NOT_ASKED -> R.string.mic_why
                MicAccess.GRANTED -> R.string.mic_ready
                MicAccess.DENIED_CAN_ASK_AGAIN -> R.string.mic_denied_can_ask_again
                MicAccess.DENIED_PERMANENTLY -> R.string.mic_denied_permanently
            }
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
            when {
                micAccess.canRequest -> Button(onClick = onAllowMic) { Text(stringResource(R.string.mic_allow)) }
                micAccess == MicAccess.DENIED_PERMANENTLY ->
                    Button(onClick = onOpenSettings) { Text(stringResource(R.string.mic_open_settings)) }
            }
        }
    }
}

private class MicAccessPreviews : PreviewParameterProvider<MicAccess> {
    override val values = MicAccess.entries.asSequence()
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MeasureContentPreview(@PreviewParameter(MicAccessPreviews::class) access: MicAccess) {
    ClearRoomTheme { MeasureContent(micAccess = access, onAllowMic = {}, onOpenSettings = {}) }
}
