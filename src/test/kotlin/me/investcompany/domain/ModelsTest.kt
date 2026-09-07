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
}
