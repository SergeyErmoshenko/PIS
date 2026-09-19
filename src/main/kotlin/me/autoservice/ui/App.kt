package me.autoservice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.autoservice.domain.*
import java.text.NumberFormat
import java.util.Locale

private enum class Section(val title: String, val icon: ImageVector) {
    DASHBOARD("Обзор", Icons.Default.Dashboard), EMPLOYEES("Сотрудники", Icons.Default.Badge), CLIENTS("Клиенты", Icons.Default.People),
    VEHICLES("Автомобили", Icons.Default.DirectionsCar), SERVICES("Услуги", Icons.Default.Build), PARTS("Запчасти", Icons.Default.Inventory),
    WORK_ORDERS("Заказ-наряды", Icons.Default.Assignment), PART_USAGE("Расход запчастей", Icons.Default.Inventory2),
    PAYMENTS("Платежи", Icons.Default.Payments), REPORTS("Отчеты", Icons.Default.Assessment),
}

@Composable
fun AutoServiceApp(repository: AutoServiceRepository) {
    var user by remember { mutableStateOf<Employee?>(null) }
    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF14532D))) {
        if (user == null) UserSelectionScreen(repository.employees().filter { it.isActive }) { user = it }
        else MainWorkspace(repository, user!!, { user = null })
    }
}

@Composable
private fun UserSelectionScreen(users: List<Employee>, select: (Employee) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.width(560.dp)) { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.DirectionsCar, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Информационная система предприятия автосервиса", style = MaterialTheme.typography.headlineSmall)
            Text("Выберите пользователя для входа. Пароль не требуется.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            users.forEach { employee -> OutlinedButton({ select(employee) }, Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(employee.fullName); Text(employee.position, style = MaterialTheme.typography.bodySmall) } } }
        } }
    }
}

@Composable
private fun MainWorkspace(repository: AutoServiceRepository, user: Employee, logout: () -> Unit) {
    var section by remember { mutableStateOf(Section.DASHBOARD) }; var revision by remember { mutableIntStateOf(0) }; var message by remember { mutableStateOf<String?>(null) }
    Scaffold { padding -> Row(Modifier.fillMaxSize().padding(padding)) {
        NavigationRail {
            Spacer(Modifier.height(12.dp)); Section.entries.filter { it != Section.EMPLOYEES || user.role == UserRole.ADMINISTRATOR }.forEach { item -> NavigationRailItem(section == item, { section = item }, { Icon(item.icon, null) }, label = { Text(item.title) }) }
            Spacer(Modifier.weight(1f)); IconButton(logout) { Icon(Icons.Default.Logout, "Сменить пользователя") }; Spacer(Modifier.height(12.dp))
        }
        VerticalDivider(); Column(Modifier.fillMaxSize()) {
            Surface(tonalElevation = 2.dp) { Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) { Text("${user.fullName} · ${user.position}") } }
            Box(Modifier.fillMaxSize().padding(24.dp)) { key(revision) { when (section) {
                Section.DASHBOARD -> DashboardScreen(repository); Section.EMPLOYEES -> EmployeesScreen(repository, { revision++ }, { message = it })
                Section.CLIENTS -> ClientsScreen(repository, user, { revision++ }, { message = it }); Section.VEHICLES -> VehiclesScreen(repository, user, { revision++ }, { message = it })
                Section.SERVICES -> ServicesScreen(repository, user, { revision++ }, { message = it }); Section.PARTS -> PartsScreen(repository, user, { revision++ }, { message = it })
                Section.WORK_ORDERS -> WorkOrdersScreen(repository, user, { revision++ }, { message = it }); Section.PART_USAGE -> PartUsageScreen(repository, user, { revision++ }, { message = it })
                Section.PAYMENTS -> PaymentsScreen(repository, user, { revision++ }, { message = it }); Section.REPORTS -> ReportsScreen(repository)
            } }; message?.let { Snackbar(Modifier.align(Alignment.BottomCenter), action = { TextButton({ message = null }) { Text("Закрыть") } }) { Text(it) } } }
        }
    } }
}

