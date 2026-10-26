package com.clearroom.app.report

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.ui.screens.rowVerdict
import com.clearroom.app.ui.theme.color
import com.clearroom.app.viz.ChartColors
import com.clearroom.app.viz.ChartStyle
import com.clearroom.app.viz.Rt60ChartModel
import com.clearroom.app.viz.SeatMapMode
import com.clearroom.app.viz.SeatMapModel
import com.clearroom.app.viz.drawRt60Chart
import com.clearroom.app.viz.drawSeatMap
import kotlin.math.max

/** A4 in PDF points (1/72 inch). The page is drawn at density 1, so 1 dp and 1 sp are one point. */
object ReportPage {
    const val WIDTH_PT = 595
    const val HEIGHT_PT = 842
    const val MARGIN_PT = 36f
}

/**
 * Draws the one-page report (FR-12): header, verdict, the charts, the best fixes, how it was
 * measured, and the footer. The text is measured first and the charts get the space that's left,
 * so a long room name or a before/after pair still fits. The charts are the app's own DrawScope
 * functions, so the PDF holds sharp vectors, not pictures. Returns false if anything ran past the
 * footer (a test checks it never does).
 */
fun DrawScope.drawReport(report: Report, text: ReportText, measurer: TextMeasurer): Boolean =
    ReportPainter(this, measurer, report, text).paint()

private class LegendItem(val colour: Color, val label: String, val dashed: Boolean = false)

