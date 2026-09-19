package me.autoservice.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import me.autoservice.domain.*
import me.autoservice.persistence.db.AutoServiceDatabase
import me.autoservice.persistence.db.Employee as DbEmployee
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.io.path.createDirectories

class SqlDelightAutoServiceRepository private constructor(
    private val driver: JdbcSqliteDriver,
    private val database: AutoServiceDatabase,
) : AutoServiceRepository {
    private val queries = database.autoServiceQueries

    override fun employees() = queries.selectEmployees().executeAsList().map { it.toDomain() }

    override fun clients(query: String) = if (query.isBlank()) {
        queries.selectClients().executeAsList().map { row ->
            Client(row.id, row.advisor_id, row.last_name, row.first_name, row.middle_name, row.phone, row.email,
                row.registration_date, ClientStatus.valueOf(row.status), row.advisor_name, row.vehicle_count)
        }
    } else {
        // SQLite's builtin lower() only folds ASCII, so the query is normalized here with
        // Kotlin's Unicode-aware lowercase() before being matched against search_name.
        queries.searchClients(query.trim().lowercase()).executeAsList().map { row ->
            Client(row.id, row.advisor_id, row.last_name, row.first_name, row.middle_name, row.phone, row.email,
                row.registration_date, ClientStatus.valueOf(row.status), row.advisor_name, row.vehicle_count)
        }
    }

    override fun vehicles() = queries.selectVehicles().executeAsList().map {
        Vehicle(it.id, it.client_id, it.make, it.model, it.year, it.license_plate, it.vin,
            VehicleStatus.valueOf(it.status), it.registered_at, it.client_name)
    }

    override fun serviceCategories() = queries.selectServiceCategories().executeAsList().map {
        ServiceCategory(it.id, it.name, it.description)
    }

    override fun services() = queries.selectServices().executeAsList().map {
        Service(it.id, it.category_id, it.code, it.name, it.is_active == 1L, it.category_name,
            it.latest_price, it.latest_price_date)
    }

    override fun parts() = queries.selectParts().executeAsList().map {
        Part(it.id, it.sku, it.name, it.unit_price, it.quantity_in_stock, it.is_active == 1L)
    }

    override fun workOrders(filter: WorkOrderFilter) = queries.filterWorkOrders(
        filter.vehicleId, filter.clientId, filter.serviceId, filter.status?.name,
        filter.dateFrom, filter.dateTo, filter.amountMin, filter.amountMax,
    ).executeAsList().map {
        WorkOrder(it.id, it.vehicle_id, it.service_id, it.mechanic_id, WorkOrderStatus.valueOf(it.status),
            it.open_date, it.quantity, it.unit_price, it.discount, it.comment, it.client_name,
            it.license_plate, it.service_code, it.service_name, it.mechanic_name)
    }

    override fun partUsages() = queries.selectPartUsages().executeAsList().map {
        PartUsage(it.id, it.work_order_id, it.part_id, it.usage_date, it.amount, it.discount_amount,
            it.license_plate, it.part_name)
    }

    override fun payments() = queries.selectPayments().executeAsList().map {
        Payment(it.id, it.work_order_id, it.payment_date, it.amount, it.discount_amount,
            it.license_plate, it.client_name)
    }

    override fun clientSpendingSummaries() = queries.clientSpendingSummary().executeAsList().map {
        ClientSpendingSummary(it.client_id, it.client_name, it.vehicle_count, it.total_paid)
    }

    override fun monthlyRevenue() = queries.monthlyRevenue().executeAsList().map {
        MonthlyRevenue(it.month ?: "", it.services ?: 0.0, it.parts ?: 0.0)
    }

    override fun categoryRevenue() = queries.categoryRevenue().executeAsList().map {
        CategoryRevenue(it.category_name, it.amount ?: 0.0)
    }

    override fun dashboard(): Dashboard = queries.dashboardCounts().executeAsOne().let {
        Dashboard(it.client_count, it.vehicle_count, it.service_count, it.work_order_count)
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

    override fun restoreEmployee(id: Long) {
        val employee = employees().firstOrNull { it.id == id } ?: error("Сотрудник не найден")
        queries.updateEmployee(employee.lastName, employee.firstName, employee.middleName, employee.position,
            employee.phone, employee.email, 1L, id)
    }

    override fun addClient(client: NewClient) {
        client.validate()
        val now = LocalDateTime.now().toString()
        queries.insertClient(client.advisorId, client.lastName.trim(), client.firstName.trim(),
            client.middleName?.trim()?.ifBlank { null }, client.phone.trim(), client.email.trim(),
            LocalDate.now().toString(), ClientStatus.ACTIVE.name,
            searchName(client.lastName, client.firstName, client.middleName), now, now)
    }

    override fun updateClient(id: Long, client: ClientUpdate) {
        NewClient(client.advisorId, client.lastName, client.firstName, client.middleName, client.phone, client.email).validate()
        queries.updateClient(client.advisorId, client.lastName.trim(), client.firstName.trim(), client.middleName.normalized(),
            client.phone.trim(), client.email.trim(), client.status.name,
            searchName(client.lastName, client.firstName, client.middleName), LocalDateTime.now().toString(), id)
    }

    override fun updateClientStatus(id: Long, status: ClientStatus) {
        queries.updateClientStatus(status.name, LocalDateTime.now().toString(), id)
    }

    override fun addVehicle(clientId: Long, make: String, model: String, year: Long, licensePlate: String, vin: String) {
        require(make.isNotBlank() && model.isNotBlank()) { "Укажите марку и модель" }
        require(licensePlate.isNotBlank()) { "Укажите госномер" }
        require(vin.isNotBlank()) { "Укажите VIN" }
        queries.insertVehicle(clientId, make.trim(), model.trim(), year, licensePlate.trim().uppercase(),
            vin.trim().uppercase(), LocalDate.now().toString())
    }

    override fun updateVehicle(id: Long, clientId: Long, make: String, model: String, year: Long, licensePlate: String, vin: String) {
        require(make.isNotBlank() && model.isNotBlank()) { "Укажите марку и модель" }
        require(licensePlate.isNotBlank()) { "Укажите госномер" }
        require(vin.isNotBlank()) { "Укажите VIN" }
        queries.updateVehicle(clientId, make.trim(), model.trim(), year, licensePlate.trim().uppercase(), vin.trim().uppercase(), id)
    }

    override fun updateVehicleStatus(id: Long, status: VehicleStatus) {
        queries.updateVehicleStatus(status.name, if (status == VehicleStatus.ACTIVE) null else LocalDate.now().toString(), id)
    }

    override fun addService(typeId: Long, code: String, name: String, price: Double) {
        require(code.isNotBlank() && name.isNotBlank()) { "Заполните данные услуги" }
        require(price > 0) { "Цена должна быть больше нуля" }
        queries.transaction {
            queries.insertService(typeId, code.uppercase(), name.trim(), 1L, LocalDateTime.now().toString())
            val service = queries.selectServices().executeAsList().first { it.code == code.uppercase() }
            queries.insertServicePrice(service.id, LocalDate.now().toString(), price)
        }
    }

    override fun updateService(id: Long, service: ServiceInput) {
        require(service.code.isNotBlank() && service.name.isNotBlank()) { "Заполните данные услуги" }
        val current = services().firstOrNull { it.id == id } ?: error("Услуга не найдена")
        queries.updateService(service.categoryId, service.code.uppercase(), service.name.trim(), if (current.isActive) 1L else 0L, id)
    }

    override fun archiveService(id: Long) {
        val service = services().firstOrNull { it.id == id } ?: error("Услуга не найдена")
        queries.updateService(service.categoryId, service.code, service.name, 0L, id)
    }

    override fun restoreService(id: Long) {
        val service = services().firstOrNull { it.id == id } ?: error("Услуга не найдена")
        queries.updateService(service.categoryId, service.code, service.name, 1L, id)
    }

    override fun addServicePrice(serviceId: Long, price: Double) {
        require(price > 0) { "Цена должна быть больше нуля" }
        queries.insertServicePrice(serviceId, LocalDate.now().toString(), price)
    }

    override fun deleteLatestServicePrice(serviceId: Long) {
        queries.deleteLatestServicePrice(serviceId)
    }

    override fun addPart(sku: String, name: String, unitPrice: Double, quantityInStock: Long) {
        require(sku.isNotBlank() && name.isNotBlank()) { "Заполните данные запчасти" }
        require(unitPrice > 0) { "Цена должна быть больше нуля" }
        require(quantityInStock >= 0) { "Остаток не может быть отрицательным" }
        queries.insertPart(sku.trim().uppercase(), name.trim(), unitPrice, quantityInStock, 1L, LocalDateTime.now().toString())
    }

    override fun updatePart(id: Long, sku: String, name: String, unitPrice: Double, quantityInStock: Long) {
        require(sku.isNotBlank() && name.isNotBlank()) { "Заполните данные запчасти" }
        require(unitPrice > 0) { "Цена должна быть больше нуля" }
        require(quantityInStock >= 0) { "Остаток не может быть отрицательным" }
        val current = parts().firstOrNull { it.id == id } ?: error("Запчасть не найдена")
        queries.updatePart(sku.trim().uppercase(), name.trim(), unitPrice, quantityInStock, if (current.isActive) 1L else 0L, id)
    }

    override fun archivePart(id: Long) {
        val part = parts().firstOrNull { it.id == id } ?: error("Запчасть не найдена")
        queries.updatePart(part.sku, part.name, part.unitPrice, part.quantityInStock, 0L, id)
    }

    override fun restorePart(id: Long) {
        val part = parts().firstOrNull { it.id == id } ?: error("Запчасть не найдена")
        queries.updatePart(part.sku, part.name, part.unitPrice, part.quantityInStock, 1L, id)
    }

    override fun addWorkOrder(workOrder: NewWorkOrder) {
        val vehicle = vehicles().firstOrNull { it.id == workOrder.vehicleId } ?: error("Автомобиль не найден")
        workOrder.validate(vehicle)
        queries.insertWorkOrder(workOrder.vehicleId, workOrder.serviceId, workOrder.mechanicId, workOrder.status.name,
            workOrder.openDate, workOrder.quantity, workOrder.unitPrice, workOrder.discount,
            workOrder.comment.normalized(), LocalDateTime.now().toString())
    }

    override fun updateWorkOrder(id: Long, workOrder: NewWorkOrder) {
        val vehicle = vehicles().firstOrNull { it.id == workOrder.vehicleId } ?: error("Автомобиль не найден")
        workOrder.validate(vehicle)
        queries.updateWorkOrder(workOrder.vehicleId, workOrder.serviceId, workOrder.mechanicId, workOrder.status.name,
            workOrder.openDate, workOrder.quantity, workOrder.unitPrice, workOrder.discount, workOrder.comment.normalized(), id)
    }

    override fun deleteWorkOrder(id: Long) {
        queries.deleteWorkOrder(id)
    }

    override fun addPartUsage(workOrderId: Long, partId: Long, amount: Double, discountAmount: Double) {
        require(amount > 0) { "Сумма должна быть больше нуля" }
        require(discountAmount in 0.0..amount) { "Некорректная сумма скидки" }
        queries.insertPartUsage(workOrderId, partId, LocalDate.now().toString(), amount, discountAmount)
    }

    override fun updatePartUsage(id: Long, workOrderId: Long, partId: Long, amount: Double, discountAmount: Double) {
        require(amount > 0) { "Сумма должна быть больше нуля" }
        require(discountAmount in 0.0..amount) { "Некорректная сумма скидки" }
        val current = partUsages().firstOrNull { it.id == id } ?: error("Расход запчасти не найден")
        queries.updatePartUsage(workOrderId, partId, current.usageDate, amount, discountAmount, id)
    }

    override fun deletePartUsage(id: Long) {
        queries.deletePartUsage(id)
    }

    override fun addPayment(workOrderId: Long, amount: Double, discountAmount: Double) {
        require(amount > 0) { "Сумма должна быть больше нуля" }
        require(discountAmount in 0.0..amount) { "Некорректная сумма скидки" }
        queries.insertPayment(workOrderId, LocalDate.now().toString(), amount, discountAmount)
    }

    override fun updatePayment(id: Long, workOrderId: Long, amount: Double, discountAmount: Double) {
        require(amount > 0) { "Сумма должна быть больше нуля" }
        require(discountAmount in 0.0..amount) { "Некорректная сумма скидки" }
        val current = payments().firstOrNull { it.id == id } ?: error("Платеж не найден")
        queries.updatePayment(workOrderId, current.paymentDate, amount, discountAmount, id)
    }

    override fun deletePayment(id: Long) {
        queries.deletePayment(id)
    }

    override fun close() = driver.close()

    companion object {
        fun open(file: File): SqlDelightAutoServiceRepository {
            file.parentFile?.toPath()?.createDirectories()
            val isNew = !file.exists()
            val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
            if (isNew) AutoServiceDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            val repository = SqlDelightAutoServiceRepository(driver, AutoServiceDatabase(driver))
            repository.seedIfEmpty()
            return repository
        }

        fun inMemory(seed: Boolean = false): SqlDelightAutoServiceRepository {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            AutoServiceDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            val repository = SqlDelightAutoServiceRepository(driver, AutoServiceDatabase(driver))
            if (seed) repository.seedIfEmpty()
            return repository
        }
    }

    private fun seedIfEmpty() {
        if (employees().isNotEmpty()) return
        val now = LocalDateTime.now().toString()
        queries.transaction {
            queries.insertEmployee("Иванов", "Пётр", "Сергеевич", "Мастер-приемщик", "+7 900 000-01-00", "ivanov@autoservice.local", 1L, now)
            queries.insertEmployee("Соколова", "Анна", "Игоревна", "Администратор", "+7 900 000-02-00", "sokolova@autoservice.local", 1L, now)
            queries.insertEmployee("Орлов", "Дмитрий", "Николаевич", "Механик", "+7 900 000-03-00", "orlov@autoservice.local", 1L, now)
            queries.insertEmployee("Кузнецов", "Игорь", "Олегович", "Механик", "+7 900 000-04-00", "kuznetsov@autoservice.local", 1L, now)
            queries.insertEmployee("Смирнова", "Елена", "Викторовна", "Мастер-приемщик", "+7 900 000-05-00", "smirnova@autoservice.local", 1L, now)
            queries.insertServiceCategory("Техническое обслуживание", "Плановое обслуживание автомобиля")
            queries.insertServiceCategory("Ремонт двигателя", "Диагностика и ремонт силового агрегата")
            queries.insertServiceCategory("Ремонт ходовой", "Подвеска, тормоза, рулевое управление")
            queries.insertServiceCategory("Кузовной ремонт", "Рихтовка, покраска, полировка")
            queries.insertServiceCategory("Диагностика", "Компьютерная и визуальная диагностика")
            queries.insertServiceCategory("Шиномонтаж", "Замена и балансировка колес")
        }
        val advisors = employees().filter { it.role == UserRole.ADVISOR }
        val mechanics = employees().filter { it.role == UserRole.MECHANIC }
        repeat(15) { index ->
            val n = index + 1
            queries.insertClient(advisors[index % advisors.size].id, "Клиент$n", "Имя$n", null,
                "+7 901 000-${n.toString().padStart(2, '0')}-00", "client$n@example.ru",
                LocalDate.now().minusDays(index.toLong()).toString(), ClientStatus.ACTIVE.name,
                searchName("Клиент$n", "Имя$n", null), now, now)
        }
        val seededClients = clients()
        val makes = listOf("Toyota" to "Camry", "Kia" to "Rio", "Hyundai" to "Solaris", "Lada" to "Vesta",
            "Volkswagen" to "Polo", "Skoda" to "Octavia", "Renault" to "Duster", "Nissan" to "Qashqai",
            "Ford" to "Focus", "Mazda" to "3")
        seededClients.forEachIndexed { index, client ->
            val (make, model) = makes[index % makes.size]
            queries.insertVehicle(client.id, make, model, (2015 + index % 9).toLong(),
                "А${(100 + index)}МК${(1 + index % 99).toString().padStart(2, '0')}",
                "VIN${(1000000 + index).toString().padStart(17, '0')}", LocalDate.now().minusDays(index.toLong()).toString())
        }
        seededClients.take(5).forEachIndexed { index, client ->
            val (make, model) = makes[(index + 3) % makes.size]
            queries.insertVehicle(client.id, make, model, (2016 + index % 8).toLong(),
                "В${(200 + index)}МК${(1 + index % 99).toString().padStart(2, '0')}",
                "VIN${(2000000 + index).toString().padStart(17, '0')}", LocalDate.now().minusDays(index.toLong()).toString())
        }
        val categories = serviceCategories()
        val serviceNames = listOf("Замена масла", "Замена фильтров", "Замена тормозных колодок", "Диагностика подвески",
            "Компьютерная диагностика", "Ремонт двигателя", "Замена ремня ГРМ", "Развал-схождение",
            "Шиномонтаж легковой", "Балансировка колес", "Покраска элемента кузова", "Полировка кузова",
            "Замена свечей зажигания", "Промывка инжектора", "Замена аккумулятора")
        serviceNames.forEachIndexed { index, name ->
            queries.insertService(categories[index % categories.size].id, "S${(index + 1).toString().padStart(3, '0')}",
                name, 1L, now)
        }
        services().forEachIndexed { index, service ->
            queries.insertServicePrice(service.id, LocalDate.now().minusDays(30).toString(), 800.0 + index * 150)
            queries.insertServicePrice(service.id, LocalDate.now().toString(), 900.0 + index * 160)
        }
        val partNames = listOf("Масляный фильтр", "Воздушный фильтр", "Салонный фильтр", "Тормозные колодки передние",
            "Тормозные колодки задние", "Свеча зажигания", "Ремень ГРМ", "Аккумулятор", "Моторное масло 5W-30",
            "Антифриз", "Амортизатор передний", "Стойка стабилизатора")
        partNames.forEachIndexed { index, name ->
            queries.insertPart("P${(index + 1).toString().padStart(4, '0')}", name, 350.0 + index * 120,
                (20 + index * 3).toLong(), 1L, now)
        }
        val vehiclesSeeded = vehicles()
        val servicesSeeded = services()
        val statuses = WorkOrderStatus.entries
        repeat(55) { index ->
            val vehicle = vehiclesSeeded[index % vehiclesSeeded.size]
            val service = servicesSeeded[index % servicesSeeded.size]
            val status = if (index % 6 == 0) statuses[index % statuses.size] else WorkOrderStatus.COMPLETED
            queries.insertWorkOrder(vehicle.id, service.id, mechanics[index % mechanics.size].id, status.name,
                LocalDate.now().minusDays((index % 40 + 1).toLong()).toString(), (index % 3 + 1).toDouble(),
                service.latestPrice ?: 900.0, 50.0, "Плановые работы", now)
        }
        val partsSeeded = parts()
        val workOrdersSeeded = workOrders()
        repeat(30) { index ->
            val workOrder = workOrdersSeeded[index % workOrdersSeeded.size]
            val part = partsSeeded[index % partsSeeded.size]
            queries.insertPartUsage(workOrder.id, part.id, LocalDate.now().minusDays(index.toLong()).toString(),
                part.unitPrice, 20.0)
        }
        workOrdersSeeded.filter { it.status == WorkOrderStatus.COMPLETED }.forEachIndexed { index, workOrder ->
            queries.insertPayment(workOrder.id, LocalDate.now().minusDays(index.toLong()).toString(), workOrder.amount, 30.0)
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
