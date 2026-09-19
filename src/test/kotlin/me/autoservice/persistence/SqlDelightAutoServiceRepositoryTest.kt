package me.autoservice.persistence

import me.autoservice.domain.*
import java.sql.SQLException
import kotlin.test.*

class SqlDelightAutoServiceRepositoryTest {
    private lateinit var repository: SqlDelightAutoServiceRepository

    @BeforeTest fun setUp() { repository = SqlDelightAutoServiceRepository.inMemory(seed = true) }
    @AfterTest fun tearDown() { repository.close() }

    @Test
    fun `seed creates complete demonstration data`() {
        assertEquals(5, repository.employees().size)
        assertEquals(15, repository.clients().size)
        assertEquals(20, repository.vehicles().size)
        assertEquals(15, repository.services().size)
        assertEquals(6, repository.serviceCategories().size)
        assertEquals(55, repository.workOrders().size)
        assertEquals(30, repository.partUsages().size)
        assertTrue(repository.payments().isNotEmpty())
        assertTrue(repository.workOrders().any { it.status == WorkOrderStatus.COMPLETED })
    }

    @Test
    fun `client can be inserted and searched by email`() {
        val advisor = repository.employees().first { it.role == UserRole.ADVISOR }
        repository.addClient(NewClient(advisor.id, "Тестов", "Тест", null, "+79990000000", "unique@example.ru"))
        val found = repository.clients("unique@example.ru")
        assertEquals(1, found.size)
        assertEquals("Тестов Тест", found.single().fullName)
    }

    @Test
    fun `client search is case-insensitive for cyrillic names`() {
        val advisor = repository.employees().first { it.role == UserRole.ADVISOR }
        repository.addClient(NewClient(advisor.id, "Смирнов", "Алексей", null, "+7 900 000-02-00", "smirnov@example.ru"))
        assertTrue(repository.clients("смирнов").any { it.lastName == "Смирнов" })
        assertTrue(repository.clients("АЛЕКСЕЙ").any { it.firstName == "Алексей" })
    }

