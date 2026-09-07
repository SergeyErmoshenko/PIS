package me.investcompany.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import me.investcompany.domain.*
import me.investcompany.persistence.db.InvestDatabase
import me.investcompany.persistence.db.Instrument as DbInstrument
import me.investcompany.persistence.db.Employee as DbEmployee
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.io.path.createDirectories

class SqlDelightInvestmentRepository private constructor(
    private val driver: JdbcSqliteDriver,
    private val database: InvestDatabase,
) : InvestmentRepository {
    private val queries = database.investmentQueries

    override fun employees() = queries.selectEmployees().executeAsList().map { it.toDomain() }

    override fun clients(query: String) = if (query.isBlank()) {
        queries.selectClients().executeAsList().map { row ->
            Client(row.id, row.manager_id, row.last_name, row.first_name, row.middle_name, row.birth_date,
                row.phone, row.email, row.passport_number, row.registration_date, ClientStatus.valueOf(row.status),
                row.manager_name, row.account_count)
        }
    } else {
        // SQLite's builtin lower() only folds ASCII, so the query is normalized here with
        // Kotlin's Unicode-aware lowercase() before being matched against search_name.
        queries.searchClients(query.trim().lowercase()).executeAsList().map { row ->
            Client(row.id, row.manager_id, row.last_name, row.first_name, row.middle_name, row.birth_date,
                row.phone, row.email, row.passport_number, row.registration_date, ClientStatus.valueOf(row.status),
                row.manager_name, row.account_count)
        }
    }

    override fun accounts() = queries.selectAccounts().executeAsList().map {
        Account(it.id, it.client_id, it.account_number, it.opened_at, it.closed_at,
            AccountStatus.valueOf(it.status), it.currency, it.client_name)
    }

    override fun instrumentTypes() = queries.selectInstrumentTypes().executeAsList().map {
        InstrumentType(it.id, it.name, it.description)
    }

    override fun instruments() = queries.selectInstruments().executeAsList().map {
        Instrument(it.id, it.type_id, it.ticker, it.name, it.issuer, it.currency, it.is_active == 1L,
            it.type_name, it.latest_price, it.latest_price_date)
    }

    override fun trades(filter: TradeFilter) = queries.filterTrades(
        filter.clientId, filter.accountId, filter.instrumentId, filter.type?.name,
        filter.dateFrom, filter.dateTo, filter.amountMin, filter.amountMax,
    ).executeAsList().map {
        Trade(it.id, it.account_id, it.instrument_id, it.employee_id, TradeType.valueOf(it.trade_type),
            it.trade_date, it.quantity, it.unit_price, it.commission, it.comment, it.client_name,
            it.account_number, it.ticker, it.instrument_name, it.employee_name)
    }

    override fun dividends() = queries.selectDividends().executeAsList().map {
        Dividend(it.id, it.account_id, it.instrument_id, it.payment_date, it.amount, it.tax_amount,
            it.client_name, it.account_number, it.ticker)
    }

    override fun positions() = queries.selectPortfolioPositions().executeAsList().map {
        PortfolioPosition(it.account_id, it.account_number, it.client_id, it.client_name,
            it.instrument_id, it.ticker, it.instrument_name, it.quantity ?: 0.0,
            it.average_buy_price ?: 0.0, it.latest_price)
    }

    override fun portfolioSummaries() = queries.portfolioSummaryByClient().executeAsList().map {
        PortfolioSummary(it.client_id, it.client_name, it.instrument_count, it.current_value)
    }

    override fun monthlyTurnover() = queries.monthlyTurnover().executeAsList().map {
        MonthlyTurnover(it.month ?: "", it.buy_turnover ?: 0.0, it.sell_turnover ?: 0.0)
    }

    override fun assetAllocation() = queries.assetAllocation().executeAsList().map {
        AssetAllocation(it.type_name, it.current_value ?: 0.0)
    }

    override fun dashboard(): Dashboard = queries.dashboardCounts().executeAsOne().let {
        Dashboard(it.client_count, it.account_count, it.instrument_count, it.trade_count)
    }

    override fun addEmployee(employee: EmployeeInput) {
        employee.validate()
        queries.insertEmployee(employee.lastName.trim(), employee.firstName.trim(), employee.middleName.normalized(),
            employee.position.trim(), employee.phone.trim(), employee.email.trim(), 1L, LocalDateTime.now().toString())
    }

    override fun updateEmployee(id: Long, employee: EmployeeInput) {
        employee.validate()
        val current = employees().firstOrNull { it.id == id } ?: error("Сотрудник не найден")
        queries.updateEmployee(employee.lastName.trim(), employee.firstName.trim(), employee.middleName.normalized(),
            employee.position.trim(), employee.phone.trim(), employee.email.trim(), if (current.isActive) 1L else 0L, id)
    }

    override fun archiveEmployee(id: Long) {
        val employee = employees().firstOrNull { it.id == id } ?: error("Сотрудник не найден")
        queries.updateEmployee(employee.lastName, employee.firstName, employee.middleName, employee.position,
            employee.phone, employee.email, 0L, id)
    }

    override fun addClient(client: NewClient) {
        client.validate()
        val now = LocalDateTime.now().toString()
        queries.insertClient(client.managerId, client.lastName.trim(), client.firstName.trim(),
            client.middleName?.trim()?.ifBlank { null }, client.birthDate, client.phone.trim(), client.email.trim(),
            client.passportNumber.trim(), LocalDate.now().toString(), ClientStatus.ACTIVE.name,
            searchName(client.lastName, client.firstName, client.middleName), now, now)
    }

    override fun updateClient(id: Long, client: ClientUpdate) {
        NewClient(client.managerId, client.lastName, client.firstName, client.middleName, client.birthDate,
            client.phone, client.email, client.passportNumber).validate()
        queries.updateClient(client.managerId, client.lastName.trim(), client.firstName.trim(), client.middleName.normalized(),
            client.birthDate, client.phone.trim(), client.email.trim(), client.passportNumber.trim(),
            client.status.name, searchName(client.lastName, client.firstName, client.middleName),
            LocalDateTime.now().toString(), id)
    }

    override fun updateClientStatus(id: Long, status: ClientStatus) {
        queries.updateClientStatus(status.name, LocalDateTime.now().toString(), id)
    }

    override fun addAccount(clientId: Long, accountNumber: String) {
        require(accountNumber.isNotBlank()) { "Укажите номер счета" }
        queries.insertAccount(clientId, accountNumber.trim(), LocalDate.now().toString(), "RUB")
    }

    override fun updateAccount(id: Long, clientId: Long, accountNumber: String) {
        require(accountNumber.isNotBlank()) { "Укажите номер счета" }
        queries.updateAccount(clientId, accountNumber.trim(), id)
    }

    override fun updateAccountStatus(id: Long, status: AccountStatus) {
        queries.updateAccountStatus(status.name, if (status == AccountStatus.CLOSED) LocalDate.now().toString() else null, id)
    }

    override fun addInstrument(typeId: Long, ticker: String, name: String, issuer: String, price: Double) {
        require(ticker.isNotBlank() && name.isNotBlank() && issuer.isNotBlank()) { "Заполните данные инструмента" }
        require(price > 0) { "Цена должна быть больше нуля" }
        queries.transaction {
            queries.insertInstrument(typeId, ticker.uppercase(), name.trim(), issuer.trim(), "RUB", 1L, LocalDateTime.now().toString())
            val instrument = queries.selectInstruments().executeAsList().first { it.ticker == ticker.uppercase() }
            queries.insertInstrumentPrice(instrument.id, LocalDate.now().toString(), price)
        }
    }

    override fun updateInstrument(id: Long, instrument: InstrumentInput) {
        require(instrument.ticker.isNotBlank() && instrument.name.isNotBlank() && instrument.issuer.isNotBlank()) { "Заполните данные инструмента" }
        val current = instruments().firstOrNull { it.id == id } ?: error("Инструмент не найден")
        queries.updateInstrument(instrument.typeId, instrument.ticker.uppercase(), instrument.name.trim(),
            instrument.issuer.trim(), instrument.currency, if (current.isActive) 1L else 0L, id)
    }

    override fun archiveInstrument(id: Long) {
        val instrument = instruments().firstOrNull { it.id == id } ?: error("Инструмент не найден")
        queries.updateInstrument(instrument.typeId, instrument.ticker, instrument.name, instrument.issuer,
            instrument.currency, 0L, id)
    }

    override fun addPrice(instrumentId: Long, price: Double) {
        require(price > 0) { "Цена должна быть больше нуля" }
        queries.insertInstrumentPrice(instrumentId, LocalDate.now().toString(), price)
    }

    override fun deleteLatestPrice(instrumentId: Long) {
        queries.deleteLatestPrice(instrumentId)
    }

    override fun addTrade(trade: NewTrade) {
        val account = accounts().firstOrNull { it.id == trade.accountId } ?: error("Счет не найден")
        val available = queries.positionQuantity(trade.accountId, trade.instrumentId).executeAsOne()
        trade.validate(account, available)
        queries.insertTrade(trade.accountId, trade.instrumentId, trade.employeeId, trade.type.name,
            trade.date, trade.quantity, trade.unitPrice, trade.commission,
            trade.comment.normalized(), LocalDateTime.now().toString())
    }

    override fun updateTrade(id: Long, trade: NewTrade) {
        val current = trades().firstOrNull { it.id == id } ?: error("Сделка не найдена")
        val account = accounts().firstOrNull { it.id == trade.accountId } ?: error("Счет не найден")
        // Exclude the trade being edited from its own position before validating the new values,
        // otherwise a BUY being turned into a SELL would still count its own old quantity as available.
        val currentEffect = if (current.accountId == trade.accountId && current.instrumentId == trade.instrumentId) {
            if (current.type == TradeType.BUY) current.quantity else -current.quantity
        } else {
            0.0
        }
        val available = queries.positionQuantity(trade.accountId, trade.instrumentId).executeAsOne() - currentEffect
        trade.validate(account, available)
        queries.updateTrade(trade.accountId, trade.instrumentId, trade.employeeId, trade.type.name, trade.date,
            trade.quantity, trade.unitPrice, trade.commission, trade.comment.normalized(), id)
    }

    override fun deleteTrade(id: Long) {
        queries.deleteTrade(id)
    }

    override fun addDividend(accountId: Long, instrumentId: Long, amount: Double, taxAmount: Double) {
        require(amount > 0) { "Сумма должна быть больше нуля" }
        require(taxAmount in 0.0..amount) { "Некорректная сумма налога" }
        queries.insertDividend(accountId, instrumentId, LocalDate.now().toString(), amount, taxAmount)
    }

    override fun updateDividend(id: Long, accountId: Long, instrumentId: Long, amount: Double, taxAmount: Double) {
        require(amount > 0) { "Сумма должна быть больше нуля" }
        require(taxAmount in 0.0..amount) { "Некорректная сумма налога" }
        val current = dividends().firstOrNull { it.id == id } ?: error("Начисление не найдено")
        queries.updateDividend(accountId, instrumentId, current.paymentDate, amount, taxAmount, id)
    }

    override fun deleteDividend(id: Long) {
        queries.deleteDividend(id)
    }

    override fun close() = driver.close()

    companion object {
        fun open(file: File): SqlDelightInvestmentRepository {
            file.parentFile?.toPath()?.createDirectories()
            val isNew = !file.exists()
            val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
            if (isNew) InvestDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            val repository = SqlDelightInvestmentRepository(driver, InvestDatabase(driver))
            repository.seedIfEmpty()
            return repository
        }

        fun inMemory(seed: Boolean = false): SqlDelightInvestmentRepository {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            InvestDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            val repository = SqlDelightInvestmentRepository(driver, InvestDatabase(driver))
            if (seed) repository.seedIfEmpty()
            return repository
        }
    }

    private fun seedIfEmpty() {
        if (employees().isNotEmpty()) return
        val now = LocalDateTime.now().toString()
        queries.transaction {
            queries.insertEmployee("Иванов", "Петр", "Сергеевич", "Инвестиционный менеджер", "+7 900 100-10-10", "ivanov@invest.local", 1L, now)
            queries.insertEmployee("Соколова", "Анна", "Игоревна", "Старший менеджер", "+7 900 200-20-20", "sokolova@invest.local", 1L, now)
            queries.insertEmployee("Орлов", "Дмитрий", null, "Аналитик", "+7 900 300-30-30", "orlov@invest.local", 1L, now)
            queries.insertInstrumentType("Акция", "Долевая ценная бумага")
            queries.insertInstrumentType("Облигация", "Долговая ценная бумага")
            queries.insertInstrumentType("Фонд", "Биржевой инвестиционный фонд")
        }
        val managers = employees()
        repeat(15) { index ->
            val n = index + 1
            queries.insertClient(managers[index % 2].id, "Клиент$n", "Имя$n", null, "198${index % 10}-01-15",
                "+7 901 000-${n.toString().padStart(2, '0')}-00", "client$n@example.ru", "45${n.toString().padStart(8, '0')}",
                LocalDate.now().minusDays(index.toLong()).toString(), ClientStatus.ACTIVE.name,
                searchName("Клиент$n", "Имя$n", null), now, now)
        }
        val seededClients = clients()
        seededClients.forEachIndexed { index, client -> queries.insertAccount(client.id, "INV-${(index + 1).toString().padStart(6, '0')}", LocalDate.now().minusDays(index.toLong()).toString(), "RUB") }
        seededClients.take(5).forEachIndexed { index, client -> queries.insertAccount(client.id, "INV-${(16 + index).toString().padStart(6, '0')}", LocalDate.now().minusDays(index.toLong()).toString(), "RUB") }
        val typeIds = queries.selectInstrumentTypes().executeAsList().map { it.id }
        val names = listOf("Сбербанк", "Газпром", "Лукойл", "Яндекс", "Роснефть", "Мосбиржа", "Аэрофлот", "МТС",
            "Совкомфлот", "Полюс", "ВТБ", "Новатэк", "Северсталь", "Норникель", "Магнит")
        names.forEachIndexed { index, name ->
            queries.insertInstrument(typeIds[index % typeIds.size], "T${index + 1}", name, name, "RUB", 1L, now)
        }
        instruments().forEachIndexed { index, instrument ->
            queries.insertInstrumentPrice(instrument.id, LocalDate.now().minusDays(30).toString(), 100.0 + index * 25)
            queries.insertInstrumentPrice(instrument.id, LocalDate.now().toString(), 110.0 + index * 27)
        }
        val accounts = accounts()
        val instruments = instruments()
        repeat(45) { index ->
            val account = accounts[index % accounts.size]
            val instrument = instruments[index % instruments.size]
            queries.insertTrade(account.id, instrument.id, managers[index % managers.size].id, "BUY",
                LocalDate.now().minusDays((index % 20 + 5).toLong()).toString(), (index % 5 + 6).toDouble(),
                instrument.latestPrice ?: 100.0, 10.0, "Демонстрационная покупка", now)
        }
        repeat(10) { index ->
            val account = accounts[index % accounts.size]
            val instrument = instruments[index % instruments.size]
            queries.insertTrade(account.id, instrument.id, managers[index % managers.size].id, "SELL",
                LocalDate.now().minusDays(index.toLong()).toString(), 2.0,
                instrument.latestPrice ?: 100.0, 5.0, "Демонстрационная продажа", now)
        }
        repeat(5) { index ->
            val account = accounts[index % accounts.size]
            val instrument = instruments[index % instruments.size]
            queries.insertDividend(account.id, instrument.id, LocalDate.now().minusDays((index * 7).toLong()).toString(),
                500.0 + index * 50, 65.0 + index * 6.5)
        }
    }
}

private fun EmployeeInput.validate() {
    require(lastName.isNotBlank()) { "Укажите фамилию" }
    require(firstName.isNotBlank()) { "Укажите имя" }
    require(position.isNotBlank()) { "Укажите должность" }
    require(phone.isNotBlank()) { "Укажите телефон" }
    require(email.contains('@')) { "Некорректный email" }
}

private fun String?.normalized() = this?.trim()?.ifBlank { null }
private fun DbEmployee.toDomain() = Employee(id, last_name, first_name, middle_name, position, phone, email, is_active == 1L)
private fun searchName(lastName: String, firstName: String, middleName: String?) =
    listOfNotNull(lastName, firstName, middleName).joinToString(" ").trim().lowercase()