private class ReportPainter(
    private val scope: DrawScope,
    private val measurer: TextMeasurer,
    private val report: Report,
    private val text: ReportText,
) {
    private val left = ReportPage.MARGIN_PT
    private val width = scope.size.width - 2 * ReportPage.MARGIN_PT
    private val bottom = scope.size.height - ReportPage.MARGIN_PT
    private val columnWidth = (width - GUTTER) / 2
    private val rightColumn = left + columnWidth + GUTTER
    private val chartStyle = ChartStyle.print()
    private var y = ReportPage.MARGIN_PT

    fun paint(): Boolean {
        scope.drawRect(Color.White)
        header()
        verdict()
        val fixes = listOf(text.fixesTitle to HeadingStyle) + text.fixes.map { it to BodyStyle } + (text.fixesNote to SmallStyle)
        val conditions = listOf(text.conditionsTitle to HeadingStyle) + text.conditions.map { it to BodyStyle }
        val footer = text.footer.map { layout(it, SmallStyle, width) }
        val footerTop = bottom - footer.sumOf { it.size.height + LINE_GAP.toInt() }
        val belowCharts = height(fixes) + SECTION_GAP + height(conditions)
        charts(space = footerTop - SECTION_GAP - belowCharts - SECTION_GAP - y)
        y += SECTION_GAP
        fixes.forEach { (content, style) -> line(content, style) }
        y += SECTION_GAP
        conditions.forEach { (content, style) -> line(content, style) }
        val fits = y <= footerTop
        var footerY = footerTop
        footer.forEach {
            scope.drawText(it, topLeft = Offset(left, footerY))
            footerY += it.size.height + LINE_GAP
        }
        return fits
    }

    private fun header() {
        line(text.title, TitleStyle, gapAfter = 4f)
        line(text.roomLine, StrongStyle)
        line(text.sizeLine, BodyStyle.copy(color = Grey))
        text.sampleNote?.let { line(it, BodyStyle.copy(color = SampleWarning, fontWeight = FontWeight.Medium)) }
        y += 4f
        scope.drawLine(Divider, Offset(left, y), Offset(left + width, y), 0.75f)
        y += 8f
    }

    private fun verdict() {
        line(text.headline, HeadlineStyle, gapAfter = 3f)
        // The band's colour beside its name and range, as on the Results tab.
        val band = rowVerdict(report.now.clarity, report.now.clarity.rows - 1).band
        val verdict = layout(text.verdict, StrongStyle, width - SWATCH - SWATCH_GAP)
        scope.drawRoundRect(band.color(), Offset(left, y + (verdict.size.height - SWATCH) / 2), Size(SWATCH, SWATCH), CornerRadius(1.5f))
        scope.drawText(verdict, topLeft = Offset(left + SWATCH + SWATCH_GAP, y))
        y += verdict.size.height + 3f
        line(text.why, BodyStyle)
        text.notes.forEach { line(it, BodyStyle) }
        y += SECTION_GAP
    }

    private fun charts(space: Float) {
        val before = report.before
        if (before == null) chartsBesideEachOther(space) else beforeAndAfter(before, space)
    }

    /** One row: the seat map (V1) beside the echo times (V4). */
    private fun chartsBesideEachOther(space: Float) {
        val titles = titleHeight(text.mapTitles + text.echoTitles)
        val mapNotes = legendHeight(bandItems(), columnWidth) + LINE_GAP + layout(text.mapCaption, SmallStyle, columnWidth).size.height
        val mapBox = (space - titles - mapNotes - 2 * LINE_GAP).coerceIn(MIN_CHART, MAX_MAP)
        val echoBox = mapBox.coerceAtMost(MAX_ECHO)
        val top = y

        title(text.mapTitles[0], left, top)
        map(SeatMapModel(report.session.room, report.now.clarity, report.now.grid), left, top + titles, mapBox)
        var leftY = top + titles + mapBox + LINE_GAP
        leftY += legend(bandItems(), left, leftY, columnWidth) + LINE_GAP
        leftY += paragraph(text.mapCaption, SmallStyle, left, leftY, columnWidth)

        title(text.echoTitles[0], rightColumn, top)
        echo(report.now.echo, rightColumn, top + titles, echoBox)
        var rightY = top + titles + echoBox + LINE_GAP
        rightY += legend(echoItems(), rightColumn, rightY, columnWidth) + LINE_GAP
        rightY += paragraph(text.echoCaption, SmallStyle, rightColumn, rightY, columnWidth)

        y = max(leftY, rightY)
    }

    /** FR-13: the maps before and after, then the echo times before and after on one scale. */
    private fun beforeAndAfter(before: ReportSide, space: Float) {
        val mapTitles = titleHeight(text.mapTitles)
        val echoTitles = titleHeight(text.echoTitles)
        val notes = legendHeight(bandItems(), width) + layout(text.mapCaption, SmallStyle, width).size.height +
            legendHeight(echoItems(), width) + layout(text.echoCaption, SmallStyle, width).size.height
        val boxes = space - mapTitles - echoTitles - notes - SECTION_GAP - 6 * LINE_GAP
        val mapBox = (boxes * MAP_SHARE).coerceIn(MIN_CHART, MAX_MAP)
        val echoBox = (boxes - mapBox).coerceIn(MIN_CHART, MAX_ECHO)
        val room = report.session.room

        title(text.mapTitles[0], left, y)
        title(text.mapTitles[1], rightColumn, y)
        map(SeatMapModel(room, before.clarity, before.grid), left, y + mapTitles, mapBox)
        map(SeatMapModel(room, report.now.clarity, report.now.grid), rightColumn, y + mapTitles, mapBox)
        y += mapTitles + mapBox + LINE_GAP
        y += legend(bandItems(), left, y, width) + LINE_GAP
        y += paragraph(text.mapCaption, SmallStyle, left, y, width) + SECTION_GAP

        title(text.echoTitles[0], left, y)
        title(text.echoTitles[1], rightColumn, y)
        echo(before.echo, left, y + echoTitles, echoBox)
        echo(report.now.echo, rightColumn, y + echoTitles, echoBox)
        y += echoTitles + echoBox + LINE_GAP
        y += legend(echoItems(), left, y, width) + LINE_GAP
        y += paragraph(text.echoCaption, SmallStyle, left, y, width)
    }

    private fun map(model: SeatMapModel, x: Float, top: Float, boxHeight: Float) = inBox(x, top, boxHeight) {
        drawSeatMap(model, SeatMapMode.CLARITY, selected = null, style = chartStyle)
    }

    private fun echo(model: Rt60ChartModel, x: Float, top: Float, boxHeight: Float) = inBox(x, top, boxHeight) {
        drawRt60Chart(model, text.echoAxisX, text.echoAxisY, chartStyle, measurer, report.echoAxisTopS)
    }

    /** Runs [draw] with a column-wide, [boxHeight]-high drawing area at ([x], [top]). */
    private fun inBox(x: Float, top: Float, boxHeight: Float, draw: DrawScope.() -> Unit) =
        scope.inset(x, top, scope.size.width - x - columnWidth, scope.size.height - top - boxHeight, draw)

    private fun bandItems() = StiBand.entries.zip(text.legend) { band, label -> LegendItem(band.color(), label) }

    private fun echoItems(): List<LegendItem> {
        val tooLong = listOfNotNull(report.now, report.before).any { side -> side.echo.overTarget.any { it } }
        return listOf(LegendItem(chartStyle.axis, text.echoTarget, dashed = true)) +
            if (tooLong) listOf(LegendItem(ChartColors.TooLong, text.echoTooLong)) else emptyList()
    }

    /** Swatches with their names, wrapping like a FlowRow; returns the height used. */
    private fun legend(items: List<LegendItem>, x: Float, top: Float, maxWidth: Float, draw: Boolean = true): Float {
        var cx = x
        var cy = top
        var rowHeight = 0f
        items.forEach { item ->
            val label = layout(item.label, SmallStyle, maxWidth)
            val itemWidth = SWATCH + SWATCH_GAP + label.size.width
            if (cx > x && cx + itemWidth > x + maxWidth) {
                cx = x
                cy += rowHeight + LINE_GAP
                rowHeight = 0f
            }
            val itemHeight = max(SWATCH, label.size.height.toFloat())
            if (draw) {
                val middle = cy + itemHeight / 2
                if (item.dashed) {
                    scope.drawLine(item.colour, Offset(cx, middle), Offset(cx + SWATCH, middle), 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f, 1.5f)))
                } else {
                    scope.drawRect(item.colour, Offset(cx, middle - SWATCH / 2), Size(SWATCH, SWATCH))
                }
                scope.drawText(label, topLeft = Offset(cx + SWATCH + SWATCH_GAP, middle - label.size.height / 2f))
            }
            cx += itemWidth + LEGEND_GAP
            rowHeight = max(rowHeight, itemHeight)
        }
        return cy + rowHeight - top
    }

    private fun legendHeight(items: List<LegendItem>, maxWidth: Float) = legend(items, 0f, 0f, maxWidth, draw = false)

    private fun title(title: String, x: Float, top: Float) = paragraph(title, HeadingStyle, x, top, columnWidth)

    private fun titleHeight(titles: List<String>): Float = titles.maxOf { layout(it, HeadingStyle, columnWidth).size.height } + TITLE_GAP

    /** Draws [content] at ([x], [top]) wrapped to [maxWidth]; returns its height. */
    private fun paragraph(content: String, style: TextStyle, x: Float, top: Float, maxWidth: Float): Float {
        val laidOut = layout(content, style, maxWidth)
        scope.drawText(laidOut, topLeft = Offset(x, top))
        return laidOut.size.height.toFloat()
    }

    /** One full-width paragraph at [y], which then moves below it. */
    private fun line(content: String, style: TextStyle, gapAfter: Float = LINE_GAP) {
        y += paragraph(content, style, left, y, width) + gapAfter
    }

    private fun height(lines: List<Pair<String, TextStyle>>): Float = lines.sumOf { (content, style) -> layout(content, style, width).size.height + LINE_GAP.toDouble() }.toFloat()

    private fun layout(content: String, style: TextStyle, maxWidth: Float): TextLayoutResult =
        measurer.measure(content, style, constraints = Constraints(maxWidth = maxWidth.toInt()))
}

private val Ink = Color(0xFF1C1B1F)
private val Grey = Color(0xFF55575F)
private val Divider = Color(0xFFD5D5DC)

/** Dark amber: readable on white, and not a clarity band colour. */
private val SampleWarning = Color(0xFF8A5300)

private val TitleStyle = TextStyle(color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
private val HeadlineStyle = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val StrongStyle = TextStyle(color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Medium)
private val HeadingStyle = TextStyle(color = Ink, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
private val BodyStyle = TextStyle(color = Ink, fontSize = 8.sp)
private val SmallStyle = TextStyle(color = Grey, fontSize = 7.sp)

private const val LINE_GAP = 2f
private const val TITLE_GAP = 3f
private const val SECTION_GAP = 12f
private const val GUTTER = 16f
private const val SWATCH = 8f
private const val SWATCH_GAP = 4f
private const val LEGEND_GAP = 10f
private const val MIN_CHART = 90f
private const val MAX_MAP = 330f
private const val MAX_ECHO = 190f

/** With before and after, the maps get a little more of the chart space than the echo charts. */
private const val MAP_SHARE = 0.55f
