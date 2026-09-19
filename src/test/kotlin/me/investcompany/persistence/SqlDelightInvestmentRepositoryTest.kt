package me.investcompany.persistence

import me.investcompany.domain.*
import kotlin.test.*
import java.sql.SQLException

class SqlDelightInvestmentRepositoryTest {
    private lateinit var repository: SqlDelightInvestmentRepository

    @BeforeTest fun setUp() { repository = SqlDelightInvestmentRepository.inMemory(seed = true) }
    @AfterTest fun tearDown() { repository.close() }

    @Test
    fun `seed creates complete demonstration data`() {
        assertEquals(3, repository.employees().size)
        assertEquals(15, repository.clients().size)
        assertEquals(20, repository.accounts().size)
        assertEquals(15, repository.instruments().size)
        assertEquals(55, repository.trades().size)
        assertEquals(3, repository.instrumentTypes().size)
        assertTrue(repository.dividends().isNotEmpty())
        assertTrue(repository.trades().any { it.type == TradeType.SELL })
    }

    @Test
    fun `client can be inserted and searched by email`() {
        repository.addClient(NewClient(1, "Тестов", "Тест", null, "1995-05-10", "+79990000000", "unique@example.ru", "999999"))
        val found = repository.clients("unique@example.ru")
        assertEquals(1, found.size)
        assertEquals("Тестов Тест", found.single().fullName)
    }

    @Test
    fun `client search is case-insensitive for cyrillic names`() {
        repository.addClient(NewClient(1, "Смирнов", "Алексей", null, "1992-03-14", "[Phone4]", "smirnov@example.ru", "778899"))
        assertTrue(repository.clients("смирнов").any { it.lastName == "Смирнов" })
        assertTrue(repository.clients("АЛЕКСЕЙ").any { it.firstName == "Алексей" })
    }