@Composable private fun DashboardScreen(repository: AutoServiceRepository) { val d = repository.dashboard(); val summaries = repository.clientSpendingSummaries(); Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { Title("Обзор автосервиса", "Основные показатели системы"); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricCard("Клиенты", d.clientCount.toString(), Icons.Default.People); MetricCard("Автомобили", d.vehicleCount.toString(), Icons.Default.DirectionsCar); MetricCard("Услуги", d.serviceCount.toString(), Icons.Default.Build); MetricCard("Заказ-наряды", d.workOrderCount.toString(), Icons.Default.Assignment); MetricCard("Выручка", money(summaries.sumOf { it.totalPaid }), Icons.Default.Payments) }; Text("Крупнейшие клиенты", style = MaterialTheme.typography.titleLarge); DataTable(listOf("Клиент", "Автомобилей", "Оплачено"), summaries.take(8).map { listOf(it.clientName, it.vehicleCount.toString(), money(it.totalPaid)) }) } }
@Composable private fun MetricCard(label: String, value: String, icon: ImageVector) { Card(Modifier.widthIn(min = 145.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.titleLarge); Text(label) } } }

@Composable
private fun EmployeesScreen(repository: AutoServiceRepository, refresh: () -> Unit, message: (String) -> Unit) {
    var editor by remember { mutableStateOf<Employee?>(null) }; var adding by remember { mutableStateOf(false) }; var archive by remember { mutableStateOf<Employee?>(null) }; val rows = repository.employees()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Сотрудники", "Мастера-приемщики, механики и администраторы", "Добавить сотрудника") { adding = true }; EntityList(listOf("ФИО", "Должность", "Email", "Телефон", "Статус"), rows, { listOf(it.fullName, it.position, it.email, it.phone, if (it.isActive) "Активен" else "Архив") }, { !it.isActive }) { item -> Actions({ editor = item }, { archive = item }, archiveLabel = "В архив", restore = if (!item.isActive) { { perform(message) { repository.restoreEmployee(item.id); refresh() } } } else null) } }
    if (adding || editor != null) EmployeeDialog(editor, { adding = false; editor = null }) { input -> perform(message) { if (editor == null) repository.addEmployee(input) else repository.updateEmployee(editor!!.id, input); adding = false; editor = null; refresh() } }
    archive?.let { item -> ConfirmDialog("Архивировать сотрудника?", "История заказ-нарядов сохранится.", { archive = null }) { perform(message) { repository.archiveEmployee(item.id); archive = null; refresh() } } }
}

@Composable
private fun ClientsScreen(repository: AutoServiceRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.MECHANIC
    var search by remember { mutableStateOf("") }; var editor by remember { mutableStateOf<Client?>(null) }; var adding by remember { mutableStateOf(false) }; var archive by remember { mutableStateOf<Client?>(null) }; val rows = repository.clients(search); val employees = repository.employees().filter { it.isActive }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Клиенты", "Создание, изменение и архивирование", if (readOnly) null else "Добавить клиента") { adding = true }; OutlinedTextField(search, { search = it }, label = { Text("Поиск") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth()); EntityList(listOf("ФИО", "Мастер-приемщик", "Email", "Телефон", "Автомобилей", "Статус"), rows, { listOf(it.fullName, it.advisorName, it.email, it.phone, it.vehicleCount.toString(), status(it.status.name)) }, { it.status == ClientStatus.ARCHIVED }) { item -> if (!readOnly) Actions({ editor = item }, { archive = item }, archiveLabel = "В архив", restore = if (item.status == ClientStatus.ARCHIVED) { { perform(message) { repository.updateClientStatus(item.id, ClientStatus.ACTIVE); refresh() } } } else null) } }
    if (adding || editor != null) ClientDialog(employees, editor, { adding = false; editor = null }) { input -> perform(message) { if (editor == null) repository.addClient(input) else repository.updateClient(editor!!.id, ClientUpdate(input.advisorId, input.lastName, input.firstName, input.middleName, input.phone, input.email, editor!!.status)); adding = false; editor = null; refresh() } }
    archive?.let { item -> ConfirmDialog("Архивировать клиента?", "Автомобили и история заказ-нарядов сохранятся.", { archive = null }) { perform(message) { repository.updateClientStatus(item.id, ClientStatus.ARCHIVED); archive = null; refresh() } } }
}

