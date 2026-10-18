package me.investcompany.report

import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream

/** Renders a [ReportDocument] into an Office Open XML workbook (.xlsx). */
class ExcelReportWriter : ReportWriter {
    override val format = ReportFormat.EXCEL

    override fun write(document: ReportDocument, output: OutputStream) {
        XSSFWorkbook().use { workbook ->
            val styles = Styles(workbook)
            val sheet = workbook.createSheet("Отчет")
            sheet.setColumnWidth(0, 32 * 256)
            repeat(MAX_COLUMNS - 1) { sheet.setColumnWidth(it + 1, 20 * 256) }

            var row = 0
            row = sheet.organizationHeader(styles, row)
            row = sheet.reportTitle(styles, document, row)
            row = sheet.headerFields(styles, document.headerFields, row)
            document.sections.forEach { section -> row = sheet.section(styles, section, row) }
            sheet.note(styles, "Отчет сформирован автоматически информационной системой инвестиционной компании.", row)

            workbook.write(output)
        }
    }

    private fun XSSFSheet.organizationHeader(styles: Styles, startRow: Int): Int {
        var row = startRow
        text(styles.organization, Organization.NAME, row++)
        text(styles.muted, Organization.DETAILS, row++)
        text(styles.muted, Organization.CONTACTS, row++)
        return row + 1
    }

    private fun XSSFSheet.reportTitle(styles: Styles, document: ReportDocument, startRow: Int): Int {
        var row = startRow
        text(styles.title, document.title, row++)
        text(styles.muted, "Дата формирования: ${document.generatedAt}", row++)
        return row + 1
    }

    private fun XSSFSheet.headerFields(styles: Styles, fields: List<ReportField>, startRow: Int): Int {
        if (fields.isEmpty()) return startRow
        var row = startRow
        fields.forEach { field ->
            val sheetRow = createRow(row++)
            sheetRow.createCell(0).apply { setCellValue(field.label); cellStyle = styles.fieldLabel }
            sheetRow.createCell(1).apply { setCellValue(field.value); cellStyle = styles.fieldValue }
        }
        return row + 1
    }

    private fun XSSFSheet.section(styles: Styles, section: ReportSection, startRow: Int): Int {
        var row = startRow
        text(styles.sectionTitle, section.title, row++)

        section.fields.forEach { field ->
            val sheetRow = createRow(row++)
            sheetRow.createCell(0).apply { setCellValue(field.label); cellStyle = styles.fieldLabel }
            sheetRow.createCell(1).apply { setCellValue(field.value); cellStyle = styles.fieldValue }
        }

        section.table?.let { table ->
            val headerRow = createRow(row++)
            table.columns.forEachIndexed { index, column ->
                headerRow.createCell(index).apply {
                    setCellValue(column.title)
                    cellStyle = styles.tableHeader
                }
            }
            table.rows.forEach { cells ->
                val dataRow = createRow(row++)
                cells.forEachIndexed { index, cell ->
                    dataRow.createCell(index).fill(cell, styles.body(table.columns[index].align), styles)
                }
            }
            if (table.totals.isNotEmpty()) {
                val totalsRow = createRow(row++)
                table.totals.forEachIndexed { index, cell ->
                    totalsRow.createCell(index).fill(cell, styles.totals(table.columns[index].align), styles)
                }
            }
        }

        section.note?.let { note(styles, it, row++) }
        return row + 1
    }

    private fun XSSFSheet.note(styles: Styles, text: String, row: Int) {
        text(styles.muted, text, row)
    }

    private fun XSSFSheet.text(style: XSSFCellStyle, value: String, row: Int) {
        val sheetRow = getRow(row) ?: createRow(row)
        sheetRow.createCell(0).apply {
            setCellValue(value)
            cellStyle = style
        }
        addMergedRegion(CellRangeAddress(row, row, 0, MAX_COLUMNS - 1))
    }

