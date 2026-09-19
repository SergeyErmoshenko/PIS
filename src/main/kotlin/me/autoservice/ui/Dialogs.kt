package me.autoservice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.autoservice.domain.*
import java.time.LocalDate

@Composable
fun EmployeeDialog(value: Employee? = null, close: () -> Unit, save: (EmployeeInput) -> Unit) {
    var lastName by remember { mutableStateOf(value?.lastName.orEmpty()) }; var firstName by remember { mutableStateOf(value?.firstName.orEmpty()) }
    var middleName by remember { mutableStateOf(value?.middleName.orEmpty()) }; var position by remember { mutableStateOf(value?.position ?: STANDARD_POSITIONS[1]) }
    var phone by remember { mutableStateOf(value?.phone.orEmpty()) }; var email by remember { mutableStateOf(value?.email.orEmpty()) }
    FormDialog(if (value == null) "Новый сотрудник" else "Редактирование сотрудника", close, { save(EmployeeInput(lastName, firstName, middleName.ifBlank { null }, position, phone, email)) }) {
        FormField(lastName, { lastName = it }, "Фамилия"); FormField(firstName, { firstName = it }, "Имя"); FormField(middleName, { middleName = it }, "Отчество")
        StringSelector("Должность", STANDARD_POSITIONS, position) { position = it }; FormField(phone, { phone = it }, "Телефон"); FormField(email, { email = it }, "Email")
    }
}

@Composable
fun ClientDialog(employees: List<Employee>, value: Client? = null, close: () -> Unit, save: (NewClient) -> Unit) {
    var advisor by remember { mutableStateOf(value?.advisorId ?: employees.firstOrNull()?.id) }
    var lastName by remember { mutableStateOf(value?.lastName.orEmpty()) }; var firstName by remember { mutableStateOf(value?.firstName.orEmpty()) }; var middleName by remember { mutableStateOf(value?.middleName.orEmpty()) }
    var phone by remember { mutableStateOf(value?.phone.orEmpty()) }; var email by remember { mutableStateOf(value?.email.orEmpty()) }
    FormDialog(if (value == null) "Новый клиент" else "Редактирование клиента", close, { advisor?.let { save(NewClient(it, lastName, firstName, middleName.ifBlank { null }, phone, email)) } }) {
        Selector("Мастер-приемщик", employees, advisor, { it.id }, { it.fullName }) { advisor = it }
        FormField(lastName, { lastName = it }, "Фамилия"); FormField(firstName, { firstName = it }, "Имя"); FormField(middleName, { middleName = it }, "Отчество")
        FormField(phone, { phone = it }, "Телефон"); FormField(email, { email = it }, "Email")
    }
}

@Composable
fun VehicleDialog(clients: List<Client>, value: Vehicle? = null, close: () -> Unit, save: (Long, String, String, Long, String, String) -> Unit) {
    var client by remember { mutableStateOf(value?.clientId ?: clients.firstOrNull()?.id) }
    var make by remember { mutableStateOf(value?.make.orEmpty()) }; var model by remember { mutableStateOf(value?.model.orEmpty()) }
    var year by remember { mutableStateOf(value?.year?.toString() ?: LocalDate.now().year.toString()) }
    var plate by remember { mutableStateOf(value?.licensePlate.orEmpty()) }; var vin by remember { mutableStateOf(value?.vin.orEmpty()) }
    FormDialog(if (value == null) "Новый автомобиль" else "Редактирование автомобиля", close, { client?.let { save(it, make, model, year.toLongOrNull() ?: 0L, plate, vin) } }) {
        Selector("Клиент", clients, client, { it.id }, { it.fullName }) { client = it }
        FormField(make, { make = it }, "Марка"); FormField(model, { model = it }, "Модель"); FormField(year, { year = it }, "Год выпуска")
        FormField(plate, { plate = it }, "Госномер"); FormField(vin, { vin = it }, "VIN")
    }
}