@Composable
private fun VehiclesScreen(repository: AutoServiceRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.MECHANIC
    var editor by remember { mutableStateOf<Vehicle?>(null) }; var adding by remember { mutableStateOf(false) }; var retire by remember { mutableStateOf<Vehicle?>(null) }; val rows = repository.vehicles(); val clients = repository.clients().filter { it.status == ClientStatus.ACTIVE }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Автомобили клиентов", "Редактирование и снятие с обслуживания", if (readOnly) null else "Добавить автомобиль") { adding = true }; EntityList(listOf("Госномер", "Марка", "Модель", "Клиент", "Год", "Статус"), rows, { listOf(it.licensePlate, it.make, it.model, it.clientName, it.year.toString(), status(it.status.name)) }, { it.status != VehicleStatus.ACTIVE }) { item -> if (!readOnly) Actions({ editor = item }, { retire = item }, archiveLabel = "Продан", restore = if (item.status != VehicleStatus.ACTIVE) { { perform(message) { repository.updateVehicleStatus(item.id, VehicleStatus.ACTIVE); refresh() } } } else null) } }
    if (adding || editor != null) VehicleDialog(clients, editor, { adding = false; editor = null }) { clientId, make, model, year, plate, vin -> perform(message) { if (editor == null) repository.addVehicle(clientId, make, model, year, plate, vin) else repository.updateVehicle(editor!!.id, clientId, make, model, year, plate, vin); adding = false; editor = null; refresh() } }
    retire?.let { item -> ConfirmDialog("Снять автомобиль с обслуживания?", "История заказ-нарядов останется.", { retire = null }) { perform(message) { repository.updateVehicleStatus(item.id, VehicleStatus.SOLD); retire = null; refresh() } } }
}

@Composable
private fun ServicesScreen(repository: AutoServiceRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.MECHANIC
    var editor by remember { mutableStateOf<Service?>(null) }; var adding by remember { mutableStateOf(false) }; var archive by remember { mutableStateOf<Service?>(null) }; var priceFor by remember { mutableStateOf<Service?>(null) }; var deletePrice by remember { mutableStateOf<Service?>(null) }; val rows = repository.services(); val categories = repository.serviceCategories()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Услуги", "Справочник, цены и архивирование", if (readOnly) null else "Добавить услугу") { adding = true }; EntityList(listOf("Код", "Название", "Категория", "Цена", "Статус"), rows, { listOf(it.code, it.name, it.categoryName, it.latestPrice?.let(::money) ?: "Нет цены", if (it.isActive) "Активна" else "Архив") }, { !it.isActive }) { item -> if (!readOnly) Row { IconButton({ editor = item }) { Icon(Icons.Default.Edit, "Изменить") }; IconButton({ priceFor = item }) { Icon(Icons.Default.AddChart, "Новая цена") }; IconButton({ deletePrice = item }) { Icon(Icons.Default.Undo, "Удалить последнюю цену") }; if (item.isActive) IconButton({ archive = item }) { Icon(Icons.Default.Archive, "В архив") } else IconButton({ perform(message) { repository.restoreService(item.id); refresh() } }) { Icon(Icons.Default.Unarchive, "Восстановить") } } } }
    if (adding || editor != null) ServiceDialog(categories, editor, { adding = false; editor = null }) { categoryId, code, name, price -> perform(message) { if (editor == null) repository.addService(categoryId, code, name, price) else repository.updateService(editor!!.id, ServiceInput(categoryId, code, name)); adding = false; editor = null; refresh() } }
    priceFor?.let { item -> ServicePriceDialog(item, { priceFor = null }) { price -> perform(message) { repository.addServicePrice(item.id, price); priceFor = null; refresh() } } }
    archive?.let { item -> ConfirmDialog("Архивировать услугу?", "Цены и заказ-наряды сохранятся.", { archive = null }) { perform(message) { repository.archiveService(item.id); archive = null; refresh() } } }
    deletePrice?.let { item -> ConfirmDialog("Удалить последнюю цену?", "Будет использована предыдущая цена услуги.", { deletePrice = null }) { perform(message) { repository.deleteLatestServicePrice(item.id); deletePrice = null; refresh() } } }
}

@Composable
private fun PartsScreen(repository: AutoServiceRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.MECHANIC
    var editor by remember { mutableStateOf<Part?>(null) }; var adding by remember { mutableStateOf(false) }; var archive by remember { mutableStateOf<Part?>(null) }; val rows = repository.parts()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Запчасти", "Складской справочник запчастей", if (readOnly) null else "Добавить запчасть") { adding = true }; EntityList(listOf("Артикул", "Название", "Цена", "Остаток", "Статус"), rows, { listOf(it.sku, it.name, money(it.unitPrice), it.quantityInStock.toString(), if (it.isActive) "Активна" else "Архив") }, { !it.isActive }) { item -> if (!readOnly) Actions({ editor = item }, { archive = item }, archiveLabel = "В архив", restore = if (!item.isActive) { { perform(message) { repository.restorePart(item.id); refresh() } } } else null) } }
    if (adding || editor != null) PartDialog(editor, { adding = false; editor = null }) { sku, name, unitPrice, quantity -> perform(message) { if (editor == null) repository.addPart(sku, name, unitPrice, quantity) else repository.updatePart(editor!!.id, sku, name, unitPrice, quantity); adding = false; editor = null; refresh() } }
    archive?.let { item -> ConfirmDialog("Архивировать запчасть?", "История расхода сохранится.", { archive = null }) { perform(message) { repository.archivePart(item.id); archive = null; refresh() } } }
}

