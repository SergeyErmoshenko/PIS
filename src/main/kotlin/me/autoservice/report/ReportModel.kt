package me.autoservice.report

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Organization {
    const val NAME = "Автосервис «Мастерская на Колёсах»"
    const val DETAILS = "ИНН 7800000000 · Сервисный центр полного цикла"
    const val CONTACTS = "г. Санкт-Петербург · service@autoservice.local"
}

enum class ReportFormat(val extension: String, val title: String) {
    WORD("docx", "Документ Word (.docx)"),
    EXCEL("xlsx", "Книга Excel (.xlsx)"),
}

enum class ReportKind(
    val title: String,
    val description: String,
    val fileBaseName: String,
    val requiresClient: Boolean,
    val requiresPeriod: Boolean,
) {
    CLIENT_WORK_ORDERS(
        title = "Заказ-наряды клиента",
        description = "История работ по выбранному клиенту за период с суммами и скидками",
        fileBaseName = "zakaz-naryady-klienta",
        requiresClient = true,
        requiresPeriod = true,
    ),
    WORK_ORDER_ACT(
        title = "Акт выполненных работ",
        description = "Документ для клиента: услуги, израсходованные запчасти и сумма к оплате",
        fileBaseName = "akt-vypolnennyh-rabot",
        requiresClient = true,
        requiresPeriod = true,
    ),
    REVENUE_STATEMENT(
        title = "Отчет по выручке за период",
        description = "Поступления по платежам, стоимость запчастей и помесячная разбивка",
        fileBaseName = "otchet-vyruchka",
        requiresClient = false,
        requiresPeriod = true,
    ),
    CLIENT_PROFILE(
        title = "Карточка клиента автосервиса",
        description = "Реквизиты клиента, автомобили в обслуживании и итоговая сумма оплат",
        fileBaseName = "kartochka-klienta",
        requiresClient = true,
        requiresPeriod = false,
    ),
    WORKSHOP_ANALYTICS(
        title = "Аналитический отчет по загрузке сервиса",
        description = "Загрузка механиков, выручка по категориям услуг и счетчики системы",
        fileBaseName = "analiticheskiy-otchet",
        requiresClient = false,
        requiresPeriod = false,
    ),
}

data class ReportRequest(
    val kind: ReportKind,
    val format: ReportFormat,
    val clientId: Long? = null,
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