@Composable
fun ServiceDialog(categories: List<ServiceCategory>, value: Service? = null, close: () -> Unit, save: (Long, String, String, Double) -> Unit) {
    var category by remember { mutableStateOf(categories.firstOrNull { it.id == value?.categoryId }?.id ?: categories.firstOrNull()?.id) }
    var code by remember { mutableStateOf(value?.code.orEmpty()) }; var name by remember { mutableStateOf(value?.name.orEmpty()) }; var price by remember { mutableStateOf(value?.latestPrice?.toString().orEmpty()) }
    FormDialog(if (value == null) "Новая услуга" else "Редактирование услуги", close, { category?.let { save(it, code, name, price.toDoubleOrNull() ?: 0.0) } }) {
        Selector("Категория", categories, category, { it.id }, { it.name }) { category = it }
        FormField(code, { code = it }, "Код"); FormField(name, { name = it }, "Название"); if (value == null) FormField(price, { price = it }, "Текущая цена")
    }
}

@Composable fun ServicePriceDialog(service: Service, close: () -> Unit, save: (Double) -> Unit) { var price by remember { mutableStateOf("") }; FormDialog("Новая цена: ${service.code}", close, { save(price.toDoubleOrNull() ?: 0.0) }) { FormField(price, { price = it }, "Цена на сегодня") } }

@Composable
fun PartDialog(value: Part? = null, close: () -> Unit, save: (String, String, Double, Long) -> Unit) {
    var sku by remember { mutableStateOf(value?.sku.orEmpty()) }; var name by remember { mutableStateOf(value?.name.orEmpty()) }
    var price by remember { mutableStateOf(value?.unitPrice?.toString().orEmpty()) }; var quantity by remember { mutableStateOf(value?.quantityInStock?.toString() ?: "0") }
    FormDialog(if (value == null) "Новая запчасть" else "Редактирование запчасти", close, { save(sku, name, price.toDoubleOrNull() ?: 0.0, quantity.toLongOrNull() ?: 0L) }) {
        FormField(sku, { sku = it }, "Артикул"); FormField(name, { name = it }, "Название"); FormField(price, { price = it }, "Цена"); FormField(quantity, { quantity = it }, "Остаток на складе")
    }
}