@Composable
private fun WorkOrdersScreen(repository: AutoServiceRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.MECHANIC
    var adding by remember { mutableStateOf(false) }; var deletion by remember { mutableStateOf<WorkOrder?>(null) }; var statusFilter by remember { mutableStateOf<WorkOrderStatus?>(null) }; var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }; var min by remember { mutableStateOf("") }; var max by remember { mutableStateOf("") }
    val rows = repository.workOrders(WorkOrderFilter(status = statusFilter, dateFrom = from.ifBlank { null }, dateTo = to.ifBlank { null }, amountMin = min.toDoubleOrNull(), amountMax = max.toDoubleOrNull()))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Header("Заказ-наряды", "Выполненные заказ-наряды не редактируются, только удаление и динамические фильтры", if (readOnly) null else "Новый заказ-наряд") { adding = true }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Статус", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(statusFilter == null, { statusFilter = null }, { Text("Все") }); WorkOrderStatus.entries.forEach { value -> FilterChip(statusFilter == value, { statusFilter = value }, { Text(status(value.name)) }) } } }; SmallField(from, { from = it }, "Дата от", "ГГГГ-ММ-ДД"); SmallField(to, { to = it }, "Дата до", "ГГГГ-ММ-ДД"); SmallField(min, { min = it }, "Сумма от"); SmallField(max, { max = it }, "Сумма до") }
        EntityList(listOf("Дата", "Статус", "Клиент", "Автомобиль", "Услуга", "Механик", "Сумма"), rows, { listOf(it.openDate, status(it.status.name), it.clientName, it.licensePlate, it.serviceName, it.mechanicName, money(it.amount)) }) { item -> if (!readOnly) IconButton({ deletion = item }) { Icon(Icons.Default.Delete, "Удалить") } } }
    val vehicles = repository.vehicles().filter { it.status == VehicleStatus.ACTIVE }; val services = repository.services().filter { it.isActive }; val mechanics = repository.employees().filter { it.isActive && it.role == UserRole.MECHANIC }
    if (adding) WorkOrderDialog(vehicles, services, mechanics, { adding = false }) { workOrder -> perform(message) { repository.addWorkOrder(workOrder); adding = false; refresh() } }
    deletion?.let { item -> ConfirmDialog("Удалить заказ-наряд?", "Расход запчастей и платежи по нему нужно будет удалить отдельно.", { deletion = null }) { perform(message) { repository.deleteWorkOrder(item.id); deletion = null; refresh() } } }
}

@Composable
private fun PartUsageScreen(repository: AutoServiceRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.MECHANIC
    var editor by remember { mutableStateOf<PartUsage?>(null) }; var adding by remember { mutableStateOf(false) }; var deletion by remember { mutableStateOf<PartUsage?>(null) }; val rows = repository.partUsages()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Расход запчастей", "Создание, изменение и удаление списаний", if (readOnly) null else "Добавить расход") { adding = true }; EntityList(listOf("Дата", "Автомобиль", "Запчасть", "Сумма", "Скидка", "К оплате"), rows, { listOf(it.usageDate, it.licensePlate, it.partName, money(it.amount), money(it.discountAmount), money(it.netAmount)) }) { item -> if (!readOnly) Actions({ editor = item }, { deletion = item }) } }
    if (adding || editor != null) PartUsageDialog(repository.workOrders(), repository.parts(), editor, { adding = false; editor = null }) { workOrder, part, amount, discount -> perform(message) { if (editor == null) repository.addPartUsage(workOrder, part, amount, discount) else repository.updatePartUsage(editor!!.id, workOrder, part, amount, discount); adding = false; editor = null; refresh() } }
    deletion?.let { item -> ConfirmDialog("Удалить расход запчасти?", "Операцию нельзя отменить.", { deletion = null }) { perform(message) { repository.deletePartUsage(item.id); deletion = null; refresh() } } }
}

