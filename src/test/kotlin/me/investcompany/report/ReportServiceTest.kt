package me.investcompany.report

import me.investcompany.persistence.SqlDelightInvestmentRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReportServiceTest {
    private lateinit var repository: SqlDelightInvestmentRepository
    private lateinit var service: ReportService

    @BeforeTest
    fun setUp() {
        repository = SqlDelightInvestmentRepository.inMemory(seed = true)
        service = ReportService(repository)
    }

    @AfterTest
    fun tearDown() = repository.close()

    private fun accountId() = repository.accounts().first { account ->
        repository.trades().any { it.accountId == account.id }
    }.id

    private fun request(kind: ReportKind, format: ReportFormat) = ReportRequest(
        kind = kind,
        format = format,
        accountId = if (kind.requiresAccount) accountId() else null,
    )

    private fun generate(request: ReportRequest): ByteArray =
        ByteArrayOutputStream().also { service.generate(request, it) }.toByteArray()

    private fun wordText(bytes: ByteArray): String =
        XWPFDocument(ByteArrayInputStream(bytes)).use { document ->
            buildString {
                document.paragraphs.forEach { appendLine(it.text) }
                document.tables.forEach { table ->
                    table.rows.forEach { row -> row.tableCells.forEach { appendLine(it.text) } }
                }
            }
        }

    private fun excelText(bytes: ByteArray): String =
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            buildString {
                val sheet = workbook.getSheetAt(0)
                (0..sheet.lastRowNum).forEach { rowIndex ->
                    sheet.getRow(rowIndex)?.forEach { cell ->
                        appendLine(
                            when (cell.cellType) {
                                org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toString()
                                else -> cell.stringCellValue
                            },
                        )
                    }
                }
            }
        }

    @Test
    fun `every report kind is generated in both formats`() {
        ReportKind.entries.forEach { kind ->
            ReportFormat.entries.forEach { format ->
                val bytes = generate(request(kind, format))
                assertTrue(bytes.isNotEmpty(), "Отчет $kind в формате $format пуст")
                val text = if (format == ReportFormat.WORD) wordText(bytes) else excelText(bytes)
                assertContains(text, kind.title, message = "В отчете $kind/$format нет заголовка")
                assertContains(text, Organization.NAME, message = "В отчете $kind/$format нет шапки организации")
            }
        }
    }

    @Test
    fun `generated files carry valid OOXML signatures`() {
        val docx = generate(request(ReportKind.BROKER_TRADES, ReportFormat.WORD))
        val xlsx = generate(request(ReportKind.BROKER_TRADES, ReportFormat.EXCEL))
        // Both OOXML containers are ZIP archives and must start with the "PK" local file header.
        listOf(docx, xlsx).forEach {
            assertEquals('P'.code.toByte(), it[0])
            assertEquals('K'.code.toByte(), it[1])
        }
    }

    @Test
    fun `broker trade report contains account trades and total turnover`() {
        val accountId = accountId()
        val trades = repository.trades().filter { it.accountId == accountId }
        val text = wordText(generate(request(ReportKind.BROKER_TRADES, ReportFormat.WORD)))

        val account = repository.accounts().first { it.id == accountId }
        assertContains(text, account.accountNumber)
        trades.forEach { assertContains(text, it.ticker) }
        assertContains(text, formatMoney(trades.sumOf { it.amount }))
    }

    @Test
    fun `broker trade report respects the requested period`() {
        val accountId = accountId()
        val dates = repository.trades().filter { it.accountId == accountId }.map { it.date }.sorted()
        val boundary = dates.first()
        val request = ReportRequest(
            kind = ReportKind.BROKER_TRADES,
            format = ReportFormat.WORD,
            accountId = accountId,
            dateFrom = boundary,
            dateTo = boundary,
        )

        val expected = repository.trades().count { it.accountId == accountId && it.date == boundary }
        val text = wordText(generate(request))
        assertContains(text, "с $boundary по $boundary")
        assertContains(text, "Количество сделок")
        assertContains(text, expected.toString())
    }

    @Test
    fun `portfolio report totals match repository positions`() {
        val accountId = accountId()
        val positions = repository.positions().filter { it.accountId == accountId }
        val text = wordText(generate(request(ReportKind.PORTFOLIO_VALUATION, ReportFormat.WORD)))

        positions.forEach { assertContains(text, it.ticker) }
        assertContains(text, formatMoney(positions.sumOf { it.currentValue }))
    }

    @Test
    fun `income report sums dividends and coupons with withheld tax`() {
        val accountId = accountId()
        val dividends = repository.dividends().filter { it.accountId == accountId }
        val coupons = repository.coupons().filter { it.accountId == accountId }
        val text = wordText(generate(request(ReportKind.INCOME, ReportFormat.WORD)))

        assertContains(text, formatMoney(dividends.sumOf { it.amount } + coupons.sumOf { it.amount }))
        assertContains(text, formatMoney(dividends.sumOf { it.taxAmount } + coupons.sumOf { it.taxAmount }))
        assertContains(text, formatMoney(dividends.sumOf { it.netAmount } + coupons.sumOf { it.netAmount }))
    }

    @Test
    fun `account opening confirmation exposes investor details`() {
        val accountId = accountId()
        val account = repository.accounts().first { it.id == accountId }
        val client = repository.clients().first { it.id == account.clientId }
        val text = wordText(generate(request(ReportKind.ACCOUNT_OPENING, ReportFormat.WORD)))

        assertContains(text, client.fullName)
        assertContains(text, client.passportNumber)
        assertContains(text, client.phone)
        assertContains(text, client.email)
        assertContains(text, client.managerName)
        assertContains(text, account.accountNumber)
        assertContains(text, account.openedAt)
        assertContains(text, account.currency)
    }

    @Test
    fun `consolidated analytics report includes dashboard counters and allocation`() {
        val dashboard = repository.dashboard()
        val text = wordText(generate(request(ReportKind.CONSOLIDATED_ANALYTICS, ReportFormat.WORD)))

        assertContains(text, dashboard.clientCount.toString())
        assertContains(text, dashboard.tradeCount.toString())
        repository.assetAllocation().forEach { assertContains(text, it.typeName) }
        repository.monthlyTurnover().forEach { assertContains(text, it.month) }
    }

    @Test
    fun `excel export writes money cells as numbers`() {
        val bytes = generate(request(ReportKind.PORTFOLIO_VALUATION, ReportFormat.EXCEL))
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val numericCells = (0..sheet.lastRowNum).sumOf { rowIndex ->
                sheet.getRow(rowIndex)
                    ?.count { it.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC }
                    ?: 0
            }
            assertTrue(numericCells > 0, "В книге Excel нет числовых ячеек")
        }
    }

    @Test
    fun `report is saved to directory with a generated file name`() {
        val directory = Files.createTempDirectory("reports").toFile()
        try {
            val request = request(ReportKind.BROKER_TRADES, ReportFormat.EXCEL)
            val first = service.saveToDirectory(request, directory)
            val second = service.saveToDirectory(request, directory)

            assertTrue(first.exists() && first.length() > 0)
            assertTrue(second.exists() && second.length() > 0)
            assertTrue(first.name.endsWith(".xlsx"))
            assertTrue(first.absolutePath != second.absolutePath, "Существующий файл не должен перезаписываться")
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `reports requiring an account reject a missing account`() {
        ReportKind.entries.filter { it.requiresAccount }.forEach { kind ->
            val error = assertFailsWith<IllegalArgumentException> {
                service.generate(ReportRequest(kind, ReportFormat.WORD, accountId = null), ByteArrayOutputStream())
            }
            assertContains(error.message.orEmpty(), "выберите счет")
        }
    }

    @Test
    fun `invalid period is rejected`() {
        val accountId = accountId()
        assertFailsWith<IllegalArgumentException> {
            service.generate(
                ReportRequest(ReportKind.INCOME, ReportFormat.WORD, accountId, dateFrom = "01-01-2024"),
                ByteArrayOutputStream(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            service.generate(
                ReportRequest(ReportKind.INCOME, ReportFormat.WORD, accountId, "2024-05-01", "2024-01-01"),
                ByteArrayOutputStream(),
            )
        }
    }
}