    @Test
    fun `dynamic work order filter combines status date and amount`() {
        val all = repository.workOrders()
        val sample = all.first { it.status == WorkOrderStatus.COMPLETED }
        val filtered = repository.workOrders(WorkOrderFilter(status = WorkOrderStatus.COMPLETED,
            dateFrom = sample.openDate, dateTo = sample.openDate, amountMin = sample.amount, amountMax = sample.amount))
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.status == WorkOrderStatus.COMPLETED && it.openDate == sample.openDate && it.amount == sample.amount })
    }

    @Test
    fun `client spending summary vehicle counts add up to all vehicles`() {
        val summaries = repository.clientSpendingSummaries()
        assertEquals(repository.vehicles().size.toLong(), summaries.sumOf { it.vehicleCount })
    }

    @Test
    fun `dashboard and reports return aggregate data`() {
        val dashboard = repository.dashboard()
        assertEquals(15, dashboard.clientCount)
        assertEquals(20, dashboard.vehicleCount)
        assertTrue(repository.monthlyRevenue().isNotEmpty())
        assertTrue(repository.categoryRevenue().isNotEmpty())
    }

    @Test
    fun `work order requires an active vehicle`() {
        val vehicle = repository.vehicles().first()
        val service = repository.services().first()
        val mechanic = repository.employees().first { it.role == UserRole.MECHANIC }
        repository.updateVehicleStatus(vehicle.id, VehicleStatus.SOLD)
        val order = NewWorkOrder(vehicle.id, service.id, mechanic.id, WorkOrderStatus.NEW, "2026-01-01", 1.0, 1000.0, 0.0, null)
        assertFailsWith<IllegalArgumentException> { repository.addWorkOrder(order) }
    }

    @Test
    fun `employee can be created edited and archived`() {
        repository.addEmployee(EmployeeInput("Новый", "Сотрудник", null, "Администратор", "+7001", "admin@example.ru"))
        val employee = repository.employees().first { it.email == "admin@example.ru" }
        repository.updateEmployee(employee.id, EmployeeInput("Новый", "Администратор", null, "Администратор", "+7002", "admin@example.ru"))
        assertEquals("Администратор", repository.employees().first { it.id == employee.id }.firstName)
        repository.archiveEmployee(employee.id)
        assertFalse(repository.employees().first { it.id == employee.id }.isActive)
        repository.restoreEmployee(employee.id)
        assertTrue(repository.employees().first { it.id == employee.id }.isActive)
    }

    @Test
    fun `client vehicle and service support edit and archive lifecycle`() {
        val client = repository.clients().first()
        val parts = client.fullName.split(" ")
        repository.updateClient(client.id, ClientUpdate(client.advisorId, parts[0], "Изменен", null, client.phone, client.email, client.status))
        assertTrue(repository.clients().first { it.id == client.id }.fullName.contains("Изменен"))
        repository.updateClientStatus(client.id, ClientStatus.ARCHIVED)
        assertEquals(ClientStatus.ARCHIVED, repository.clients().first { it.id == client.id }.status)

        val vehicle = repository.vehicles().first()
        repository.updateVehicle(vehicle.id, vehicle.clientId, vehicle.make, "Изменена", vehicle.year, vehicle.licensePlate, vehicle.vin)
        assertEquals("Изменена", repository.vehicles().first { it.id == vehicle.id }.model)
        repository.updateVehicleStatus(vehicle.id, VehicleStatus.SOLD)
        assertEquals(VehicleStatus.SOLD, repository.vehicles().first { it.id == vehicle.id }.status)

        val service = repository.services().first()
        repository.updateService(service.id, ServiceInput(service.categoryId, service.code, "Измененная услуга"))
        assertEquals("Измененная услуга", repository.services().first { it.id == service.id }.name)
        repository.archiveService(service.id)
        assertFalse(repository.services().first { it.id == service.id }.isActive)
        repository.restoreService(service.id)
        assertTrue(repository.services().first { it.id == service.id }.isActive)
    }

    @Test
    fun `part supports edit and archive lifecycle`() {
        val part = repository.parts().first()
        repository.updatePart(part.id, part.sku, "Изменена", part.unitPrice, part.quantityInStock)
        assertEquals("Изменена", repository.parts().first { it.id == part.id }.name)
        repository.archivePart(part.id)
        assertFalse(repository.parts().first { it.id == part.id }.isActive)
        repository.restorePart(part.id)
        assertTrue(repository.parts().first { it.id == part.id }.isActive)
    }

    @Test
    fun `work order without dependents can be updated and deleted`() {
        val vehicle = repository.vehicles().first { it.status == VehicleStatus.ACTIVE }
        val service = repository.services().first()
        val mechanic = repository.employees().first { it.role == UserRole.MECHANIC }
        repository.addWorkOrder(NewWorkOrder(vehicle.id, service.id, mechanic.id, WorkOrderStatus.NEW, "2026-01-01", 1.0, 1000.0, 0.0, null))
        val order = repository.workOrders().first { it.vehicleId == vehicle.id && it.openDate == "2026-01-01" }
        repository.updateWorkOrder(order.id, NewWorkOrder(vehicle.id, service.id, mechanic.id, WorkOrderStatus.COMPLETED, "2026-01-01", 2.0, 1000.0, 0.0, "Изменено"))
        assertEquals("Изменено", repository.workOrders().first { it.id == order.id }.comment)
        val before = repository.workOrders().size
        repository.deleteWorkOrder(order.id)
        assertEquals(before - 1, repository.workOrders().size)
    }

    @Test
    fun `part usage and payment can be updated and deleted`() {
        val usage = repository.partUsages().first()
        repository.updatePartUsage(usage.id, usage.workOrderId, usage.partId, 500.0, 50.0)
        assertEquals(450.0, repository.partUsages().first { it.id == usage.id }.netAmount)
        repository.deletePartUsage(usage.id)
        assertTrue(repository.partUsages().none { it.id == usage.id })

        val payment = repository.payments().first()
        repository.updatePayment(payment.id, payment.workOrderId, 2000.0, 200.0)
        assertEquals(1800.0, repository.payments().first { it.id == payment.id }.netAmount)
        repository.deletePayment(payment.id)
        assertTrue(repository.payments().none { it.id == payment.id })
    }

    @Test
    fun `latest service price can be removed`() {
        val service = repository.services().first()
        val current = service.latestPrice
        repository.deleteLatestServicePrice(service.id)
        assertNotEquals(current, repository.services().first { it.id == service.id }.latestPrice)
    }

    @Test
    fun `service price can be added and becomes latest`() {
        val service = repository.services().first()
        repository.deleteLatestServicePrice(service.id)
        repository.addServicePrice(service.id, 12345.0)
        assertEquals(12345.0, repository.services().first { it.id == service.id }.latestPrice)
    }

    @Test
    fun `invalid client is rejected before insert`() {
        val advisor = repository.employees().first { it.role == UserRole.ADVISOR }
        assertFailsWith<IllegalArgumentException> { repository.addClient(NewClient(advisor.id, "", "Тест", null, "+7000", "test@example.ru")) }
        assertFailsWith<IllegalArgumentException> { repository.addClient(NewClient(advisor.id, "Тестов", "Тест", null, "+7000", "invalid-email")) }
    }

    @Test
    fun `duplicate employee email is rejected`() {
        val existing = repository.employees().first()
        assertFailsWith<SQLException> {
            repository.addEmployee(EmployeeInput("Новый", "Сотрудник", null, "Механик", "+7000", existing.email))
        }
    }

    @Test
    fun `duplicate vehicle license plate is rejected`() {
        val existing = repository.vehicles().first()
        val client = repository.clients().first()
        assertFailsWith<SQLException> {
            repository.addVehicle(client.id, "Toyota", "Corolla", 2021, existing.licensePlate, "VIN99999999999999X")
        }
    }

    @Test
    fun `duplicate service code is rejected`() {
        val existing = repository.services().first()
        val category = repository.serviceCategories().first()
        assertFailsWith<SQLException> { repository.addService(category.id, existing.code, "Другая услуга", 500.0) }
    }

    @Test
    fun `duplicate part sku is rejected`() {
        val existing = repository.parts().first()
        assertFailsWith<SQLException> { repository.addPart(existing.sku, "Другая запчасть", 500.0, 10) }
    }

    @Test
    fun `work order for missing vehicle is rejected`() {
        val service = repository.services().first()
        val mechanic = repository.employees().first { it.role == UserRole.MECHANIC }
        val missingVehicleId = repository.vehicles().maxOf { it.id } + 1000
        val order = NewWorkOrder(missingVehicleId, service.id, mechanic.id, WorkOrderStatus.NEW, "2026-01-01", 1.0, 1000.0, 0.0, null)
        assertFailsWith<IllegalStateException> { repository.addWorkOrder(order) }
    }

    @Test
    fun `work order with dependents cannot be deleted`() {
        val payment = repository.payments().first()
        assertFailsWith<SQLException> { repository.deleteWorkOrder(payment.workOrderId) }
    }

    @Test
    fun `dynamic work order filter matches individual criteria`() {
        val sample = repository.workOrders().first()
        assertTrue(repository.workOrders(WorkOrderFilter(vehicleId = sample.vehicleId)).all { it.vehicleId == sample.vehicleId })
        assertTrue(repository.workOrders(WorkOrderFilter(serviceId = sample.serviceId)).all { it.serviceId == sample.serviceId })
        val client = repository.clients().first { it.fullName == sample.clientName }
        assertTrue(repository.workOrders(WorkOrderFilter(clientId = client.id)).all { it.clientName == client.fullName })
    }

    @Test
    fun `blank client search returns full list`() {
        assertEquals(repository.clients().size, repository.clients("   ").size)
    }
}