@Composable
private fun PaymentsScreen(repository: AutoServiceRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.MECHANIC
    var editor by remember { mutableStateOf<Payment?>(null) }; var adding by remember { mutableStateOf(false) }; var deletion by remember { mutableStateOf<Payment?>(null) }; val rows = repository.payments()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Платежи", "Создание, изменение и удаление платежей по заказ-нарядам", if (readOnly) null else "Добавить платеж") { adding = true }; EntityList(listOf("Дата", "Автомобиль", "Клиент", "Сумма", "Скидка", "К зачислению"), rows, { listOf(it.paymentDate, it.licensePlate, it.clientName, money(it.amount), money(it.discountAmount), money(it.netAmount)) }) { item -> if (!readOnly) Actions({ editor = item }, { deletion = item }) } }
    if (adding || editor != null) PaymentDialog(repository.workOrders(), editor, { adding = false; editor = null }) { workOrder, amount, discount -> perform(message) { if (editor == null) repository.addPayment(workOrder, amount, discount) else repository.updatePayment(editor!!.id, workOrder, amount, discount); adding = false; editor = null; refresh() } }
    deletion?.let { item -> ConfirmDialog("Удалить платеж?", "Операцию нельзя отменить.", { deletion = null }) { perform(message) { repository.deletePayment(item.id); deletion = null; refresh() } } }
}

@Composable private fun ReportsScreen(repository: AutoServiceRepository) { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { Title("Отчеты", "Группировка и аналитические запросы"); Text("Оборот по месяцам", style = MaterialTheme.typography.titleLarge); DataTable(listOf("Месяц", "Услуги", "Запчасти"), repository.monthlyRevenue().map { listOf(it.month, money(it.services), money(it.parts)) }); Text("Выручка по категориям услуг", style = MaterialTheme.typography.titleLarge); DataTable(listOf("Категория", "Выручка"), repository.categoryRevenue().map { listOf(it.categoryName, money(it.amount)) }) } }

@Composable private fun Title(title: String, subtitle: String) = Column { Text(title, style = MaterialTheme.typography.headlineMedium); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun Header(title: String, subtitle: String, button: String?, action: () -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Title(title, subtitle); if (button != null) Button(action) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(button) } }
@Composable private fun SmallField(value: String, change: (String) -> Unit, label: String, placeholder: String? = null) = OutlinedTextField(value, change, label = { Text(label) }, placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Clip, style = MaterialTheme.typography.bodySmall) } }, modifier = Modifier.width(if (placeholder != null) 150.dp else 125.dp), singleLine = true)
@Composable private fun Actions(edit: () -> Unit, remove: () -> Unit, archiveLabel: String = "Удалить", restore: (() -> Unit)? = null) = Row {
    IconButton(edit) { Icon(Icons.Default.Edit, "Изменить") }
    if (restore != null) IconButton(restore) { Icon(Icons.Default.Unarchive, "Восстановить") }
    else IconButton(remove) { Icon(if (archiveLabel == "Удалить") Icons.Default.Delete else Icons.Default.Archive, archiveLabel) }
}
@Composable private fun <T> EntityList(headers: List<String>, rows: List<T>, values: (T) -> List<String>, muted: (T) -> Boolean = { false }, action: @Composable (T) -> Unit) = LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) { item { TableHeader(headers) }; items(rows) { item -> TableRow(values(item), muted(item)) { action(item) } } }
@Composable private fun DataTable(headers: List<String>, rows: List<List<String>>) = LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) { item { TableHeader(headers) }; items(rows) { TableRow(it) } }
private val ActionsColumnWidth = 200.dp
@Composable private fun TableHeader(values: List<String>) = Surface(color = MaterialTheme.colorScheme.surfaceVariant) { Row(Modifier.fillMaxWidth().padding(9.dp)) { values.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge) }; Spacer(Modifier.width(ActionsColumnWidth)) } }
@Composable private fun TableRow(values: List<String>, muted: Boolean = false, action: (@Composable () -> Unit)? = null) = Surface(tonalElevation = 1.dp, color = if (muted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface) { Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { values.forEachIndexed { index, value -> Text(value, Modifier.weight(1f), maxLines = 2, color = if (muted && index == values.lastIndex) MaterialTheme.colorScheme.error else if (muted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified) }; Box(Modifier.width(ActionsColumnWidth)) { action?.invoke() } } }
private fun money(value: Double) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU")).format(value)
private fun number(value: Double) = "%.2f".format(Locale.US, value).trimEnd('0').trimEnd('.')
private fun status(value: String) = when (value) { "ACTIVE" -> "Активен"; "BLOCKED" -> "Заблокирован"; "ARCHIVED" -> "Архив"; "SOLD" -> "Продан"; "NEW" -> "Новый"; "IN_PROGRESS" -> "В работе"; "COMPLETED" -> "Завершен"; "CANCELLED" -> "Отменен"; else -> value }
private inline fun perform(message: (String) -> Unit, action: () -> Unit) = try { action() } catch (error: Exception) { message(error.message ?: "Не удалось выполнить операцию") }
