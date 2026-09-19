package me.autoservice.domain

enum class ClientStatus { ACTIVE, BLOCKED, ARCHIVED }
enum class VehicleStatus { ACTIVE, SOLD, BLOCKED }
enum class WorkOrderStatus { NEW, IN_PROGRESS, COMPLETED, CANCELLED }
enum class UserRole { ADMINISTRATOR, ADVISOR, MECHANIC }

val STANDARD_POSITIONS = listOf("Администратор", "Мастер-приемщик", "Механик")

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
            position.contains("механик", ignoreCase = true) -> UserRole.MECHANIC
            position.contains("администратор", ignoreCase = true) -> UserRole.ADMINISTRATOR
            else -> UserRole.ADVISOR
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
    val advisorId: Long,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val phone: String,
    val email: String,
    val registrationDate: String,
    val status: ClientStatus,
    val advisorName: String,
    val vehicleCount: Long,
) {
    val fullName: String get() = listOfNotNull(lastName, firstName, middleName).joinToString(" ")
}

data class ServiceCategory(
    val id: Long,
    val name: String,
    val description: String?,
)

data class Vehicle(
    val id: Long,
    val clientId: Long,
    val make: String,
    val model: String,
    val year: Long,
    val licensePlate: String,
    val vin: String,
    val status: VehicleStatus,
    val registeredAt: String,
    val clientName: String,
)

data class Service(
    val id: Long,
    val categoryId: Long,
    val code: String,
    val name: String,
    val isActive: Boolean,
    val categoryName: String,
    val latestPrice: Double?,
    val latestPriceDate: String?,
)

data class Part(
    val id: Long,
    val sku: String,
    val name: String,
    val unitPrice: Double,
    val quantityInStock: Long,
    val isActive: Boolean,
)

data class WorkOrder(
    val id: Long,
    val vehicleId: Long,
    val serviceId: Long,
    val mechanicId: Long,
    val status: WorkOrderStatus,
    val openDate: String,
    val quantity: Double,
    val unitPrice: Double,
    val discount: Double,
    val comment: String?,
    val clientName: String,
    val licensePlate: String,
    val serviceCode: String,
    val serviceName: String,
    val mechanicName: String,
) {
    val amount: Double get() = quantity * unitPrice
}

data class PartUsage(
    val id: Long,
    val workOrderId: Long,
    val partId: Long,
    val usageDate: String,
    val amount: Double,
    val discountAmount: Double,
    val licensePlate: String,
    val partName: String,
) {
    val netAmount: Double get() = amount - discountAmount
}

data class Payment(
    val id: Long,
    val workOrderId: Long,
    val paymentDate: String,
    val amount: Double,
    val discountAmount: Double,
    val licensePlate: String,
    val clientName: String,
) {
    val netAmount: Double get() = amount - discountAmount
}

data class ClientSpendingSummary(
    val clientId: Long,
    val clientName: String,
    val vehicleCount: Long,
    val totalPaid: Double,
)

data class MonthlyRevenue(val month: String, val services: Double, val parts: Double)
data class CategoryRevenue(val categoryName: String, val amount: Double)
data class Dashboard(val clientCount: Long, val vehicleCount: Long, val serviceCount: Long, val workOrderCount: Long)

data class WorkOrderFilter(
    val vehicleId: Long? = null,
    val clientId: Long? = null,
    val serviceId: Long? = null,
    val status: WorkOrderStatus? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val amountMin: Double? = null,
    val amountMax: Double? = null,
)

data class NewClient(
    val advisorId: Long,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val phone: String,
    val email: String,
)

data class ClientUpdate(
    val advisorId: Long,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val phone: String,
    val email: String,
    val status: ClientStatus,
)

data class ServiceInput(
    val categoryId: Long,
    val code: String,
    val name: String,
)

data class NewWorkOrder(
    val vehicleId: Long,
    val serviceId: Long,
    val mechanicId: Long,
    val status: WorkOrderStatus,
    val openDate: String,
    val quantity: Double,
    val unitPrice: Double,
    val discount: Double,
    val comment: String?,
)

fun NewClient.validate() {
    require(lastName.isNotBlank()) { "Укажите фамилию" }
    require(firstName.isNotBlank()) { "Укажите имя" }
    require(email.contains('@')) { "Некорректный email" }
    require(phone.isNotBlank()) { "Укажите телефон" }
}

fun NewWorkOrder.validate(vehicle: Vehicle) {
    require(vehicle.status == VehicleStatus.ACTIVE) { "Автомобиль продан или заблокирован" }
    require(quantity > 0) { "Количество должно быть больше нуля" }
    require(unitPrice > 0) { "Цена должна быть больше нуля" }
    require(discount >= 0) { "Скидка не может быть отрицательной" }
}
