package me.autoservice.report

import me.autoservice.domain.AutoServiceRepository
import me.autoservice.domain.Client
import me.autoservice.domain.ClientStatus
import me.autoservice.domain.VehicleStatus
import me.autoservice.domain.WorkOrder
import me.autoservice.domain.WorkOrderFilter
import me.autoservice.domain.WorkOrderStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Collects domain data for a [ReportRequest] and shapes it into a renderer-agnostic [ReportDocument],
 * so that the Word and Excel writers stay free of any business logic.
 */
class ReportBuilder(private val repository: AutoServiceRepository) {

    fun build(request: ReportRequest): ReportDocument = when (request.kind) {
        ReportKind.CLIENT_WORK_ORDERS -> clientWorkOrders(request)
        ReportKind.WORK_ORDER_ACT -> workOrderAct(request)
        ReportKind.REVENUE_STATEMENT -> revenueStatement(request)
        ReportKind.CLIENT_PROFILE -> clientProfile(request)
        ReportKind.WORKSHOP_ANALYTICS -> workshopAnalytics(request)
    }

    private fun clientWorkOrders(request: ReportRequest): ReportDocument {
        val client = requireClient(request)
        val orders = workOrdersOf(client.id, request).sortedBy { it.openDate }

        val table = ReportTable(
            columns = listOf(
                ReportColumn("Дата"),
                ReportColumn("Статус"),
                ReportColumn("Автомобиль"),
                ReportColumn("Услуга"),
                ReportColumn("Механик"),
                ReportColumn("Кол-во", ReportAlign.RIGHT),
                ReportColumn("Цена", ReportAlign.RIGHT),
                ReportColumn("Скидка", ReportAlign.RIGHT),
                ReportColumn("Сумма", ReportAlign.RIGHT),
            ),
            rows = orders.map { order ->
                listOf(
                    ReportCell.Text(order.openDate),
                    ReportCell.Text(order.status.label()),
                    ReportCell.Text(order.licensePlate),
                    ReportCell.Text(order.serviceName),
                    ReportCell.Text(order.mechanicName),
                    ReportCell.Quantity(order.quantity),
                    ReportCell.Money(order.unitPrice),
                    ReportCell.Money(order.discount),
                    ReportCell.Money(order.amount),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого"),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Money(orders.sumOf { it.discount }),
                ReportCell.Money(orders.sumOf { it.amount }),
            ),
        )

        val completed = orders.filter { it.status == WorkOrderStatus.COMPLETED }
        return ReportDocument(
            kind = request.kind,
            generatedAt = now(),
            headerFields = clientHeader(client) + periodField(request),
            sections = listOf(
                ReportSection(
                    title = "История заказ-нарядов",
                    table = table,
                    note = if (orders.isEmpty()) "За выбранный период заказ-наряды отсутствуют." else null,
                ),
                ReportSection(
                    title = "Сводка по клиенту",
                    fields = listOf(
                        ReportField("Всего заказ-нарядов", orders.size.toString()),
                        ReportField("Завершено", completed.size.toString()),
                        ReportField("В работе", orders.count { it.status == WorkOrderStatus.IN_PROGRESS }.toString()),
                        ReportField("Сумма скидок", formatMoney(orders.sumOf { it.discount })),
                        ReportField("Общая сумма работ", formatMoney(orders.sumOf { it.amount })),
                    ),
                ),
            ),
        )
    }

    private fun workOrderAct(request: ReportRequest): ReportDocument {
        val client = requireClient(request)
        val orders = workOrdersOf(client.id, request).sortedBy { it.openDate }
        val orderIds = orders.map { it.id }.toSet()
        val usages = repository.partUsages().filter { it.workOrderId in orderIds }
        val payments = repository.payments().filter { it.workOrderId in orderIds }

        val servicesTable = ReportTable(
            columns = listOf(
                ReportColumn("Дата"),
                ReportColumn("Код"),
                ReportColumn("Услуга"),
                ReportColumn("Механик"),
                ReportColumn("Кол-во", ReportAlign.RIGHT),
                ReportColumn("Цена", ReportAlign.RIGHT),
                ReportColumn("Сумма", ReportAlign.RIGHT),
            ),
            rows = orders.map { order ->
                listOf(
                    ReportCell.Text(order.openDate),
                    ReportCell.Text(order.serviceCode),
                    ReportCell.Text(order.serviceName),
                    ReportCell.Text(order.mechanicName),
                    ReportCell.Quantity(order.quantity),
                    ReportCell.Money(order.unitPrice),
                    ReportCell.Money(order.amount),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого по работам"),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Money(orders.sumOf { it.amount }),
            ),
        )

        val partsTable = ReportTable(
            columns = listOf(
                ReportColumn("Дата"),
                ReportColumn("Автомобиль"),
                ReportColumn("Запчасть"),
                ReportColumn("Сумма", ReportAlign.RIGHT),
                ReportColumn("Скидка", ReportAlign.RIGHT),
                ReportColumn("К оплате", ReportAlign.RIGHT),
            ),
            rows = usages.map { usage ->
                listOf(
                    ReportCell.Text(usage.usageDate),
                    ReportCell.Text(usage.licensePlate),
                    ReportCell.Text(usage.partName),
                    ReportCell.Money(usage.amount),
                    ReportCell.Money(usage.discountAmount),
                    ReportCell.Money(usage.netAmount),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого по запчастям"),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Money(usages.sumOf { it.amount }),
                ReportCell.Money(usages.sumOf { it.discountAmount }),
                ReportCell.Money(usages.sumOf { it.netAmount }),
            ),
        )

        val worksTotal = orders.sumOf { it.amount }
        val partsTotal = usages.sumOf { it.netAmount }
        val paidTotal = payments.sumOf { it.netAmount }
        val grandTotal = worksTotal + partsTotal

        return ReportDocument(
            kind = request.kind,
            generatedAt = now(),
            headerFields = clientHeader(client) + periodField(request),
            sections = listOf(
                ReportSection(
                    title = "Выполненные работы",
                    table = servicesTable,
                    note = if (orders.isEmpty()) "За выбранный период работы не выполнялись." else null,
                ),
                ReportSection(
                    title = "Израсходованные запчасти",
                    table = partsTable,
                    note = if (usages.isEmpty()) "Расход запчастей по указанным заказ-нарядам не зарегистрирован." else null,
                ),
                ReportSection(
                    title = "Расчет стоимости",
                    fields = listOf(
                        ReportField("Стоимость работ", formatMoney(worksTotal)),
                        ReportField("Стоимость запчастей", formatMoney(partsTotal)),
                        ReportField("Итого к оплате", formatMoney(grandTotal)),
                        ReportField("Оплачено", formatMoney(paidTotal)),
                        ReportField("Задолженность", formatMoney(grandTotal - paidTotal)),
                    ),
                ),
            ),
        )
    }

    private fun revenueStatement(request: ReportRequest): ReportDocument {
        val from = request.dateFrom?.ifBlank { null }
        val to = request.dateTo?.ifBlank { null }
        val payments = repository.payments().filter { it.paymentDate.inPeriod(from, to) }.sortedBy { it.paymentDate }
        val usages = repository.partUsages().filter { it.usageDate.inPeriod(from, to) }
        val orders = repository.workOrders().filter { it.openDate.inPeriod(from, to) }

        val table = ReportTable(
            columns = listOf(
                ReportColumn("Дата"),
                ReportColumn("Клиент"),
                ReportColumn("Автомобиль"),
                ReportColumn("Сумма", ReportAlign.RIGHT),
                ReportColumn("Скидка", ReportAlign.RIGHT),
                ReportColumn("Зачислено", ReportAlign.RIGHT),
            ),
            rows = payments.map { payment ->
                listOf(
                    ReportCell.Text(payment.paymentDate),
                    ReportCell.Text(payment.clientName),
                    ReportCell.Text(payment.licensePlate),
                    ReportCell.Money(payment.amount),
                    ReportCell.Money(payment.discountAmount),
                    ReportCell.Money(payment.netAmount),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого"),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Money(payments.sumOf { it.amount }),
                ReportCell.Money(payments.sumOf { it.discountAmount }),
                ReportCell.Money(payments.sumOf { it.netAmount }),
            ),
        )

        val monthly = repository.monthlyRevenue()
            .filter { it.month.inPeriod(from?.take(7), to?.take(7)) }
            .sortedBy { it.month }
            .map { Monthly(it.month, it.services, it.parts) }

        val monthlyTable = ReportTable(
            columns = listOf(
                ReportColumn("Месяц"),
                ReportColumn("Услуги", ReportAlign.RIGHT),
                ReportColumn("Запчасти", ReportAlign.RIGHT),
                ReportColumn("Итого", ReportAlign.RIGHT),
            ),
            rows = monthly.map {
                listOf(
                    ReportCell.Text(it.month),
                    ReportCell.Money(it.services),
                    ReportCell.Money(it.parts),
                    ReportCell.Money(it.total),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого"),
                ReportCell.Money(monthly.sumOf { it.services }),
                ReportCell.Money(monthly.sumOf { it.parts }),
                ReportCell.Money(monthly.sumOf { it.total }),
            ),
        )

        return ReportDocument(
            kind = request.kind,
            generatedAt = now(),
            headerFields = listOf(ReportField("Подразделение", "Сервисный центр")) + periodField(request),
            sections = listOf(
                ReportSection(
                    title = "Поступления по платежам",
                    table = table,
                    note = if (payments.isEmpty()) "За выбранный период платежи не зарегистрированы." else null,
                ),
                ReportSection(
                    title = "Помесячная разбивка выручки",
                    table = monthlyTable,
                    note = if (monthly.isEmpty()) "Данные за выбранный период отсутствуют." else null,
                ),
                ReportSection(
                    title = "Итоговые показатели",
                    fields = listOf(
                        ReportField("Заказ-нарядов оформлено", orders.size.toString()),
                        ReportField("Платежей поступило", payments.size.toString()),
                        ReportField("Выручка по платежам", formatMoney(payments.sumOf { it.netAmount })),
                        ReportField("Стоимость запчастей", formatMoney(usages.sumOf { it.netAmount })),
                        ReportField("Средний чек", formatMoney(payments.takeIf { it.isNotEmpty() }?.let { payments.sumOf { p -> p.netAmount } / it.size } ?: 0.0)),
                    ),
                ),
            ),
        )
    }

    private fun clientProfile(request: ReportRequest): ReportDocument {
        val client = requireClient(request)
        val vehicles = repository.vehicles().filter { it.clientId == client.id }
        val orders = repository.workOrders(WorkOrderFilter(clientId = client.id)).sortedBy { it.openDate }
        val spending = repository.clientSpendingSummaries().firstOrNull { it.clientId == client.id }

        val vehiclesTable = ReportTable(
            columns = listOf(
                ReportColumn("Госномер"),
                ReportColumn("Марка"),
                ReportColumn("Модель"),
                ReportColumn("Год", ReportAlign.RIGHT),
                ReportColumn("VIN"),
                ReportColumn("Статус"),
                ReportColumn("На обслуживании с"),
            ),
            rows = vehicles.map { vehicle ->
                listOf(
                    ReportCell.Text(vehicle.licensePlate),
                    ReportCell.Text(vehicle.make),
                    ReportCell.Text(vehicle.model),
                    ReportCell.Quantity(vehicle.year.toDouble()),
                    ReportCell.Text(vehicle.vin),
                    ReportCell.Text(vehicle.status.label()),
                    ReportCell.Text(vehicle.registeredAt),
                )
            },
        )

        val recentOrders = orders.takeLast(RECENT_ORDERS).reversed()
        val ordersTable = ReportTable(
            columns = listOf(
                ReportColumn("Дата"),
                ReportColumn("Услуга"),
                ReportColumn("Автомобиль"),
                ReportColumn("Механик"),
                ReportColumn("Сумма", ReportAlign.RIGHT),
            ),
            rows = recentOrders.map { order ->
                listOf(
                    ReportCell.Text(order.openDate),
                    ReportCell.Text(order.serviceName),
                    ReportCell.Text(order.licensePlate),
                    ReportCell.Text(order.mechanicName),
                    ReportCell.Money(order.amount),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого"),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Money(recentOrders.sumOf { it.amount }),
            ),
        )

        return ReportDocument(
            kind = request.kind,
            generatedAt = now(),
            headerFields = clientHeader(client),
            sections = listOf(
                ReportSection(
                    title = "Реквизиты клиента",
                    fields = listOf(
                        ReportField("ФИО", client.fullName),
                        ReportField("Телефон", client.phone),
                        ReportField("Email", client.email),
                        ReportField("Мастер-приемщик", client.advisorName),
                        ReportField("Дата регистрации", client.registrationDate),
                        ReportField("Статус", client.status.label()),
                    ),
                ),
                ReportSection(
                    title = "Автомобили в обслуживании",
                    table = vehiclesTable,
                    note = if (vehicles.isEmpty()) "Автомобили не зарегистрированы." else null,
                ),
                ReportSection(
                    title = "Последние обращения",
                    table = ordersTable,
                    note = if (recentOrders.isEmpty()) "Заказ-наряды отсутствуют." else null,
                ),
                ReportSection(
                    title = "Итоги обслуживания",
                    fields = listOf(
                        ReportField("Всего заказ-нарядов", orders.size.toString()),
                        ReportField("Завершено", orders.count { it.status == WorkOrderStatus.COMPLETED }.toString()),
                        ReportField("Автомобилей на обслуживании", vehicles.count { it.status == VehicleStatus.ACTIVE }.toString()),
                        ReportField("Всего оплачено", formatMoney(spending?.totalPaid ?: 0.0)),
                    ),
                ),
            ),
        )
    }

    private fun workshopAnalytics(request: ReportRequest): ReportDocument {
        val dashboard = repository.dashboard()
        val orders = repository.workOrders()
        val payments = repository.payments()
        val categoryRevenue = repository.categoryRevenue().sortedByDescending { it.amount }

        val mechanics = orders
            .filter { it.status == WorkOrderStatus.COMPLETED }
            .groupBy { it.mechanicName }
            .map { (name, list) -> MechanicLoad(name, list.size, list.sumOf { it.amount }) }
            .sortedByDescending { it.revenue }

        val mechanicsTable = ReportTable(
            columns = listOf(
                ReportColumn("Механик"),
                ReportColumn("Заказ-нарядов", ReportAlign.RIGHT),
                ReportColumn("Выручка", ReportAlign.RIGHT),
                ReportColumn("Доля", ReportAlign.RIGHT),
            ),
            rows = mechanics.map {
                listOf(
                    ReportCell.Text(it.name),
                    ReportCell.Quantity(it.orders.toDouble()),
                    ReportCell.Money(it.revenue),
                    ReportCell.Percent(share(it.revenue, mechanics.sumOf { mechanic -> mechanic.revenue })),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого"),
                ReportCell.Quantity(mechanics.sumOf { it.orders }.toDouble()),
                ReportCell.Money(mechanics.sumOf { it.revenue }),
                ReportCell.Percent(if (mechanics.isEmpty()) 0.0 else 100.0),
            ),
        )

        val categoryTotal = categoryRevenue.sumOf { it.amount }
        val categoriesTable = ReportTable(
            columns = listOf(
                ReportColumn("Категория услуг"),
                ReportColumn("Выручка", ReportAlign.RIGHT),
                ReportColumn("Доля", ReportAlign.RIGHT),
            ),
            rows = categoryRevenue.map {
                listOf(
                    ReportCell.Text(it.categoryName),
                    ReportCell.Money(it.amount),
                    ReportCell.Percent(share(it.amount, categoryTotal)),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого"),
                ReportCell.Money(categoryTotal),
                ReportCell.Percent(if (categoryRevenue.isEmpty()) 0.0 else 100.0),
            ),
        )

        val monthly = repository.monthlyRevenue().sortedBy { it.month }
        val monthlyTable = ReportTable(
            columns = listOf(
                ReportColumn("Месяц"),
                ReportColumn("Услуги", ReportAlign.RIGHT),
                ReportColumn("Запчасти", ReportAlign.RIGHT),
                ReportColumn("Итого", ReportAlign.RIGHT),
            ),
            rows = monthly.map {
                listOf(
                    ReportCell.Text(it.month),
                    ReportCell.Money(it.services),
                    ReportCell.Money(it.parts),
                    ReportCell.Money(it.services + it.parts),
                )
            },
            totals = listOf(
                ReportCell.Text("Итого"),
                ReportCell.Money(monthly.sumOf { it.services }),
                ReportCell.Money(monthly.sumOf { it.parts }),
                ReportCell.Money(monthly.sumOf { it.services + it.parts }),
            ),
        )

        return ReportDocument(
            kind = request.kind,
            generatedAt = now(),
            headerFields = listOf(ReportField("Получатель", "Руководство сервисного центра")),
            sections = listOf(
                ReportSection(
                    title = "Счетчики системы",
                    fields = listOf(
                        ReportField("Клиентов", dashboard.clientCount.toString()),
                        ReportField("Автомобилей", dashboard.vehicleCount.toString()),
                        ReportField("Услуг в справочнике", dashboard.serviceCount.toString()),
                        ReportField("Заказ-нарядов", dashboard.workOrderCount.toString()),
                        ReportField("Выручка по платежам", formatMoney(payments.sumOf { it.netAmount })),
                    ),
                ),
                ReportSection(
                    title = "Загрузка механиков",
                    table = mechanicsTable,
                    note = if (mechanics.isEmpty()) "Завершенные заказ-наряды отсутствуют." else null,
                ),
                ReportSection(
                    title = "Выручка по категориям услуг",
                    table = categoriesTable,
                    note = if (categoryRevenue.isEmpty()) "Данные по категориям отсутствуют." else null,
                ),
                ReportSection(
                    title = "Динамика выручки по месяцам",
                    table = monthlyTable,
                    note = if (monthly.isEmpty()) "Данные по месяцам отсутствуют." else null,
                ),
            ),
        )
    }

    private fun requireClient(request: ReportRequest): Client {
        val clientId = requireNotNull(request.clientId) { "Для отчета «${request.kind.title}» выберите клиента" }
        return repository.clients().firstOrNull { it.id == clientId } ?: error("Клиент не найден")
    }

    private fun workOrdersOf(clientId: Long, request: ReportRequest): List<WorkOrder> =
        repository.workOrders(
            WorkOrderFilter(
                clientId = clientId,
                dateFrom = request.dateFrom?.ifBlank { null },
                dateTo = request.dateTo?.ifBlank { null },
            ),
        )

    private fun clientHeader(client: Client) = listOf(
        ReportField("Клиент", client.fullName),
        ReportField("Телефон", client.phone),
        ReportField("Email", client.email),
        ReportField("Мастер-приемщик", client.advisorName),
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

    private data class Monthly(val month: String, val services: Double, val parts: Double) {
        val total: Double get() = services + parts
    }

    private data class MechanicLoad(val name: String, val orders: Int, val revenue: Double)
}

private val timestampFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

private fun now() = LocalDateTime.now().format(timestampFormat)

private fun share(value: Double, total: Double) = if (total > 0) value / total * 100.0 else 0.0

// ISO-8601 dates sort lexicographically, which keeps the period filter free of date parsing.
private fun String.inPeriod(from: String?, to: String?) =
    (from == null || this >= from) && (to == null || this <= to)

private fun WorkOrderStatus.label() = when (this) {
    WorkOrderStatus.NEW -> "Новый"
    WorkOrderStatus.IN_PROGRESS -> "В работе"
    WorkOrderStatus.COMPLETED -> "Завершен"
    WorkOrderStatus.CANCELLED -> "Отменен"
}

private fun ClientStatus.label() = when (this) {
    ClientStatus.ACTIVE -> "Активен"
    ClientStatus.BLOCKED -> "Заблокирован"
    ClientStatus.ARCHIVED -> "Архив"
}

private fun VehicleStatus.label() = when (this) {
    VehicleStatus.ACTIVE -> "На обслуживании"
    VehicleStatus.SOLD -> "Продан"
    VehicleStatus.BLOCKED -> "Заблокирован"
}

private const val RECENT_ORDERS = 10
