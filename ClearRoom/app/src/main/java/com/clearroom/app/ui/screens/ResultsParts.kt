package com.clearroom.app.ui.screens

import com.clearroom.app.ui.theme.Lime
import com.clearroom.app.ui.theme.Ink
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.annotation.DrawableRes
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.acoustics.Cause
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.ui.AppTab
import com.clearroom.app.ui.theme.ClearRoomTheme
import com.clearroom.app.ui.theme.color
import com.clearroom.app.viz.decimals

// Pieces every tab shares: the screen header, cards, the hero card, the band-with-range line, the
// band legend, and the plain-word names of bands and causes (NFR-2).

/**
 * A tab's big title, with [action] (a lime pill or a chip) at the right and [subtitle] under it.
 * The introduction ([intro]) shows only while the tab has nothing else to show: once there are
 * results, they speak for themselves.
 */
@Composable
internal fun ScreenHeader(
    tab: AppTab,
    @StringRes intro: Int? = null,
    subtitle: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(tab.titleRes), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        subtitle?.invoke()
        intro?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** A small rounded tag in the header: the open room, or the date of what is shown. */
@Composable
internal fun InfoChip(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .widthIn(max = CHIP_MAX_WIDTH)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The header's action: a lime pill with ink text in both themes ("Edit room", "Stop"). */
@Composable
internal fun ActionPill(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Lime,
            contentColor = Ink,
            disabledContainerColor = Lime.copy(alpha = 0.4f),
            disabledContentColor = Ink.copy(alpha = 0.6f),
        ),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Two or three views of the same thing, as a lime pill in a grey track ("3D room | Seat map |
 * Causes"). Each option is a tab for TalkBack and tests, named by its visible text.
 */
@Composable
internal fun PillToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .selectableGroup()
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isSelected) Lime else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Ink else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A full-width pill for the screen's main next step ("View seat details"): ink by day, an
 * outlined dark pill at night. [strong] = false gives a quieter outlined one for a second action.
 */
@Composable
internal fun WideButton(label: String, onClick: () -> Unit, enabled: Boolean = true, strong: Boolean = true, chevron: Boolean = false) {
    val brand = ClearRoomTheme.brand
    val modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    val content: @Composable RowScope.() -> Unit = {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
    if (strong) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = CircleShape,
            border = brand.strongButtonBorder?.let { BorderStroke(1.dp, it) },
            colors = ButtonDefaults.buttonColors(containerColor = brand.strongButton, contentColor = brand.onStrongButton),
            content = content,
        )
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = CircleShape, content = content)
    }
}

