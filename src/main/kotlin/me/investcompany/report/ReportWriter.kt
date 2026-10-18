package me.investcompany.report

import java.io.OutputStream

/** Renders a prepared [ReportDocument] into a concrete office file format. */
interface ReportWriter {
    val format: ReportFormat

    fun write(document: ReportDocument, output: OutputStream)
}
