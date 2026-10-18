package me.investcompany.report

import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import java.io.OutputStream

/** Renders a [ReportDocument] into an Office Open XML text document (.docx). */
class WordReportWriter : ReportWriter {
    override val format = ReportFormat.WORD

    override fun write(document: ReportDocument, output: OutputStream) {
        XWPFDocument().use { docx ->
            docx.organizationHeader()
            docx.reportTitle(document)
            docx.headerFields(document.headerFields)
            document.sections.forEach { docx.section(it) }
            docx.signature()
            docx.write(output)
        }
    }

    private fun XWPFDocument.organizationHeader() {
        paragraph(Organization.NAME, bold = true, size = 14, align = ParagraphAlignment.CENTER)
        paragraph(Organization.DETAILS, size = 9, align = ParagraphAlignment.CENTER)
        paragraph(Organization.CONTACTS, size = 9, align = ParagraphAlignment.CENTER, spacingAfter = 240)
    }

    private fun XWPFDocument.reportTitle(document: ReportDocument) {
        paragraph(document.title, bold = true, size = 13, align = ParagraphAlignment.CENTER)
        paragraph(
            "Дата формирования: ${document.generatedAt}",
            size = 9,
            align = ParagraphAlignment.CENTER,
            spacingAfter = 200,
        )
    }

    private fun XWPFDocument.headerFields(fields: List<ReportField>) {
        if (fields.isEmpty()) return
        fields.forEach { field ->
            val paragraph = createParagraph().apply { spacingAfter = 40 }
            paragraph.run("${field.label}: ", bold = true, size = 10)
            paragraph.run(field.value, size = 10)
        }
        paragraph("", size = 6)
    }

    private fun XWPFDocument.section(section: ReportSection) {
        paragraph(section.title, bold = true, size = 11, spacingBefore = 160, spacingAfter = 80)
        section.fields.forEach { field ->
            val paragraph = createParagraph().apply { spacingAfter = 40 }
            paragraph.run("${field.label}: ", size = 10)
            paragraph.run(field.value, bold = true, size = 10)
        }
        section.table?.let { table(it) }
        section.note?.let { paragraph(it, size = 9, italic = true, spacingBefore = 80) }
    }

    private fun XWPFDocument.table(table: ReportTable) {
        val rowCount = table.rows.size + 1 + if (table.totals.isEmpty()) 0 else 1
        val poiTable = createTable(rowCount, table.columns.size)
        poiTable.setTableWidthPercent()

        table.columns.forEachIndexed { index, column ->
            poiTable.getRow(0).getCell(index).fill(column.title, bold = true, align = column.align, shaded = true)
        }
        table.rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { cellIndex, cell ->
                poiTable.getRow(rowIndex + 1).getCell(cellIndex)
                    .fill(cell.text, align = table.columns[cellIndex].align)
            }
        }
        if (table.totals.isNotEmpty()) {
            val totalsRow = poiTable.getRow(rowCount - 1)
            table.totals.forEachIndexed { index, cell ->
                totalsRow.getCell(index).fill(cell.text, bold = true, align = table.columns[index].align, shaded = true)
            }
        }
    }

    private fun XWPFDocument.signature() {
        paragraph(
            "Отчет сформирован автоматически информационной системой инвестиционной компании.",
            size = 9,
            italic = true,
            spacingBefore = 240,
        )
        val paragraph = createParagraph().apply { spacingBefore = 160 }
        paragraph.run("Ответственный сотрудник ____________________ / ____________________", size = 10)
    }

    private fun XWPFDocument.paragraph(
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        size: Int = 10,
        align: ParagraphAlignment = ParagraphAlignment.LEFT,
        spacingBefore: Int = 0,
        spacingAfter: Int = 0,
    ) {
        val paragraph = createParagraph()
        paragraph.alignment = align
        if (spacingBefore > 0) paragraph.spacingBefore = spacingBefore
        if (spacingAfter > 0) paragraph.spacingAfter = spacingAfter
        paragraph.run(text, bold = bold, italic = italic, size = size)
    }

    private fun XWPFParagraph.run(text: String, bold: Boolean = false, italic: Boolean = false, size: Int = 10) {
        createRun().also {
            it.isBold = bold
            it.isItalic = italic
            it.fontSize = size
            it.fontFamily = "Times New Roman"
            it.setText(text)
        }
    }

    private fun XWPFTableCell.fill(
        text: String,
        bold: Boolean = false,
        align: ReportAlign = ReportAlign.LEFT,
        shaded: Boolean = false,
    ) {
        if (shaded) setColor(HEADER_FILL)
        // A freshly created POI cell already owns one empty paragraph; reuse it instead of appending.
        val paragraph = paragraphs.firstOrNull() ?: addParagraph()
        paragraph.alignment = if (align == ReportAlign.RIGHT) ParagraphAlignment.RIGHT else ParagraphAlignment.LEFT
        paragraph.spacingAfter = 0
        paragraph.run(text, bold = bold, size = 9)
    }

    private fun XWPFTable.setTableWidthPercent() {
        val layout = ctTbl.tblPr ?: ctTbl.addNewTblPr()
        val width = layout.tblW ?: layout.addNewTblW()
        width.type = org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth.PCT
        width.w = java.math.BigInteger.valueOf(5000)
    }

    private companion object {
        const val HEADER_FILL = "E8F0E8"
    }
}
