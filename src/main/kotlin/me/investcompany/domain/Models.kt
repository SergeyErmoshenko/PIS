package me.investcompany.domain

enum class ClientStatus { ACTIVE, BLOCKED, ARCHIVED }
enum class AccountStatus { OPEN, CLOSED, BLOCKED }
enum class TradeType { BUY, SELL }
enum class UserRole { ADMINISTRATOR, MANAGER, ANALYST }

data class Employee(
    val id: Long,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val position: String,
    val phone: String,
    val email: String,
    val isActive: Boolean,
) {
    val fullName: String get() = listOfNotNull(lastName, firstName, middleName).joinToString(" ")
    val role: UserRole
        get() = when {
            position.contains("аналитик", ignoreCase = true) -> UserRole.ANALYST
            position.contains("администратор", ignoreCase = true) -> UserRole.ADMINISTRATOR
            else -> UserRole.MANAGER
        }
}

data class EmployeeInput(
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val position: String,
    val phone: String,
    val email: String,
)

data class Client(
    val id: Long,
    val managerId: Long,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val birthDate: String,
    val phone: String,
    val email: String,
    val passportNumber: String,
    val registrationDate: String,
    val status: ClientStatus,
    val managerName: String,
    val accountCount: Long,
) {
    val fullName: String get() = listOfNotNull(lastName, firstName, middleName).joinToString(" ")
}

data class InstrumentType(
    val id: Long,
    val name: String,
    val description: String?,
)

data class Account(
    val id: Long,
    val clientId: Long,
    val accountNumber: String,
    val openedAt: String,
    val closedAt: String?,
    val status: AccountStatus,
    val currency: String,
    val clientName: String,
)

data class Instrument(
    val id: Long,
    val typeId: Long,
    val ticker: String,
    val name: String,
    val issuer: String,
    val currency: String,
    val isActive: Boolean,
    val typeName: String,
    val latestPrice: Double?,
    val latestPriceDate: String?,
)

data class Trade(
    val id: Long,
    val accountId: Long,
    val instrumentId: Long,
    val employeeId: Long,
    val type: TradeType,
    val date: String,
    val quantity: Double,
    val unitPrice: Double,
    val commission: Double,
    val comment: String?,
    val clientName: String,
    val accountNumber: String,
    val ticker: String,
    val instrumentName: String,
    val employeeName: String,
) {
    val amount: Double get() = quantity * unitPrice
}

data class Dividend(
    val id: Long,
    val accountId: Long,
    val instrumentId: Long,
    val paymentDate: String,
    val amount: Double,
    val taxAmount: Double,
    val clientName: String,
    val accountNumber: String,
    val ticker: String,
) {
    val netAmount: Double get() = amount - taxAmount
}

data class PortfolioPosition(
    val accountId: Long,
    val accountNumber: String,
    val clientId: Long,
    val clientName: String,
    val instrumentId: Long,
    val ticker: String,
    val instrumentName: String,
    val quantity: Double,
    val averageBuyPrice: Double,
    val latestPrice: Double?,
) {
    val currentValue: Double get() = quantity * (latestPrice ?: 0.0)
    val profit: Double get() = currentValue - quantity * averageBuyPrice
}

data class PortfolioSummary(
    val clientId: Long,
    val clientName: String,
    val instrumentCount: Long,
    val currentValue: Double,
)

data class MonthlyTurnover(val month: String, val buy: Double, val sell: Double)
data class AssetAllocation(val typeName: String, val currentValue: Double)
data class Dashboard(val clientCount: Long, val accountCount: Long, val instrumentCount: Long, val tradeCount: Long)

data class TradeFilter(
    val clientId: Long? = null,
    val accountId: Long? = null,
    val instrumentId: Long? = null,
    val type: TradeType? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val amountMin: Double? = null,
    val amountMax: Double? = null,
)

data class NewClient(
    val managerId: Long,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val birthDate: String,
    val phone: String,
    val email: String,
    val passportNumber: String,
)

data class ClientUpdate(
    val managerId: Long,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val birthDate: String,
    val phone: String,
    val email: String,
    val passportNumber: String,
    val status: ClientStatus,
)

data class InstrumentInput(
    val typeId: Long,
    val ticker: String,
    val name: String,
    val issuer: String,
    val currency: String = "RUB",
)

data class NewTrade(
    val accountId: Long,
    val instrumentId: Long,
    val employeeId: Long,
    val type: TradeType,
    val date: String,
    val quantity: Double,
    val unitPrice: Double,
    val commission: Double,
    val comment: String?,
)

fun NewClient.validate() {
    require(lastName.isNotBlank()) { "Укажите фамилию" }
    require(firstName.isNotBlank()) { "Укажите имя" }
    require(email.contains('@')) { "Некорректный email" }
    require(phone.isNotBlank()) { "Укажите телефон" }
    require(passportNumber.isNotBlank()) { "Укажите номер паспорта" }
    require(birthDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Дата рождения должна иметь формат ГГГГ-ММ-ДД" }
}

fun NewTrade.validate(account: Account, availableQuantity: Double) {
    require(account.status == AccountStatus.OPEN) { "Счет закрыт или заблокирован" }
    require(quantity > 0) { "Количество должно быть больше нуля" }
    require(unitPrice > 0) { "Цена должна быть больше нуля" }
    require(commission >= 0) { "Комиссия не может быть отрицательной" }
    if (type == TradeType.SELL) {
        require(quantity <= availableQuantity) { "Недостаточно инструментов для продажи" }
    }
}
