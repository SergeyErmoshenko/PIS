package me.autoservice.report

import me.autoservice.domain.WorkOrderFilter
import me.autoservice.domain.WorkOrderStatus
import me.autoservice.persistence.SqlDelightAutoServiceRepository
import org.apache.poi.ss.usermodel.CellType
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
    private lateinit var repository: SqlDelightAutoServiceRepository
    private lateinit var service: ReportService

    @BeforeTest
    fun setUp() {
        repository = SqlDelightAutoServiceRepository.inMemory(seed = true)
        service = ReportService(repository)
    }

    @AfterTest
    fun tearDown() = repository.close()

    /** Picks a client that actually has work orders, so the reports are not trivially empty. */
    private fun clientId() = repository.clients().first { client ->
        repository.workOrders().any { it.clientName == client.fullName }
    }.id

    private fun request(kind: ReportKind, format: ReportFormat) = ReportRequest(
        kind = kind,
        format = format,
        clientId = if (kind.requiresClient) clientId() else null,
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
                                CellType.NUMERIC -> cell.numericCellValue.toString()
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
        val docx = generate(request(ReportKind.CLIENT_WORK_ORDERS, ReportFormat.WORD))
        val xlsx = generate(request(ReportKind.CLIENT_WORK_ORDERS, ReportFormat.EXCEL))
        // Both OOXML containers are ZIP archives and must start with the "PK" local file header.
        listOf(docx, xlsx).forEach {
            assertEquals('P'.code.toByte(), it[0])
            assertEquals('K'.code.toByte(), it[1])
        }
    }

    @Test
    fun `client work order report lists orders and total amount`() {
        val clientId = clientId()
        val orders = repository.workOrders(WorkOrderFilter(clientId = clientId))
        val client = repository.clients().first { it.id == clientId }
        val text = wordText(generate(request(ReportKind.CLIENT_WORK_ORDERS, ReportFormat.WORD)))

        assertContains(text, client.fullName)
        assertContains(text, client.advisorName)
        orders.forEach { assertContains(text, it.serviceName) }
        assertContains(text, formatMoney(orders.sumOf { it.amount }))
    }

    @Test
    fun `client work order report respects the requested period`() {
        val clientId = clientId()
        val boundary = repository.workOrders(WorkOrderFilter(clientId = clientId)).minOf { it.openDate }
        val expected = repository.workOrders(
            WorkOrderFilter(clientId = clientId, dateFrom = boundary, dateTo = boundary),
        )

        val text = wordText(
            generate(
                ReportRequest(
                    kind = ReportKind.CLIENT_WORK_ORDERS,
                    format = ReportFormat.WORD,
                    clientId = clientId,
                    dateFrom = boundary,
                    dateTo = boundary,
                ),
            ),
        )
        assertContains(text, "с $boundary по $boundary")
        assertContains(text, formatMoney(expected.sumOf { it.amount }))
    }

    @Test
    fun `work order act sums works parts and payments`() {
        val clientId = clientId()
        val orders = repository.workOrders(WorkOrderFilter(clientId = clientId))
        val orderIds = orders.map { it.id }.toSet()
        val usages = repository.partUsages().filter { it.workOrderId in orderIds }
        val payments = repository.payments().filter { it.workOrderId in orderIds }

        val worksTotal = orders.sumOf { it.amount }
        val partsTotal = usages.sumOf { it.netAmount }
        val text = wordText(generate(request(ReportKind.WORK_ORDER_ACT, ReportFormat.WORD)))

        assertContains(text, formatMoney(worksTotal))
        assertContains(text, formatMoney(worksTotal + partsTotal))
        assertContains(text, formatMoney(payments.sumOf { it.netAmount }))
    }

    @Test
    fun `revenue statement totals match repository payments`() {
        val payments = repository.payments()
        val text = wordText(generate(request(ReportKind.REVENUE_STATEMENT, ReportFormat.WORD)))

        assertContains(text, formatMoney(payments.sumOf { it.netAmount }))
        assertContains(text, payments.size.toString())
    }

    @Test
    fun `revenue statement filters payments by period`() {
        val boundary = repository.payments().maxOf { it.paymentDate }
        val expected = repository.payments().filter { it.paymentDate == boundary }

        val text = wordText(
            generate(
                ReportRequest(
                    kind = ReportKind.REVENUE_STATEMENT,
                    format = ReportFormat.WORD,
                    dateFrom = boundary,
                    dateTo = boundary,
                ),
            ),
        )
        assertContains(text, formatMoney(expected.sumOf { it.netAmount }))
    }

    @Test
    fun `client profile exposes contacts vehicles and spending`() {
        val clientId = clientId()
        val client = repository.clients().first { it.id == clientId }
        val vehicles = repository.vehicles().filter { it.clientId == clientId }
        val spending = repository.clientSpendingSummaries().firstOrNull { it.clientId == clientId }
        val text = wordText(generate(request(ReportKind.CLIENT_PROFILE, ReportFormat.WORD)))

        assertContains(text, client.fullName)
        assertContains(text, client.phone)
        assertContains(text, client.email)
        assertContains(text, client.registrationDate)
        vehicles.forEach {
            assertContains(text, it.licensePlate)
            assertContains(text, it.vin)
        }
        assertContains(text, formatMoney(spending?.totalPaid ?: 0.0))
    }

    @Test
    fun `workshop analytics includes mechanics categories and counters`() {
        val dashboard = repository.dashboard()
        val mechanics = repository.workOrders()
            .filter { it.status == WorkOrderStatus.COMPLETED }
            .map { it.mechanicName }
            .distinct()
        val text = wordText(generate(request(ReportKind.WORKSHOP_ANALYTICS, ReportFormat.WORD)))

        assertContains(text, dashboard.clientCount.toString())
        assertContains(text, dashboard.workOrderCount.toString())
        mechanics.forEach { assertContains(text, it) }
        repository.categoryRevenue().forEach { assertContains(text, it.categoryName) }
        repository.monthlyRevenue().forEach { assertContains(text, it.month) }
    }

    @Test
    fun `excel export writes money cells as numbers`() {
        val bytes = generate(request(ReportKind.REVENUE_STATEMENT, ReportFormat.EXCEL))
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val numericCells = (0..sheet.lastRowNum).sumOf { rowIndex ->
                sheet.getRow(rowIndex)?.count { it.cellType == CellType.NUMERIC } ?: 0
            }
            assertTrue(numericCells > 0, "В книге Excel нет числовых ячеек")
        }
    }

    @Test
    fun `report is saved to directory without overwriting existing files`() {
        val directory = Files.createTempDirectory("reports").toFile()
        try {
            val request = request(ReportKind.CLIENT_WORK_ORDERS, ReportFormat.EXCEL)
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
    fun `reports requiring a client reject a missing client`() {
        ReportKind.entries.filter { it.requiresClient }.forEach { kind ->
            val error = assertFailsWith<IllegalArgumentException> {
                service.generate(ReportRequest(kind, ReportFormat.WORD, clientId = null), ByteArrayOutputStream())
            }
            assertContains(error.message.orEmpty(), "выберите клиента")
        }
    }

    @Test
    fun `invalid period is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            service.generate(
                ReportRequest(ReportKind.REVENUE_STATEMENT, ReportFormat.WORD, dateFrom = "01-01-2024"),
                ByteArrayOutputStream(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            service.generate(
                ReportRequest(
                    ReportKind.REVENUE_STATEMENT,
                    ReportFormat.WORD,
                    dateFrom = "2024-05-01",
                    dateTo = "2024-01-01",
                ),
                ByteArrayOutputStream(),
            )
        }
    }
}
