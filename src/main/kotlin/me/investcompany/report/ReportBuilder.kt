package me.investcompany.report

import me.investcompany.domain.Account
import me.investcompany.domain.AccountStatus
import me.investcompany.domain.Client
import me.investcompany.domain.InvestmentRepository
import me.investcompany.domain.TradeFilter
import me.investcompany.domain.TradeType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Collects domain data for a [ReportRequest] and shapes it into a renderer-agnostic [ReportDocument],
 * so that the Word and Excel writers stay free of any business logic.
 */
class ReportBuilder(private val repository: InvestmentRepository) {

    fun build(request: ReportRequest): ReportDocument = when (request.kind) {
        ReportKind.BROKER_TRADES -> brokerTrades(request)
        ReportKind.PORTFOLIO_VALUATION -> portfolioValuation(request)
        ReportKind.INCOME -> income(request)
        ReportKind.ACCOUNT_OPENING -> accountOpening(request)
        ReportKind.CONSOLIDATED_ANALYTICS -> consolidatedAnalytics(request)
    }

    private fun brokerTrades(request: ReportRequest): ReportDocument {
        val account = requireAccount(request)
        val client = clientOf(account)
        val trades = repository.trades(
            TradeFilter(
                accountId = account.id,
                dateFrom = request.dateFrom?.ifBlank { null },
                dateTo = request.dateTo?.ifBlank { null },
            ),
        ).sortedBy { it.date }

        val turnover = trades.sumOf { it.amount }
        val commission = trades.sumOf { it.commission }
        val buyTurnover = trades.filter { it.type == TradeType.BUY }.sumOf { it.amount }
        val sellTurnover = trades.filter { it.type == TradeType.SELL }.sumOf { it.amount }

        val table = ReportTable(
            columns = listOf(
                ReportColumn("Дата"),
                ReportColumn("Тикер"),
                ReportColumn("Инструмент"),
                ReportColumn("Тип"),
                ReportColumn("Цена", ReportAlign.RIGHT),
                ReportColumn("Объем", ReportAlign.RIGHT),
                ReportColumn("Комиссия", ReportAlign.RIGHT),
                ReportColumn("Сумма", ReportAlign.RIGHT),
            ),
            rows = trades.map { trade ->
                listOf(
                    ReportCell.Text(trade.date),
                    ReportCell.Text(trade.ticker),
                    ReportCell.Text(trade.instrumentName),
                    ReportCell.Text(trade.type.label()),
                    ReportCell.Money(trade.unitPrice),
                    ReportCell.Quantity(trade.quantity),
                    ReportCell.Money(trade.commission),
                    ReportCell.Money(trade.amount),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого"),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Quantity(trades.sumOf { it.quantity }),
                ReportCell.Money(commission),
                ReportCell.Money(turnover),
            ),
        )

        return ReportDocument(
            kind = request.kind,
            generatedAt = now(),
            headerFields = clientHeader(client, account) + periodField(request),
            sections = listOf(
                ReportSection(
                    title = "Реестр сделок",
                    table = table,
                    note = if (trades.isEmpty()) "За выбранный период сделки по счету не зарегистрированы." else null,
                ),
                ReportSection(
                    title = "Итоговые показатели",
                    fields = listOf(
                        ReportField("Количество сделок", trades.size.toString()),
                        ReportField("Оборот по покупкам", formatMoney(buyTurnover)),
                        ReportField("Оборот по продажам", formatMoney(sellTurnover)),
                        ReportField("Удержано комиссии", formatMoney(commission)),
                        ReportField("Итоговый оборот", formatMoney(turnover)),
                    ),
                ),
            ),
        )
    }

    private fun portfolioValuation(request: ReportRequest): ReportDocument {
        val account = requireAccount(request)
        val client = clientOf(account)
        val positions = repository.positions().filter { it.accountId == account.id }

        val totalValue = positions.sumOf { it.currentValue }
        val totalCost = positions.sumOf { it.quantity * it.averageBuyPrice }
        val totalProfit = positions.sumOf { it.profit }

        val table = ReportTable(
            columns = listOf(
                ReportColumn("Тикер"),
                ReportColumn("Инструмент"),
                ReportColumn("Объем", ReportAlign.RIGHT),
                ReportColumn("Средняя цена покупки", ReportAlign.RIGHT),
                ReportColumn("Текущая цена", ReportAlign.RIGHT),
                ReportColumn("Общая стоимость", ReportAlign.RIGHT),
                ReportColumn("Результат", ReportAlign.RIGHT),
            ),
            rows = positions.map { position ->
                listOf(
                    ReportCell.Text(position.ticker),
                    ReportCell.Text(position.instrumentName),
                    ReportCell.Quantity(position.quantity),
                    ReportCell.Money(position.averageBuyPrice),
                    position.latestPrice?.let(ReportCell::Money) ?: ReportCell.Text("—"),
                    ReportCell.Money(position.currentValue),
                    ReportCell.Money(position.profit),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого"),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Money(totalValue),
                ReportCell.Money(totalProfit),
            ),
        )

        return ReportDocument(
            kind = request.kind,
            generatedAt = now(),
            headerFields = clientHeader(client, account),
            sections = listOf(
                ReportSection(
                    title = "Открытые позиции",
                    table = table,
                    note = if (positions.isEmpty()) "На счете нет открытых позиций." else null,
                ),
                ReportSection(
                    title = "Оценка портфеля",
                    fields = listOf(
                        ReportField("Количество позиций", positions.size.toString()),
                        ReportField("Затраты на приобретение", formatMoney(totalCost)),
                        ReportField("Текущая рыночная стоимость", formatMoney(totalValue)),
                        ReportField("Финансовый результат", formatMoney(totalProfit)),
                        ReportField("Доходность", "${formatQuantity(profitability(totalProfit, totalCost))} %"),
                    ),
                ),
            ),
        )
    }

    private fun income(request: ReportRequest): ReportDocument {
        val account = requireAccount(request)
        val client = clientOf(account)
        val from = request.dateFrom?.ifBlank { null }
        val to = request.dateTo?.ifBlank { null }

        val dividends = repository.dividends()
            .filter { it.accountId == account.id && it.paymentDate.inPeriod(from, to) }
        val coupons = repository.coupons()
            .filter { it.accountId == account.id && it.paymentDate.inPeriod(from, to) }

        val rows = buildList {
            dividends.forEach { dividend ->
                add(
                    IncomeRow(
                        date = dividend.paymentDate,
                        kind = "Дивиденды",
                        ticker = dividend.ticker,
                        amount = dividend.amount,
                        tax = dividend.taxAmount,
                        net = dividend.netAmount,
                    ),
                )
            }
            coupons.forEach { coupon ->
                add(
                    IncomeRow(
                        date = coupon.paymentDate,
                        kind = "Купоны",
                        ticker = coupon.ticker,
                        amount = coupon.amount,
                        tax = coupon.taxAmount,
                        net = coupon.netAmount,
                    ),
                )
            }
        }.sortedBy { it.date }

        val table = ReportTable(
            columns = listOf(
                ReportColumn("Дата начисления"),
                ReportColumn("Вид дохода"),
                ReportColumn("Тикер"),
                ReportColumn("Начислено", ReportAlign.RIGHT),
                ReportColumn("Удержан НДФЛ", ReportAlign.RIGHT),
                ReportColumn("К выплате", ReportAlign.RIGHT),
            ),
            rows = rows.map { row ->
                listOf(
                    ReportCell.Text(row.date),
                    ReportCell.Text(row.kind),
                    ReportCell.Text(row.ticker),
                    ReportCell.Money(row.amount),
                    ReportCell.Money(row.tax),
                    ReportCell.Money(row.net),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого"),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Money(rows.sumOf { it.amount }),
                ReportCell.Money(rows.sumOf { it.tax }),
                ReportCell.Money(rows.sumOf { it.net }),
            ),
        )

        return ReportDocument(
            kind = request.kind,
            generatedAt = now(),
            headerFields = clientHeader(client, account) + periodField(request),
            sections = listOf(
                ReportSection(
                    title = "Реестр начислений",
                    table = table,
                    note = if (rows.isEmpty()) "За выбранный период начисления отсутствуют." else null,
                ),
                ReportSection(
                    title = "Сводка по доходам",
                    fields = listOf(
                        ReportField("Начислено дивидендов", formatMoney(dividends.sumOf { it.amount })),
                        ReportField("Начислено купонов", formatMoney(coupons.sumOf { it.amount })),
                        ReportField("Всего начислено", formatMoney(rows.sumOf { it.amount })),
                        ReportField("Удержано НДФЛ", formatMoney(rows.sumOf { it.tax })),
                        ReportField("Итого к выплате", formatMoney(rows.sumOf { it.net })),
                    ),
                ),
            ),
        )
    }

    private fun accountOpening(request: ReportRequest): ReportDocument {
        val account = requireAccount(request)
        val client = clientOf(account)

        return ReportDocument(
            kind = request.kind,
            generatedAt = now(),
            headerFields = listOf(
                ReportField("Инвестор", client.fullName),
                ReportField("Номер счета", account.accountNumber),
            ),
            sections = listOf(
                ReportSection(
                    title = "Реквизиты инвестора",
                    fields = listOf(
                        ReportField("ФИО", client.fullName),
                        ReportField("Дата рождения", client.birthDate),
                        ReportField("Паспорт", client.passportNumber),
                        ReportField("Телефон", client.phone),
                        ReportField("Email", client.email),
                        ReportField("Дата регистрации в системе", client.registrationDate),
                    ),
                ),
                ReportSection(
                    title = "Параметры инвестиционного счета",
                    fields = listOfNotNull(
                        ReportField("Номер счета", account.accountNumber),
                        ReportField("Дата открытия", account.openedAt),
                        ReportField("Валюта счета", account.currency),
                        ReportField("Статус", account.status.label()),
                        account.closedAt?.let { ReportField("Дата закрытия", it) },
                    ),
                ),
                ReportSection(
                    title = "Персональный менеджер",
                    fields = listOf(ReportField("ФИО менеджера", client.managerName)),
                    note = "Уведомление сформировано автоматически и не требует подписи сторон.",
                ),
            ),
        )
    }

    private fun consolidatedAnalytics(request: ReportRequest): ReportDocument {
        val dashboard = repository.dashboard()
        val turnover = repository.monthlyTurnover().sortedBy { it.month }
        val allocation = repository.assetAllocation()
        val allocationTotal = allocation.sumOf { it.currentValue }

        val turnoverTable = ReportTable(
            columns = listOf(
                ReportColumn("Месяц"),
                ReportColumn("Оборот покупок", ReportAlign.RIGHT),
                ReportColumn("Оборот продаж", ReportAlign.RIGHT),
                ReportColumn("Итого", ReportAlign.RIGHT),
            ),
            rows = turnover.map {
                listOf(
                    ReportCell.Text(it.month),
                    ReportCell.Money(it.buy),
                    ReportCell.Money(it.sell),
                    ReportCell.Money(it.buy + it.sell),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого"),
                ReportCell.Money(turnover.sumOf { it.buy }),
                ReportCell.Money(turnover.sumOf { it.sell }),
                ReportCell.Money(turnover.sumOf { it.buy + it.sell }),
            ),
        )

        val allocationTable = ReportTable(
            columns = listOf(
                ReportColumn("Класс активов"),
                ReportColumn("Стоимость", ReportAlign.RIGHT),
                ReportColumn("Доля", ReportAlign.RIGHT),
            ),
            rows = allocation.map {
                listOf(
                    ReportCell.Text(it.typeName),
                    ReportCell.Money(it.currentValue),
                    ReportCell.Percent(share(it.currentValue, allocationTotal)),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого"),
                ReportCell.Money(allocationTotal),
                ReportCell.Percent(if (allocationTotal > 0) 100.0 else 0.0),
            ),
        )

        return ReportDocument(
            kind = request.kind,
            generatedAt = now(),
            headerFields = listOf(ReportField("Получатель", "Руководство компании")),
            sections = listOf(
                ReportSection(
                    title = "Счетчики системы",
                    fields = listOf(
                        ReportField("Активных клиентов", dashboard.clientCount.toString()),
                        ReportField("Открытых счетов", dashboard.accountCount.toString()),
                        ReportField("Активных инструментов", dashboard.instrumentCount.toString()),
                        ReportField("Зарегистрированных сделок", dashboard.tradeCount.toString()),
                        ReportField("Активы под управлением", formatMoney(allocationTotal)),
                    ),
                ),
                ReportSection(
                    title = "Помесячный оборот покупок и продаж",
                    table = turnoverTable,
                    note = if (turnover.isEmpty()) "Сделки в системе отсутствуют." else null,
                ),
                ReportSection(
                    title = "Распределение капитала по классам активов",
                    table = allocationTable,
                    note = if (allocation.isEmpty()) "Открытые позиции отсутствуют." else null,
                ),
            ),
        )
    }

    private fun requireAccount(request: ReportRequest): Account {
        val accountId = requireNotNull(request.accountId) { "Для отчета «${request.kind.title}» выберите счет" }
        return repository.accounts().firstOrNull { it.id == accountId }
            ?: error("Счет не найден")
    }

    private fun clientOf(account: Account): Client =
        repository.clients().firstOrNull { it.id == account.clientId } ?: error("Клиент счета не найден")

    private fun clientHeader(client: Client, account: Account) = listOf(
        ReportField("ФИО клиента", client.fullName),
        ReportField("Номер счета", account.accountNumber),
        ReportField("Валюта счета", account.currency),
        ReportField("Персональный менеджер", client.managerName),
    )

    private fun periodField(request: ReportRequest) = listOf(
        ReportField(
            "Период",
            when {
                request.dateFrom.isNullOrBlank() && request.dateTo.isNullOrBlank() -> "за весь период"
                request.dateFrom.isNullOrBlank() -> "по ${request.dateTo}"
                request.dateTo.isNullOrBlank() -> "с ${request.dateFrom}"
                else -> "с ${request.dateFrom} по ${request.dateTo}"
            },
        ),
    )

    private data class IncomeRow(
        val date: String,
        val kind: String,
        val ticker: String,
        val amount: Double,
        val tax: Double,
        val net: Double,
    )
}

private val timestampFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

private fun now() = LocalDateTime.now().format(timestampFormat)

private fun profitability(profit: Double, cost: Double) = if (cost > 0) profit / cost * 100.0 else 0.0

private fun share(value: Double, total: Double) = if (total > 0) value / total * 100.0 else 0.0

// ISO-8601 dates sort lexicographically, which keeps the period filter free of date parsing.
private fun String.inPeriod(from: String?, to: String?) =
    (from == null || this >= from) && (to == null || this <= to)

private fun TradeType.label() = if (this == TradeType.BUY) "Покупка (BUY)" else "Продажа (SELL)"

private fun AccountStatus.label() = when (this) {
    AccountStatus.OPEN -> "Открыт"
    AccountStatus.CLOSED -> "Закрыт"
    AccountStatus.BLOCKED -> "Заблокирован"
}