    /**
     * Numeric cells are written as real numbers so that the exported workbook stays usable for
     * further calculations in Excel, while textual cells keep their already formatted text.
     */
    private fun XSSFCell.fill(cell: ReportCell, style: XSSFCellStyle, styles: Styles) {
        when (cell) {
            is ReportCell.Money -> {
                setCellValue(cell.value)
                cellStyle = styles.money(style)
            }
            is ReportCell.Quantity -> {
                setCellValue(cell.value)
                cellStyle = styles.number(style)
            }
            is ReportCell.Percent -> {
                setCellValue(cell.value)
                cellStyle = styles.number(style)
            }
            is ReportCell.Text -> {
                setCellValue(cell.text)
                cellStyle = style
            }
        }
    }

    private class Styles(private val workbook: XSSFWorkbook) {
        private val moneyCache = mutableMapOf<XSSFCellStyle, XSSFCellStyle>()
        private val numberCache = mutableMapOf<XSSFCellStyle, XSSFCellStyle>()

        val organization: XSSFCellStyle = workbook.style(bold = true, size = 13, align = HorizontalAlignment.CENTER)
        val title: XSSFCellStyle = workbook.style(bold = true, size = 12, align = HorizontalAlignment.CENTER)
        val muted: XSSFCellStyle = workbook.style(size = 9, italic = true, align = HorizontalAlignment.CENTER)
        val sectionTitle: XSSFCellStyle = workbook.style(bold = true, size = 11)
        val fieldLabel: XSSFCellStyle = workbook.style(size = 10)
        val fieldValue: XSSFCellStyle = workbook.style(size = 10, bold = true)
        val tableHeader: XSSFCellStyle = workbook.style(bold = true, size = 10, bordered = true, filled = true)
        private val bodyLeft: XSSFCellStyle = workbook.style(size = 10, bordered = true)
        private val bodyRight: XSSFCellStyle =
            workbook.style(size = 10, bordered = true, align = HorizontalAlignment.RIGHT)
        private val totalsLeft: XSSFCellStyle = workbook.style(size = 10, bold = true, bordered = true, filled = true)
        private val totalsRight: XSSFCellStyle =
            workbook.style(size = 10, bold = true, bordered = true, filled = true, align = HorizontalAlignment.RIGHT)

        fun body(align: ReportAlign) = if (align == ReportAlign.RIGHT) bodyRight else bodyLeft

        fun totals(align: ReportAlign) = if (align == ReportAlign.RIGHT) totalsRight else totalsLeft

        fun money(base: XSSFCellStyle): XSSFCellStyle = moneyCache.getOrPut(base) { base.derive(MONEY_FORMAT) }

        fun number(base: XSSFCellStyle): XSSFCellStyle = numberCache.getOrPut(base) { base.derive(NUMBER_FORMAT) }

        private fun XSSFCellStyle.derive(pattern: String): XSSFCellStyle = workbook.createCellStyle().also {
            it.cloneStyleFrom(this)
            it.dataFormat = workbook.createDataFormat().getFormat(pattern)
        }
    }

    private companion object {
        const val MAX_COLUMNS = 8
        const val MONEY_FORMAT = "#,##0.00 \"₽\""
        const val NUMBER_FORMAT = "#,##0.##"
    }
}

private fun XSSFWorkbook.style(
    bold: Boolean = false,
    italic: Boolean = false,
    size: Int = 10,
    align: HorizontalAlignment = HorizontalAlignment.LEFT,
    bordered: Boolean = false,
    filled: Boolean = false,
): XSSFCellStyle = createCellStyle().also { style ->
    style.setFont(
        createFont().also {
            it.bold = bold
            it.italic = italic
            it.fontHeightInPoints = size.toShort()
            it.fontName = "Calibri"
        },
    )
    style.alignment = align
    style.verticalAlignment = VerticalAlignment.CENTER
    style.wrapText = true
    if (bordered) {
        style.borderTop = BorderStyle.THIN
        style.borderBottom = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
    }
    if (filled) {
        style.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
    }
}
