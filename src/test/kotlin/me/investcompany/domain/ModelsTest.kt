package me.investcompany.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelsTest {
    @Test
    fun `client requires valid email`() {
        val client = NewClient(1, "Иванов", "Иван", null, "1990-01-01", "+7000", "invalid", "1234")
        assertFailsWith<IllegalArgumentException> { client.validate() }
    }

    @Test
    fun `sell cannot exceed available quantity`() {
        val account = Account(1, 1, "A", "2026-01-01", null, AccountStatus.OPEN, "RUB", "Клиент")
        val trade = NewTrade(1, 1, 1, TradeType.SELL, "2026-01-01", 11.0, 10.0, 0.0, null)
        assertFailsWith<IllegalArgumentException> { trade.validate(account, 10.0) }
    }

    @Test
    fun `position calculates current value and profit`() {
        val position = PortfolioPosition(1, "A", 1, "Клиент", 1, "AAA", "Акция", 10.0, 100.0, 125.0)
        assertEquals(1250.0, position.currentValue)
        assertEquals(250.0, position.profit)
    }

    @Test
    fun `employee position determines selectable user role`() {
        assertEquals(UserRole.ANALYST, Employee(1, "Иванов", "Иван", null, "Аналитик", "1", "a@b.ru", true).role)
        assertEquals(UserRole.MANAGER, Employee(2, "Петров", "Петр", null, "Старший менеджер", "1", "c@d.ru", true).role)
    }

    @Test
    fun `employee role matching is case-insensitive`() {
        assertEquals(UserRole.ANALYST, Employee(1, "Иванов", "Иван", null, "АНАЛИТИК", "1", "a@b.ru", true).role)
        assertEquals(UserRole.ADMINISTRATOR, Employee(2, "Петров", "Петр", null, "АДМИНИСТРАТОР", "1", "c@d.ru", true).role)
    }

    @Test
    fun `client full name omits blank middle name`() {
        val client = Client(1, 1, "Иванов", "Иван", null, "1990-01-01", "+7000", "a@b.ru", "1234",
            "2026-01-01", ClientStatus.ACTIVE, "Петров Петр", 0)
        assertEquals("Иванов Иван", client.fullName)
    }

    @Test
    fun `client requires non-blank name fields individually`() {
        assertFailsWith<IllegalArgumentException> { NewClient(1, "", "Иван", null, "1990-01-01", "+7000", "a@b.ru", "1234").validate() }
        assertFailsWith<IllegalArgumentException> { NewClient(1, "Иванов", "", null, "1990-01-01", "+7000", "a@b.ru", "1234").validate() }
        assertFailsWith<IllegalArgumentException> { NewClient(1, "Иванов", "Иван", null, "1990-01-01", "", "a@b.ru", "1234").validate() }
    }

    @Test
    fun `client requires valid passport and birth date format`() {
        assertFailsWith<IllegalArgumentException> { NewClient(1, "Иванов", "Иван", null, "1990-01-01", "+7000", "a@b.ru", "").validate() }
        assertFailsWith<IllegalArgumentException> { NewClient(1, "Иванов", "Иван", null, "01-01-1990", "+7000", "a@b.ru", "1234").validate() }
    }

    @Test
    fun `trade requires positive quantity price and non-negative commission`() {
        val account = Account(1, 1, "A", "2026-01-01", null, AccountStatus.OPEN, "RUB", "Клиент")
        assertFailsWith<IllegalArgumentException> { NewTrade(1, 1, 1, TradeType.BUY, "2026-01-01", 0.0, 100.0, 0.0, null).validate(account, 0.0) }
        assertFailsWith<IllegalArgumentException> { NewTrade(1, 1, 1, TradeType.BUY, "2026-01-01", 1.0, 0.0, 0.0, null).validate(account, 0.0) }
        assertFailsWith<IllegalArgumentException> { NewTrade(1, 1, 1, TradeType.BUY, "2026-01-01", 1.0, 100.0, -1.0, null).validate(account, 0.0) }
    }

    @Test
    fun `trade is rejected for closed or blocked account`() {
        val closed = Account(1, 1, "A", "2026-01-01", "2026-02-01", AccountStatus.CLOSED, "RUB", "Клиент")
        val blocked = Account(2, 1, "B", "2026-01-01", null, AccountStatus.BLOCKED, "RUB", "Клиент")
        val trade = NewTrade(1, 1, 1, TradeType.BUY, "2026-01-01", 1.0, 100.0, 0.0, null)
        assertFailsWith<IllegalArgumentException> { trade.validate(closed, 100.0) }
        assertFailsWith<IllegalArgumentException> { trade.validate(blocked, 100.0) }
    }

    @Test
    fun `trade amount is quantity times unit price`() {
        val trade = Trade(1, 1, 1, 1, TradeType.BUY, "2026-01-01", 3.0, 500.0, 10.0, null, "Клиент", "ACC1", "T1", "Инструмент", "Сотрудник")
        assertEquals(1500.0, trade.amount)
    }

    @Test
    fun `dividend and coupon net amount subtracts tax`() {
        val dividend = Dividend(1, 1, 1, "2026-01-01", 200.0, 26.0, "Клиент", "ACC1", "T1")
        val coupon = Coupon(1, 1, 1, "2026-01-01", 300.0, 39.0, "Клиент", "ACC1", "T1")
        assertEquals(174.0, dividend.netAmount)
        assertEquals(261.0, coupon.netAmount)
    }

    @Test
    fun `portfolio position with no latest price has zero current value`() {
        val position = PortfolioPosition(1, "A", 1, "Клиент", 1, "AAA", "Акция", 10.0, 100.0, null)
        assertEquals(0.0, position.currentValue)
        assertEquals(-1000.0, position.profit)
    }
}
