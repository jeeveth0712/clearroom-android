package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.ui.theme.ClearRoomTheme

/** Title and a plain-language explanation of what a tab is for; placeholder until its milestone lands. */
@Composable
fun TabIntro(@StringRes titleRes: Int, @StringRes bodyRes: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun TabIntroPreview() {
    ClearRoomTheme { TabIntro(R.string.tab_room, R.string.room_intro) }
}
