package com.clearroom.app.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Writes the report as a one-page A4 PDF with Android's own [PdfDocument] (FR-12). Only the newest
 * report is kept in [dir]; the share sheet reads it from there through [ReportSharing].
 */
class PdfReportWriter(
    private val context: Context,
    private val dir: File,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ReportWriter {

    override fun write(report: Report): File {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("can't create $dir")
        dir.listFiles()?.forEach { it.delete() } // older reports: one is enough, and they may be stale
        val file = File(dir, fileName(report.createdAtMillis, zone()))
        val text = ReportText.of(report, context.resources, zone())
        val measurer = TextMeasurer(createFontFamilyResolver(context), PAGE_DENSITY, LayoutDirection.Ltr)
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(ReportPage.WIDTH_PT, ReportPage.HEIGHT_PT, 1).create())
            val pageSize = Size(ReportPage.WIDTH_PT.toFloat(), ReportPage.HEIGHT_PT.toFloat())
            CanvasDrawScope().draw(PAGE_DENSITY, LayoutDirection.Ltr, Canvas(page.canvas), pageSize) {
                drawReport(report, text, measurer)
            }
            document.finishPage(page)
            FileOutputStream(file).use { document.writeTo(it) }
        } catch (e: IOException) {
            file.delete()
            throw e
        } finally {
            document.close()
        }
        return file
    }

    companion object {
        /** The cache folder the FileProvider shares (res/xml/file_paths.xml); nothing else is shared. */
        const val FOLDER = "reports"

        /** One point per pixel: PDF pages are measured in points, so charts' dp and text's sp come out in points. */
        private val PAGE_DENSITY = Density(1f)

        /** "ClearRoom-report-20261027-1430.pdf": the user-typed room name never becomes a file name. */
        fun fileName(createdAtMillis: Long, zone: ZoneId): String =
            "ClearRoom-report-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT).withZone(zone).format(Instant.ofEpochMilli(createdAtMillis)) + ".pdf"
    }
}

/** Hands a report to the share sheet: email, chat, Drive, print. No network permission is needed for this. */
object ReportSharing {
    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /**
     * A chooser for sending [file] as a PDF. The receiving app gets read access to this one file
     * only, for as long as it needs it.
     */
    fun chooser(context: Context, file: File, title: String): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = PDF_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            // The ClipData carries the read grant through the chooser to the app picked.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    const val PDF_TYPE = "application/pdf"
}
