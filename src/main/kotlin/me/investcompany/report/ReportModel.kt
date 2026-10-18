package me.investcompany.report

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Organization {
    const val NAME = "Инвестиционная компания «ИнвестКапитал»"
    const val DETAILS = "ИНН 7700000000 · Лицензия ФСФР №045-00000-100000"
    const val CONTACTS = "г. Москва · info@investcapital.local"
}

enum class ReportFormat(val extension: String, val title: String) {
    WORD("docx", "Документ Word (.docx)"),
    EXCEL("xlsx", "Книга Excel (.xlsx)"),
}

enum class ReportKind(
    val title: String,
    val description: String,
    val fileBaseName: String,
    val requiresAccount: Boolean,
    val requiresPeriod: Boolean,
) {
    BROKER_TRADES(
        title = "Брокерский отчет по сделкам",
        description = "Реестр сделок по выбранному счету за период с итоговым оборотом",
        fileBaseName = "brokerskiy-otchet",
        requiresAccount = true,
        requiresPeriod = true,
    ),
    PORTFOLIO_VALUATION(
        title = "Выписка по инвестиционному портфелю",
        description = "Открытые позиции, рыночная оценка и финансовый результат",
        fileBaseName = "vypiska-portfel",
        requiresAccount = true,
        requiresPeriod = false,
    ),
    INCOME(
        title = "Отчет о доходах: дивиденды и купоны",
        description = "Реестр начислений, удержанный НДФЛ и суммы к выплате",
        fileBaseName = "otchet-dohody",
        requiresAccount = true,
        requiresPeriod = true,
    ),
    ACCOUNT_OPENING(
        title = "Уведомление об открытии инвестиционного счета",
        description = "Реквизиты инвестора, номер счета и персональный менеджер",
        fileBaseName = "uvedomlenie-otkrytie-scheta",
        requiresAccount = true,
        requiresPeriod = false,
    ),
    CONSOLIDATED_ANALYTICS(
        title = "Сводный аналитический отчет по обороту и структуре активов",
        description = "Помесячный оборот, распределение капитала и счетчики системы",
        fileBaseName = "svodnyy-analiticheskiy-otchet",
        requiresAccount = false,
        requiresPeriod = false,
    ),
}

data class ReportRequest(
    val kind: ReportKind,
    val format: ReportFormat,
    val accountId: Long? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
)

data class ReportField(val label: String, val value: String)

data class ReportDocument(
    val kind: ReportKind,
    val generatedAt: String,
    val headerFields: List<ReportField>,
    val sections: List<ReportSection>,
) {
    val title: String get() = kind.title
}

data class ReportSection(
    val title: String,
    val fields: List<ReportField> = emptyList(),
    val table: ReportTable? = null,
    val note: String? = null,
)

data class ReportTable(
    val columns: List<ReportColumn>,
    val rows: List<List<ReportCell>>,
    val totals: List<ReportCell> = emptyList(),
)

enum class ReportAlign { LEFT, RIGHT }

data class ReportColumn(val title: String, val align: ReportAlign = ReportAlign.LEFT)

sealed interface ReportCell {
    val text: String

    data class Text(override val text: String) : ReportCell

    data class Money(val value: Double) : ReportCell {
        override val text: String get() = formatMoney(value)
    }

    data class Quantity(val value: Double) : ReportCell {
        override val text: String get() = formatQuantity(value)
    }

    data class Percent(val value: Double) : ReportCell {
        override val text: String get() = "${formatQuantity(value)} %"
    }
}

private val moneyFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.forLanguageTag("ru-RU")))

fun formatMoney(value: Double): String = "${moneyFormat.format(value)} ₽"

fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.US, value)