    @Test
    fun `dynamic trade filter combines type date and amount`() {
        val all = repository.trades()
        val sample = all.first { it.type == TradeType.BUY }
        val filtered = repository.trades(TradeFilter(type = TradeType.BUY, dateFrom = sample.date, dateTo = sample.date, amountMin = sample.amount, amountMax = sample.amount))
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.type == TradeType.BUY && it.date == sample.date && it.amount == sample.amount })
    }

    @Test
    fun `portfolio is aggregated from trades and latest prices`() {
        val positions = repository.positions()
        assertTrue(positions.isNotEmpty())
        assertTrue(positions.all { it.quantity > 0 && it.currentValue > 0 })
        assertEquals(repository.portfolioSummaries().sumOf { it.currentValue }, positions.sumOf { it.currentValue }, 0.001)
    }

    @Test
    fun `sell greater than position is rejected`() {
        val account = repository.accounts().first()
        val instrument = repository.instruments().first()
        val employee = repository.employees().first()
        val trade = NewTrade(account.id, instrument.id, employee.id, TradeType.SELL, "2026-09-06", 1_000_000.0, 100.0, 0.0, null)
        assertFailsWith<IllegalArgumentException> { repository.addTrade(trade) }
    }

    @Test
    fun `deleting trade recalculates portfolio`() {
        val trade = repository.trades().first { it.type == TradeType.BUY }
        val before = repository.positions().sumOf { it.currentValue }
        repository.deleteTrade(trade.id)
        val after = repository.positions().sumOf { it.currentValue }
        assertTrue(after < before)
    }

    @Test
    fun `dashboard and reports return aggregate data`() {
        val dashboard = repository.dashboard()
        assertEquals(15, dashboard.clientCount)
        assertTrue(repository.monthlyTurnover().isNotEmpty())
        assertTrue(repository.assetAllocation().isNotEmpty())
    }

    @Test
    fun `employee can be created edited and archived`() {
        repository.addEmployee(EmployeeInput("Новый", "Сотрудник", null, "Администратор", "+7001", "admin@example.ru"))
        val employee = repository.employees().first { it.email == "admin@example.ru" }
        repository.updateEmployee(employee.id, EmployeeInput("Новый", "Администратор", null, "Администратор", "+7002", "admin@example.ru"))
        assertEquals("Администратор", repository.employees().first { it.id == employee.id }.firstName)
        repository.archiveEmployee(employee.id)
        assertFalse(repository.employees().first { it.id == employee.id }.isActive)
    }

    @Test
    fun `client account and instrument support edit and archive lifecycle`() {
        val client = repository.clients().first()
        val parts = client.fullName.split(" ")
        repository.updateClient(client.id, ClientUpdate(client.managerId, parts[0], "Изменен", null, client.birthDate, client.phone, client.email, client.passportNumber, client.status))
        assertTrue(repository.clients().first { it.id == client.id }.fullName.contains("Изменен"))
        repository.updateClientStatus(client.id, ClientStatus.ARCHIVED)
        assertEquals(ClientStatus.ARCHIVED, repository.clients().first { it.id == client.id }.status)

        val account = repository.accounts().first()
        repository.updateAccount(account.id, account.clientId, "UPDATED-${account.id}")
        assertEquals("UPDATED-${account.id}", repository.accounts().first { it.id == account.id }.accountNumber)
        repository.updateAccountStatus(account.id, AccountStatus.CLOSED)
        assertEquals(AccountStatus.CLOSED, repository.accounts().first { it.id == account.id }.status)

        val instrument = repository.instruments().first()
        repository.updateInstrument(instrument.id, InstrumentInput(instrument.typeId, instrument.ticker, "Измененный инструмент", instrument.issuer))
        assertEquals("Измененный инструмент", repository.instruments().first { it.id == instrument.id }.name)
        repository.archiveInstrument(instrument.id)
        assertFalse(repository.instruments().first { it.id == instrument.id }.isActive)
    }

    @Test
    fun `editing a buy trade into an oversized sell is rejected`() {
        val client = repository.clients().first()
        repository.addAccount(client.id, "TEST-ACC-1")
        val account = repository.accounts().first { it.accountNumber == "TEST-ACC-1" }
        repository.addInstrument(repository.instrumentTypes().first().id, "TESTX", "Test Instrument", "Test Issuer", 100.0)
        val instrument = repository.instruments().first { it.ticker == "TESTX" }
        val employee = repository.employees().first()
        repository.addTrade(NewTrade(account.id, instrument.id, employee.id, TradeType.BUY, "2026-01-01", 10.0, 100.0, 0.0, null))
        val buy = repository.trades().first { it.accountId == account.id && it.instrumentId == instrument.id }
        val oversizedSell = NewTrade(account.id, instrument.id, employee.id, TradeType.SELL, "2026-01-02", 10.0, 100.0, 0.0, null)
        assertFailsWith<IllegalArgumentException> { repository.updateTrade(buy.id, oversizedSell) }
    }

    @Test
    fun `trade and dividend can be updated and deleted`() {
        val trade = repository.trades().first()
        val update = NewTrade(trade.accountId, trade.instrumentId, trade.employeeId, trade.type, trade.date, trade.quantity, trade.unitPrice + 1, trade.commission, "Изменено")
        repository.updateTrade(trade.id, update)
        assertEquals("Изменено", repository.trades().first { it.id == trade.id }.comment)

        val account = repository.accounts().first()
        val instrument = repository.instruments().first()
        repository.addDividend(account.id, instrument.id, 100.0, 13.0)
        val dividend = repository.dividends().first()
        repository.updateDividend(dividend.id, dividend.accountId, dividend.instrumentId, 200.0, 26.0)
        assertEquals(174.0, repository.dividends().first { it.id == dividend.id }.netAmount)
        repository.deleteDividend(dividend.id)
        assertTrue(repository.dividends().none { it.id == dividend.id })
    }

    @Test
    fun `latest instrument price can be removed`() {
        val instrument = repository.instruments().first()
        val current = instrument.latestPrice
        repository.deleteLatestPrice(instrument.id)
        assertNotEquals(current, repository.instruments().first { it.id == instrument.id }.latestPrice)
    }

    @Test
    fun `instrument price can be added and becomes latest`() {
        val instrument = repository.instruments().first()
        repository.deleteLatestPrice(instrument.id)
        repository.addPrice(instrument.id, 12345.0)
        assertEquals(12345.0, repository.instruments().first { it.id == instrument.id }.latestPrice)
    }

    @Test
    fun `invalid client is rejected before insert`() {
        val manager = repository.employees().first()
        assertFailsWith<IllegalArgumentException> {
            repository.addClient(NewClient(manager.id, "", "Тест", null, "1995-05-10", "+7000", "test@example.ru", "999"))
        }
        assertFailsWith<IllegalArgumentException> {
            repository.addClient(NewClient(manager.id, "Тестов", "Тест", null, "invalid-date", "+7000", "test@example.ru", "999"))
        }
    }

    @Test
    fun `duplicate employee email is rejected`() {
        val existing = repository.employees().first()
        assertFailsWith<SQLException> {
            repository.addEmployee(EmployeeInput("Новый", "Сотрудник", null, "Аналитик", "+7000", existing.email))
        }
    }

    @Test
    fun `duplicate client email is rejected`() {
        val existing = repository.clients().first()
        val manager = repository.employees().first()
        assertFailsWith<SQLException> {
            repository.addClient(NewClient(manager.id, "Тестов", "Тест", null, "1995-05-10", "+7000", existing.email, "555555"))
        }
    }

    @Test
    fun `duplicate client passport number is rejected`() {
        val existing = repository.clients().first()
        val manager = repository.employees().first()
        assertFailsWith<SQLException> {
            repository.addClient(NewClient(manager.id, "Тестов", "Тест", null, "1995-05-10", "+7000", "unique2@example.ru", existing.passportNumber))
        }
    }

    @Test
    fun `duplicate account number is rejected`() {
        val existing = repository.accounts().first()
        val client = repository.clients().first()
        assertFailsWith<SQLException> { repository.addAccount(client.id, existing.accountNumber) }
    }

    @Test
    fun `duplicate instrument ticker is rejected`() {
        val existing = repository.instruments().first()
        val type = repository.instrumentTypes().first()
        assertFailsWith<SQLException> { repository.addInstrument(type.id, existing.ticker, "Другой", "Эмитент", 100.0) }
    }

    @Test
    fun `trade for missing account is rejected`() {
        val instrument = repository.instruments().first()
        val employee = repository.employees().first()
        val missingAccountId = repository.accounts().maxOf { it.id } + 1000
        val trade = NewTrade(missingAccountId, instrument.id, employee.id, TradeType.BUY, "2026-01-01", 1.0, 100.0, 0.0, null)
        assertFailsWith<IllegalStateException> { repository.addTrade(trade) }
    }

    @Test
    fun `dividend for missing account is rejected`() {
        val instrument = repository.instruments().first()
        val missingAccountId = repository.accounts().maxOf { it.id } + 1000
        assertFailsWith<SQLException> { repository.addDividend(missingAccountId, instrument.id, 100.0, 10.0) }
    }

    @Test
    fun `dynamic trade filter matches individual criteria`() {
        val sample = repository.trades().first()
        assertTrue(repository.trades(TradeFilter(accountId = sample.accountId)).all { it.accountId == sample.accountId })
        assertTrue(repository.trades(TradeFilter(instrumentId = sample.instrumentId)).all { it.instrumentId == sample.instrumentId })
        val client = repository.clients().first { it.fullName == sample.clientName }
        assertTrue(repository.trades(TradeFilter(clientId = client.id)).all { it.clientName == client.fullName })
    }

    @Test
    fun `blank client search returns full list`() {
        assertEquals(repository.clients().size, repository.clients("   ").size)
    }
}
