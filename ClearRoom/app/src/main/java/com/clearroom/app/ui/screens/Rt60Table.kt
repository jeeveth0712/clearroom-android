package com.clearroom.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.clearroom.app.acoustics.BandRt60
import com.clearroom.app.acoustics.Rt60Summary
import com.clearroom.app.dsp.OctaveBands
import java.util.Locale

/** One band's echo time to a tenth of a second; borrowed (estimated) values in brackets. */
fun rt60Cell(band: BandRt60): String {
    val value = String.format(Locale.getDefault(), "%.1f", band.meanS)
    return if (band.estimated) "($value)" else value
}

/** RT60 per octave band at one seat, mean over its claps (FR-3). */
@Composable
fun Rt60Table(summary: Rt60Summary, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            OctaveBands.CENTRES_HZ.forEachIndexed { band, hz ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(bandLabel(hz), style = MaterialTheme.typography.labelSmall)
                    Text(rt60Cell(summary.bands[band]), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