@Composable
fun WorkOrderDialog(vehicles: List<Vehicle>, services: List<Service>, mechanics: List<Employee>, close: () -> Unit, save: (NewWorkOrder) -> Unit) {
    var vehicle by remember { mutableStateOf(vehicles.firstOrNull()?.id) }; var service by remember { mutableStateOf(services.firstOrNull()?.id) }; var mechanic by remember { mutableStateOf(mechanics.firstOrNull()?.id) }
    var status by remember { mutableStateOf(WorkOrderStatus.NEW) }; var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var quantity by remember { mutableStateOf("1") }; var price by remember { mutableStateOf(services.firstOrNull()?.latestPrice?.toString().orEmpty()) }; var discount by remember { mutableStateOf("0") }; var comment by remember { mutableStateOf("") }
    FormDialog("Новый заказ-наряд", close, { if (vehicle != null && service != null && mechanic != null) save(NewWorkOrder(vehicle!!, service!!, mechanic!!, status, date, quantity.toDoubleOrNull() ?: 0.0, price.toDoubleOrNull() ?: 0.0, discount.toDoubleOrNull() ?: 0.0, comment.ifBlank { null })) }) {
        Selector("Автомобиль", vehicles, vehicle, { it.id }, { "${it.licensePlate} — ${it.clientName}" }) { vehicle = it }
        Selector("Услуга", services, service, { it.id }, { "${it.code} — ${it.name}" }) { selected -> service = selected; price = services.first { it.id == selected }.latestPrice?.toString().orEmpty() }
        Selector("Механик", mechanics, mechanic, { it.id }, { it.fullName }) { mechanic = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { WorkOrderStatus.entries.forEach { value -> FilterChip(status == value, { status = value }, { Text(workOrderStatusLabel(value)) }) } }
        FormField(date, { date = it }, "Дата, ГГГГ-ММ-ДД"); FormField(quantity, { quantity = it }, "Количество"); FormField(price, { price = it }, "Цена"); FormField(discount, { discount = it }, "Скидка"); FormField(comment, { comment = it }, "Комментарий")
    }
}

@Composable
fun PartUsageDialog(workOrders: List<WorkOrder>, parts: List<Part>, value: PartUsage? = null, close: () -> Unit, save: (Long, Long, Double, Double) -> Unit) {
    var workOrder by remember { mutableStateOf(value?.workOrderId ?: workOrders.firstOrNull()?.id) }; var part by remember { mutableStateOf(value?.partId ?: parts.firstOrNull()?.id) }
    var amount by remember { mutableStateOf(value?.amount?.toString().orEmpty()) }; var discount by remember { mutableStateOf(value?.discountAmount?.toString() ?: "0") }
    FormDialog(if (value == null) "Новый расход запчасти" else "Редактирование расхода", close, { if (workOrder != null && part != null) save(workOrder!!, part!!, amount.toDoubleOrNull() ?: 0.0, discount.toDoubleOrNull() ?: 0.0) }) {
        Selector("Заказ-наряд", workOrders, workOrder, { it.id }, { "${it.licensePlate} — ${it.serviceName}" }) { workOrder = it }; Selector("Запчасть", parts, part, { it.id }, { "${it.sku} — ${it.name}" }) { part = it }
        FormField(amount, { amount = it }, "Сумма"); FormField(discount, { discount = it }, "Скидка")
    }
}

@Composable
fun PaymentDialog(workOrders: List<WorkOrder>, value: Payment? = null, close: () -> Unit, save: (Long, Double, Double) -> Unit) {
    var workOrder by remember { mutableStateOf(value?.workOrderId ?: workOrders.firstOrNull()?.id) }
    var amount by remember { mutableStateOf(value?.amount?.toString().orEmpty()) }; var discount by remember { mutableStateOf(value?.discountAmount?.toString() ?: "0") }
    FormDialog(if (value == null) "Новый платеж" else "Редактирование платежа", close, { if (workOrder != null) save(workOrder!!, amount.toDoubleOrNull() ?: 0.0, discount.toDoubleOrNull() ?: 0.0) }) {
        Selector("Заказ-наряд", workOrders, workOrder, { it.id }, { "${it.licensePlate} — ${it.serviceName}" }) { workOrder = it }; FormField(amount, { amount = it }, "Сумма"); FormField(discount, { discount = it }, "Скидка")
    }
}

@Composable fun ConfirmDialog(title: String, text: String, close: () -> Unit, confirm: () -> Unit) = AlertDialog(close, title = { Text(title) }, text = { Text(text) }, confirmButton = { Button(confirm) { Text("Подтвердить") } }, dismissButton = { TextButton(close) { Text("Отмена") } })

@Composable private fun FormDialog(title: String, close: () -> Unit, save: () -> Unit, content: @Composable ColumnScope.() -> Unit) = AlertDialog(close, title = { Text(title) }, text = { Column(Modifier.width(480.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }, confirmButton = { Button(save) { Text("Сохранить") } }, dismissButton = { TextButton(close) { Text("Отмена") } })
@Composable private fun FormField(value: String, change: (String) -> Unit, label: String) = OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
@Composable private fun <T> Selector(label: String, values: List<T>, selected: Long?, id: (T) -> Long, text: (T) -> String, change: (Long) -> Unit) { var expanded by remember { mutableStateOf(false) }; Box { OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text("$label: ${values.firstOrNull { id(it) == selected }?.let(text) ?: "не выбран"}") }; DropdownMenu(expanded, { expanded = false }) { values.forEach { value -> DropdownMenuItem({ Text(text(value)) }, { change(id(value)); expanded = false }) } } } }
@Composable private fun StringSelector(label: String, values: List<String>, selected: String, change: (String) -> Unit) { var expanded by remember { mutableStateOf(false) }; Box { OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text("$label: $selected") }; DropdownMenu(expanded, { expanded = false }) { values.forEach { value -> DropdownMenuItem({ Text(value) }, { change(value); expanded = false }) } } } }
private fun workOrderStatusLabel(value: WorkOrderStatus) = when (value) { WorkOrderStatus.NEW -> "Новый"; WorkOrderStatus.IN_PROGRESS -> "В работе"; WorkOrderStatus.COMPLETED -> "Завершен"; WorkOrderStatus.CANCELLED -> "Отменен" }
