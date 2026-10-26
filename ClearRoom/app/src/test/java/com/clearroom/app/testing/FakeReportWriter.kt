package com.clearroom.app.testing

import com.clearroom.app.report.Report
import com.clearroom.app.report.ReportWriter
import java.io.File
import java.io.IOException

/** [ReportWriter] that keeps the reports it was given; [failWrites] makes it fail like a full disk. */
class FakeReportWriter : ReportWriter {
    val written = mutableListOf<Report>()
    var failWrites = false

    override fun write(report: Report): File {
        if (failWrites) throw IOException("fake: disk full")
        written += report
        return File("report-${written.size}.pdf")
    }
}
