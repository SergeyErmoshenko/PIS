package me.investcompany.report

import me.investcompany.domain.InvestmentRepository
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Entry point of the reporting module: assembles report data through [ReportBuilder] and renders it
 * with the writer matching the requested [ReportFormat].
 */
class ReportService(
    repository: InvestmentRepository,
    private val builder: ReportBuilder = ReportBuilder(repository),
    writers: List<ReportWriter> = listOf(WordReportWriter(), ExcelReportWriter()),
) {
    private val writersByFormat = writers.associateBy { it.format }

    fun generate(request: ReportRequest, output: OutputStream) {
        validate(request)
        val document = builder.build(request)
        val writer = writersByFormat[request.format] ?: error("Формат ${request.format} не поддерживается")
        writer.write(document, output)
    }

    fun saveTo(request: ReportRequest, file: File): File {
        file.parentFile?.mkdirs()
        file.outputStream().buffered().use { generate(request, it) }
        return file
    }

    /** Saves the report into [directory] under an automatically generated, collision-free name. */
    fun saveToDirectory(request: ReportRequest, directory: File): File =
        saveTo(request, uniqueFile(directory, suggestedFileName(request)))

    fun suggestedFileName(request: ReportRequest): String {
        val stamp = LocalDateTime.now().format(FILE_STAMP)
        return "${request.kind.fileBaseName}-$stamp.${request.format.extension}"
    }

    private fun validate(request: ReportRequest) {
        if (request.kind.requiresAccount) {
            requireNotNull(request.accountId) { "Для отчета «${request.kind.title}» выберите счет" }
        }
        listOfNotNull(request.dateFrom?.ifBlank { null }, request.dateTo?.ifBlank { null }).forEach {
            require(it.matches(DATE_PATTERN)) { "Дата должна иметь формат ГГГГ-ММ-ДД" }
        }
        val from = request.dateFrom?.ifBlank { null }
        val to = request.dateTo?.ifBlank { null }
        if (from != null && to != null) {
            require(from <= to) { "Начало периода не может быть позже его окончания" }
        }
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val candidate = File(directory, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.')
        var index = 1
        while (true) {
            val next = File(directory, "$base($index).$extension")
            if (!next.exists()) return next
            index++
        }
    }

    private companion object {
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
        val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}
