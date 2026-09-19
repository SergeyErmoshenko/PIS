package me.autoservice.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelsTest {
    @Test
    fun `client requires valid email`() {
        val client = NewClient(1, "Иванов", "Иван", null, "+7000", "invalid")
        assertFailsWith<IllegalArgumentException> { client.validate() }
    }

    @Test
    fun `work order requires an active vehicle`() {
        val vehicle = Vehicle(1, 1, "Toyota", "Camry", 2020, "А000АА00", "VIN1", VehicleStatus.SOLD, "2026-01-01", "Клиент")
        val workOrder = NewWorkOrder(1, 1, 1, WorkOrderStatus.NEW, "2026-01-01", 1.0, 1000.0, 0.0, null)
        assertFailsWith<IllegalArgumentException> { workOrder.validate(vehicle) }
    }

    @Test
    fun `payment and part usage calculate net amount`() {
        val payment = Payment(1, 1, "2026-01-01", 1000.0, 100.0, "А000АА00", "Клиент")
        assertEquals(900.0, payment.netAmount)
        val usage = PartUsage(1, 1, 1, "2026-01-01", 500.0, 50.0, "А000АА00", "Масляный фильтр")
        assertEquals(450.0, usage.netAmount)
    }

    @Test
    fun `employee position determines selectable user role`() {
        assertEquals(UserRole.MECHANIC, Employee(1, "Иванов", "Иван", null, "Механик", "1", "a@b.ru", true).role)
        assertEquals(UserRole.ADMINISTRATOR, Employee(2, "Петров", "Петр", null, "Администратор", "1", "c@d.ru", true).role)
        assertEquals(UserRole.ADVISOR, Employee(3, "Сидоров", "Сидор", null, "Мастер-приемщик", "1", "e@f.ru", true).role)
    }

    @Test
    fun `employee role matching ignores case`() {
        assertEquals(UserRole.MECHANIC, Employee(1, "Иванов", "Иван", null, "МЕХАНИК", "1", "a@b.ru", true).role)
        assertEquals(UserRole.ADMINISTRATOR, Employee(2, "Петров", "Петр", null, "АДМИНИСТРАТОР", "1", "c@d.ru", true).role)
    }

    @Test
    fun `client full name omits blank middle name`() {
        val client = Client(1, 1, "Иванов", "Иван", null, "+7000", "a@b.ru", "2026-01-01", ClientStatus.ACTIVE, "Петров Петр", 0)
        assertEquals("Иванов Иван", client.fullName)
    }

    @Test
    fun `client requires non-blank name fields and phone`() {
        assertFailsWith<IllegalArgumentException> { NewClient(1, "", "Иван", null, "+7000", "a@b.ru").validate() }
        assertFailsWith<IllegalArgumentException> { NewClient(1, "Иванов", "", null, "+7000", "a@b.ru").validate() }
        assertFailsWith<IllegalArgumentException> { NewClient(1, "Иванов", "Иван", null, "", "a@b.ru").validate() }
    }

    @Test
    fun `work order requires positive quantity price and non-negative discount`() {
        val vehicle = Vehicle(1, 1, "Toyota", "Camry", 2020, "А000АА00", "VIN1", VehicleStatus.ACTIVE, "2026-01-01", "Клиент")
        assertFailsWith<IllegalArgumentException> { NewWorkOrder(1, 1, 1, WorkOrderStatus.NEW, "2026-01-01", 0.0, 1000.0, 0.0, null).validate(vehicle) }
        assertFailsWith<IllegalArgumentException> { NewWorkOrder(1, 1, 1, WorkOrderStatus.NEW, "2026-01-01", 1.0, 0.0, 0.0, null).validate(vehicle) }
        assertFailsWith<IllegalArgumentException> { NewWorkOrder(1, 1, 1, WorkOrderStatus.NEW, "2026-01-01", 1.0, 1000.0, -1.0, null).validate(vehicle) }
    }

    @Test
    fun `work order amount is quantity times unit price`() {
        val workOrder = WorkOrder(1, 1, 1, 1, WorkOrderStatus.NEW, "2026-01-01", 3.0, 500.0, 50.0, null, "Клиент", "А000АА00", "S001", "Услуга", "Механик")
        assertEquals(1500.0, workOrder.amount)
    }
}