/** A labelled number in a small outlined box: "Length / 6 m", "Echo / 1.1 → 0.8 s". */
@Composable
internal fun StatTile(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
        detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** The four clarity bands as coloured bars with their names and score ranges: the key to every clarity colour. */
@Composable
internal fun BandScale(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StiBand.entries.forEach { band ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(10.dp).background(band.color(), CircleShape))
                Text(stringResource(band.labelRes()), style = MaterialTheme.typography.labelLarge)
                Text(bandBoundsText(band), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "< 0.45", "0.45–0.60", "≥ 0.75": where a band starts and ends on the 0–1 scale. */
@Composable
internal fun bandBoundsText(band: StiBand): String {
    val next = StiBand.entries.getOrNull(band.ordinal + 1)
    return when {
        next == null -> stringResource(R.string.band_bounds_from, decimals(band.lowerBound, 2))
        band.ordinal == 0 -> stringResource(R.string.band_bounds_below, decimals(next.lowerBound, 2))
        else -> stringResource(R.string.band_bounds_between, decimals(band.lowerBound, 2), decimals(next.lowerBound, 2))
    }
}

/** A band's name on its own colour, like a tag: readable on any card, unlike coloured text. */
@Composable
internal fun BandTag(band: StiBand, large: Boolean = false) {
    Text(
        stringResource(band.labelRes()),
        style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
        color = Ink,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(band.color())
            .padding(horizontal = if (large) 12.dp else 8.dp, vertical = if (large) 2.dp else 1.dp),
    )
}

/**
 * The big number of a headline card, lime at night and ink by day. It shrinks to fit its width,
 * so a range like "0.57–0.58" stays on one line on a small phone.
 */
@Composable
internal fun HeadlineNumber(text: String, modifier: Modifier = Modifier, color: Color = ClearRoomTheme.brand.headline, maxSize: TextUnit = 44.sp) {
    BasicText(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.displayMedium.copy(color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = maxSize, stepSize = 1.sp),
    )
}

private val CHIP_MAX_WIDTH = 190.dp

/**
 * "Fair · 0.55–0.61" next to the band's colour: the verdict with its uncertainty range (NFR-10).
 * [large] is the hero card's headline number.
 */
@Composable
internal fun BandRange(band: StiBand, low: Double, high: Double, large: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) 12.dp else 8.dp)) {
        val swatch = if (large) 22.dp else 14.dp
        Box(Modifier.size(swatch).background(band.color(), RoundedCornerShape(if (large) 6.dp else 3.dp)))
        if (large) {
            Text(rangeText(band, low, high), style = MaterialTheme.typography.headlineSmall)
        } else {
            Text(rangeText(band, low, high), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** "Fair · 0.55–0.61", or "Fair · 0.59" when the range is narrower than the digits shown. */
@Composable
internal fun rangeText(band: StiBand, low: Double, high: Double): String = rangeText(LocalResources.current, band, low, high)

/** [rangeText] outside Compose, for the report (FR-12). */
fun rangeText(resources: Resources, band: StiBand, low: Double, high: Double): String {
    val (lowText, highText) = decimals(low, 2) to decimals(high, 2)
    val name = resources.getString(band.labelRes())
    return if (lowText == highText) {
        resources.getString(R.string.results_band_value, name, lowText)
    } else {
        resources.getString(R.string.results_band_range, name, lowText, highText)
    }
}

/** The four clarity bands with their fixed colours; colour is never the only cue (names shown too). */
@Composable
fun BandLegend(modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StiBand.entries.forEach { band ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(12.dp).background(band.color(), RoundedCornerShape(2.dp)))
                Text(stringResource(band.labelRes()), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@StringRes
fun StiBand.labelRes(): Int = when (this) {
    StiBand.POOR -> R.string.band_poor
    StiBand.FAIR -> R.string.band_fair
    StiBand.GOOD -> R.string.band_good
    StiBand.EXCELLENT -> R.string.band_excellent
}

/** The band in plain words (NFR-2): what listening there is like. */
@StringRes
fun StiBand.phraseRes(): Int = when (this) {
    StiBand.POOR -> R.string.band_phrase_poor
    StiBand.FAIR -> R.string.band_phrase_fair
    StiBand.GOOD -> R.string.band_phrase_good
    StiBand.EXCELLENT -> R.string.band_phrase_excellent
}

@StringRes
fun Cause.nameRes(): Int = when (this) {
    Cause.ECHO -> R.string.cause_echo
    Cause.NOISE -> R.string.cause_noise
    Cause.DISTANCE -> R.string.cause_distance
}

/** Why this cause hurts, and the change that helps most. */
@StringRes
fun Cause.whyRes(): Int = when (this) {
    Cause.ECHO -> R.string.cause_echo_why
    Cause.NOISE -> R.string.cause_noise_why
    Cause.DISTANCE -> R.string.cause_distance_why
}

@Composable
internal fun ChartCard(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    InfoCard {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** The everyday card: white (dark grey at night) on the background, rounded, no shadow. */
@Composable
internal fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/**
 * The one bold card on a screen, like a balance card: the verdict. Black with white text in the
 * light theme, lime with black text in the dark one; text inside takes its colour automatically.
 */
@Composable
internal fun HeroCard(content: @Composable ColumnScope.() -> Unit) {
    val brand = ClearRoomTheme.brand
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = brand.hero, contentColor = brand.onHero),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** The hero card's action: a pill in the accent that stands out on [HeroCard]. */
@Composable
internal fun HeroButton(@StringRes label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    val brand = ClearRoomTheme.brand
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = brand.heroAccent,
            contentColor = brand.onHeroAccent,
            disabledContainerColor = brand.heroAccent.copy(alpha = 0.4f),
            disabledContentColor = brand.onHeroAccent.copy(alpha = 0.6f),
        ),
    ) { Text(stringResource(label)) }
}

@Composable
internal fun Body(@StringRes text: Int) = Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)

/**
 * A button that asks before it acts: [button] draws it with the click that opens the question,
 * and [onConfirm] runs only on [confirm]. For changes that are hard to undo.
 */
@Composable
internal fun ConfirmFirst(
    @StringRes title: Int,
    body: String,
    @StringRes confirm: Int,
    @StringRes dismiss: Int,
    onConfirm: () -> Unit,
    button: @Composable (onClick: () -> Unit) -> Unit,
) {
    var asking by remember { mutableStateOf(false) }
    button { asking = true }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(title)) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    onConfirm()
                }) { Text(stringResource(confirm)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(dismiss)) } },
        )
    }
}
