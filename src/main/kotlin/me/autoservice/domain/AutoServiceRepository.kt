package me.autoservice.domain

interface AutoServiceRepository : AutoCloseable {
    fun employees(): List<Employee>
    fun clients(query: String = ""): List<Client>
    fun vehicles(): List<Vehicle>
    fun serviceCategories(): List<ServiceCategory>
    fun services(): List<Service>
    fun parts(): List<Part>
    fun workOrders(filter: WorkOrderFilter = WorkOrderFilter()): List<WorkOrder>
    fun partUsages(): List<PartUsage>
    fun payments(): List<Payment>
    fun clientSpendingSummaries(): List<ClientSpendingSummary>
    fun monthlyRevenue(): List<MonthlyRevenue>
    fun categoryRevenue(): List<CategoryRevenue>
    fun dashboard(): Dashboard

    fun addEmployee(employee: EmployeeInput)
    fun updateEmployee(id: Long, employee: EmployeeInput)
    fun archiveEmployee(id: Long)
    fun restoreEmployee(id: Long)

    fun addClient(client: NewClient)
    fun updateClient(id: Long, client: ClientUpdate)
    fun updateClientStatus(id: Long, status: ClientStatus)

    fun addVehicle(clientId: Long, make: String, model: String, year: Long, licensePlate: String, vin: String)
    fun updateVehicle(id: Long, clientId: Long, make: String, model: String, year: Long, licensePlate: String, vin: String)
    fun updateVehicleStatus(id: Long, status: VehicleStatus)

    fun addService(typeId: Long, code: String, name: String, price: Double)
    fun updateService(id: Long, service: ServiceInput)
    fun archiveService(id: Long)
    fun restoreService(id: Long)
    fun addServicePrice(serviceId: Long, price: Double)
    fun deleteLatestServicePrice(serviceId: Long)

    fun addPart(sku: String, name: String, unitPrice: Double, quantityInStock: Long)
    fun updatePart(id: Long, sku: String, name: String, unitPrice: Double, quantityInStock: Long)
    fun archivePart(id: Long)
    fun restorePart(id: Long)

    fun addWorkOrder(workOrder: NewWorkOrder)
    fun updateWorkOrder(id: Long, workOrder: NewWorkOrder)
    fun deleteWorkOrder(id: Long)

    fun addPartUsage(workOrderId: Long, partId: Long, amount: Double, discountAmount: Double)
    fun updatePartUsage(id: Long, workOrderId: Long, partId: Long, amount: Double, discountAmount: Double)
    fun deletePartUsage(id: Long)

    fun addPayment(workOrderId: Long, amount: Double, discountAmount: Double)
    fun updatePayment(id: Long, workOrderId: Long, amount: Double, discountAmount: Double)
    fun deletePayment(id: Long)
}
