package com.clearroom.app.report

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.data.RtTake
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.Session
import com.clearroom.app.ui.theme.color
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import kotlin.math.abs

/** The PDF itself (FR-12): one A4 page that fits, drawn in the band colours, and shared safely. */
@RunWith(AndroidJUnit4::class)
class PdfReportWriterTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, PdfReportWriter.FOLDER)
    private val utc = ZoneId.of("UTC")
    private val writer = PdfReportWriter(context, dir) { utc }

    /** The sample's readings in the user's own room. */
    private val mine = SampleData.session.copy(room = SampleData.room.copy(id = "mine"))

    @After
    fun removeReports() {
        dir.deleteRecursively()
    }

    private fun reportOf(session: Session, at: Long = 1_800_000_000_000L) = requireNotNull(Report.of(session, at))

    /** [session] re-measured after a change that made every clap ring 30 % shorter. */
    private fun afterCurtains(session: Session): Session {
        val shorter = session.seats.map { seat -> seat.copy(rtTakes = seat.rtTakes.map { take -> RtTake(take.t60S.map { it?.times(0.7) }) }) }
        return session.startRemeasuring(1_700_000_000_000L).copy(seats = shorter)
    }

    private fun <T> withPdf(file: File, use: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use(use) }

    /** The page at twice its size, white behind it, as a viewer would show it. */
    private fun render(file: File): Bitmap = withPdf(file) { pdf ->
        pdf.openPage(0).use { page ->
            Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(android.graphics.Color.WHITE)
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    private fun Bitmap.hasColour(argb: Int): Boolean {
        val pixels = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
        fun near(a: Int, b: Int, shift: Int) = abs((a shr shift and 0xFF) - (b shr shift and 0xFF)) <= 3
        return pixels.count { near(it, argb, 16) && near(it, argb, 8) && near(it, argb, 0) } > 50
    }

    /** Draws the report on a bitmap and returns whether everything fitted above the footer. */
    private fun fits(report: Report): Boolean {
        val bitmap = Bitmap.createBitmap(ReportPage.WIDTH_PT, ReportPage.HEIGHT_PT, Bitmap.Config.ARGB_8888)
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
        var fitted = false
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bitmap)), Size(ReportPage.WIDTH_PT.toFloat(), ReportPage.HEIGHT_PT.toFloat())) {
            fitted = drawReport(report, ReportText.of(report, context.resources, utc), measurer)
        }
        return fitted
    }

    @Test
    fun theReport_isOneA4PageNamedByWhenItWasMade() {
        val file = writer.write(reportOf(mine))
        assertEquals("ClearRoom-report-20270115-0800.pdf", file.name)
        assertEquals(dir, file.parentFile)
        withPdf(file) { pdf ->
            assertEquals(1, pdf.pageCount)
            pdf.openPage(0).use { page ->
                assertEquals(ReportPage.WIDTH_PT, page.width)
                assertEquals(ReportPage.HEIGHT_PT, page.height)
            }
        }
    }

    @Test
    fun thePage_drawsEverySeatInItsClarityBandColour() {
        val report = reportOf(mine)
        val page = render(writer.write(report))
        report.now.clarity.seats.map { it.band }.distinct().forEach { band ->
            assertTrue("no $band seats on the page", page.hasColour(band.color().toArgb()))
        }
    }

    @Test
    fun aBeforeAndAfterReport_isStillOnePage_withBothSidesDrawn() {
        val report = reportOf(afterCurtains(mine))
        val page = render(writer.write(report))
        withPdf(writer.write(report)) { assertEquals(1, it.pageCount) }
        (report.now.clarity.seats + requireNotNull(report.before).clarity.seats).map { it.band }.distinct().forEach { band ->
            assertTrue("no $band seats on the page", page.hasColour(band.color().toArgb()))
        }
    }

    @Test
    fun everyKindOfReport_fitsAboveTheFooter() {
        val longName = mine.copy(room = mine.room.copy(name = "Physics and chemistry laboratory, second floor, east wing, room 214 B"))
        listOf(SampleData.session, mine, mine.copy(voice = null), longName, afterCurtains(mine), afterCurtains(longName.copy(voice = null))).forEach { session ->
            assertTrue("${session.room.name}, comparing: ${session.baseline != null}", fits(reportOf(session)))
        }
    }

    @Test
    fun aNewReport_replacesTheOlderOne() {
        val first = writer.write(reportOf(mine, at = 1_700_000_000_000L))
        val second = writer.write(reportOf(mine, at = 1_800_000_000_000L))
        assertFalse(first.exists())
        assertEquals(listOf(second.name), dir.list()?.toList())
    }

    @Test
    fun theShareSheet_getsTheReportAsAReadablePdf() {
        val file = writer.write(reportOf(mine))
        val chooser = ReportSharing.chooser(context, file, "Share")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = requireNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(ReportSharing.PDF_TYPE, send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = requireNotNull(IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals("content", uri.scheme)
        assertEquals(ReportSharing.authority(context), uri.authority)
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
        assertArrayEquals(file.readBytes(), bytes)
        assertEquals("%PDF", String(bytes, 0, 4, Charsets.US_ASCII))
    }

    @Test
    fun onlyTheReportFolderIsShared_neverTheRoomsOrLogs() {
        val roomFile = File(context.filesDir, "rooms/shared-test.json").apply {
            parentFile?.mkdirs()
            writeText("{}")
        }
        try {
            assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(context, ReportSharing.authority(context), roomFile) }
        } finally {
            roomFile.delete()
        }
    }
}
