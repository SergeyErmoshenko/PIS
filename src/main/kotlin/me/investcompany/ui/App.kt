package me.investcompany.ui

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
import me.investcompany.domain.*
import java.text.NumberFormat
import java.util.Locale

private enum class Section(val title: String, val icon: ImageVector) {
    DASHBOARD("Обзор", Icons.Default.Dashboard), EMPLOYEES("Сотрудники", Icons.Default.Badge), CLIENTS("Клиенты", Icons.Default.People),
    ACCOUNTS("Счета", Icons.Default.AccountBalanceWallet), INSTRUMENTS("Инструменты", Icons.Default.ShowChart), TRADES("Сделки", Icons.Default.SwapHoriz),
    PORTFOLIOS("Портфели", Icons.Default.PieChart), DIVIDENDS("Дивиденды", Icons.Default.Payments), COUPONS("Купоны", Icons.Default.RequestQuote), REPORTS("Отчеты", Icons.Default.Assessment),
}

@Composable
fun InvestmentApp(repository: InvestmentRepository) {
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
            Icon(Icons.Default.AccountBalance, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Информационная система инвестиционной компании", style = MaterialTheme.typography.headlineSmall)
            Text("Выберите пользователя для входа. Пароль не требуется.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            users.forEach { employee -> OutlinedButton({ select(employee) }, Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(employee.fullName); Text(employee.position, style = MaterialTheme.typography.bodySmall) } } }
        } }
    }
}

@Composable
private fun MainWorkspace(repository: InvestmentRepository, user: Employee, logout: () -> Unit) {
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
                Section.CLIENTS -> ClientsScreen(repository, user, { revision++ }, { message = it }); Section.ACCOUNTS -> AccountsScreen(repository, user, { revision++ }, { message = it })
                Section.INSTRUMENTS -> InstrumentsScreen(repository, user, { revision++ }, { message = it }); Section.TRADES -> TradesScreen(repository, user, { revision++ }, { message = it })
                Section.PORTFOLIOS -> PortfoliosScreen(repository, user, { revision++ }, { message = it }); Section.DIVIDENDS -> DividendsScreen(repository, user, { revision++ }, { message = it })
                Section.COUPONS -> CouponsScreen(repository, user, { revision++ }, { message = it }); Section.REPORTS -> ReportsScreen(repository)
            } }; message?.let { Snackbar(Modifier.align(Alignment.BottomCenter), action = { TextButton({ message = null }) { Text("Закрыть") } }) { Text(it) } } }
        }
    } }
}

@Composable private fun DashboardScreen(repository: InvestmentRepository) { val d = repository.dashboard(); val summaries = repository.portfolioSummaries(); Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { Title("Обзор инвестиционной компании", "Основные показатели системы"); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricCard("Клиенты", d.clientCount.toString(), Icons.Default.People); MetricCard("Счета", d.accountCount.toString(), Icons.Default.AccountBalanceWallet); MetricCard("Инструменты", d.instrumentCount.toString(), Icons.Default.ShowChart); MetricCard("Сделки", d.tradeCount.toString(), Icons.Default.SwapHoriz); MetricCard("Активы", money(summaries.sumOf { it.currentValue }), Icons.Default.Payments) }; Text("Крупнейшие портфели", style = MaterialTheme.typography.titleLarge); DataTable(listOf("Клиент", "Инструментов", "Стоимость"), summaries.take(8).map { listOf(it.clientName, it.instrumentCount.toString(), money(it.currentValue)) }) } }
@Composable private fun MetricCard(label: String, value: String, icon: ImageVector) { Card(Modifier.widthIn(min = 145.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.titleLarge); Text(label) } } }

@Composable
private fun EmployeesScreen(repository: InvestmentRepository, refresh: () -> Unit, message: (String) -> Unit) {
    var editor by remember { mutableStateOf<Employee?>(null) }; var adding by remember { mutableStateOf(false) }; var archive by remember { mutableStateOf<Employee?>(null) }; val rows = repository.employees()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Сотрудники", "Пользователи системы и клиентские менеджеры", "Добавить сотрудника") { adding = true }; EntityList(listOf("ФИО", "Должность", "Email", "Телефон", "Статус"), rows, { listOf(it.fullName, it.position, it.email, it.phone, if (it.isActive) "Активен" else "Архив") }, { !it.isActive }) { item -> Actions({ editor = item }, { archive = item }, archiveLabel = "В архив", restore = if (!item.isActive) { { perform(message) { repository.restoreEmployee(item.id); refresh() } } } else null) } }
    if (adding || editor != null) EmployeeDialog(editor, { adding = false; editor = null }) { input -> perform(message) { if (editor == null) repository.addEmployee(input) else repository.updateEmployee(editor!!.id, input); adding = false; editor = null; refresh() } }
    archive?.let { item -> ConfirmDialog("Архивировать сотрудника?", "История сделок и клиенты сохранятся.", { archive = null }) { perform(message) { repository.archiveEmployee(item.id); archive = null; refresh() } } }
}

@Composable
private fun ClientsScreen(repository: InvestmentRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.ANALYST
    var search by remember { mutableStateOf("") }; var editor by remember { mutableStateOf<Client?>(null) }; var adding by remember { mutableStateOf(false) }; var archive by remember { mutableStateOf<Client?>(null) }; val rows = repository.clients(search); val employees = repository.employees().filter { it.isActive }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Клиенты", "Создание, изменение и архивирование", if (readOnly) null else "Добавить клиента") { adding = true }; OutlinedTextField(search, { search = it }, label = { Text("Поиск") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth()); EntityList(listOf("ФИО", "Менеджер", "Email", "Телефон", "Счетов", "Статус"), rows, { listOf(it.fullName, it.managerName, it.email, it.phone, it.accountCount.toString(), status(it.status.name)) }, { it.status == ClientStatus.ARCHIVED }) { item -> if (!readOnly) Actions({ editor = item }, { archive = item }, archiveLabel = "В архив", restore = if (item.status == ClientStatus.ARCHIVED) { { perform(message) { repository.updateClientStatus(item.id, ClientStatus.ACTIVE); refresh() } } } else null) } }
    if (adding || editor != null) ClientDialog(employees, editor, { adding = false; editor = null }) { input -> perform(message) { if (editor == null) repository.addClient(input) else repository.updateClient(editor!!.id, ClientUpdate(input.managerId, input.lastName, input.firstName, input.middleName, input.birthDate, input.phone, input.email, input.passportNumber, editor!!.status)); adding = false; editor = null; refresh() } }
    archive?.let { item -> ConfirmDialog("Архивировать клиента?", "Счета и финансовая история сохранятся.", { archive = null }) { perform(message) { repository.updateClientStatus(item.id, ClientStatus.ARCHIVED); archive = null; refresh() } } }
}

@Composable
private fun AccountsScreen(repository: InvestmentRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.ANALYST
    var editor by remember { mutableStateOf<Account?>(null) }; var adding by remember { mutableStateOf(false) }; var close by remember { mutableStateOf<Account?>(null) }; val rows = repository.accounts(); val clients = repository.clients().filter { it.status == ClientStatus.ACTIVE }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Инвестиционные счета", "Редактирование и безопасное закрытие счетов", if (readOnly) null else "Открыть счет") { adding = true }; EntityList(listOf("Номер", "Клиент", "Открыт", "Валюта", "Статус"), rows, { listOf(it.accountNumber, it.clientName, it.openedAt, it.currency, status(it.status.name)) }, { it.status == AccountStatus.CLOSED }) { item -> if (!readOnly) Actions({ editor = item }, { close = item }, archiveLabel = "Закрыть", restore = if (item.status == AccountStatus.CLOSED) { { perform(message) { repository.updateAccountStatus(item.id, AccountStatus.OPEN); refresh() } } } else null) } }
    if (adding || editor != null) AccountDialog(clients, editor, { adding = false; editor = null }) { client, number -> perform(message) { if (editor == null) repository.addAccount(client, number) else repository.updateAccount(editor!!.id, client, number); adding = false; editor = null; refresh() } }
    close?.let { item -> ConfirmDialog("Закрыть счет?", "Сделки и портфель останутся в истории.", { close = null }) { perform(message) { repository.updateAccountStatus(item.id, AccountStatus.CLOSED); close = null; refresh() } } }
}

@Composable
private fun InstrumentsScreen(repository: InvestmentRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.ANALYST
    var editor by remember { mutableStateOf<Instrument?>(null) }; var adding by remember { mutableStateOf(false) }; var archive by remember { mutableStateOf<Instrument?>(null) }; var priceFor by remember { mutableStateOf<Instrument?>(null) }; var deletePrice by remember { mutableStateOf<Instrument?>(null) }; val rows = repository.instruments(); val types = repository.instrumentTypes()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Финансовые инструменты", "Справочник, цены и архивирование", if (readOnly) null else "Добавить инструмент") { adding = true }; EntityList(listOf("Тикер", "Название", "Тип", "Эмитент", "Цена", "Статус"), rows, { listOf(it.ticker, it.name, it.typeName, it.issuer, it.latestPrice?.let(::money) ?: "Нет цены", if (it.isActive) "Активен" else "Архив") }, { !it.isActive }) { item -> if (!readOnly) Row { IconButton({ editor = item }) { Icon(Icons.Default.Edit, "Изменить") }; IconButton({ priceFor = item }) { Icon(Icons.Default.AddChart, "Новая цена") }; IconButton({ deletePrice = item }) { Icon(Icons.Default.Undo, "Удалить последнюю цену") }; if (item.isActive) IconButton({ archive = item }) { Icon(Icons.Default.Archive, "В архив") } else IconButton({ perform(message) { repository.restoreInstrument(item.id); refresh() } }) { Icon(Icons.Default.Unarchive, "Восстановить") } } } }
    if (adding || editor != null) InstrumentDialog(types, editor, { adding = false; editor = null }) { typeId, ticker, name, issuer, price -> perform(message) { if (editor == null) repository.addInstrument(typeId, ticker, name, issuer, price) else repository.updateInstrument(editor!!.id, InstrumentInput(typeId, ticker, name, issuer, editor!!.currency)); adding = false; editor = null; refresh() } }
    priceFor?.let { item -> PriceDialog(item, { priceFor = null }) { price -> perform(message) { repository.addPrice(item.id, price); priceFor = null; refresh() } } }
    archive?.let { item -> ConfirmDialog("Архивировать инструмент?", "Цены и сделки сохранятся.", { archive = null }) { perform(message) { repository.archiveInstrument(item.id); archive = null; refresh() } } }
    deletePrice?.let { item -> ConfirmDialog("Удалить последнюю цену?", "Будет использована предыдущая цена инструмента.", { deletePrice = null }) { perform(message) { repository.deleteLatestPrice(item.id); deletePrice = null; refresh() } } }
}

@Composable
private fun TradesScreen(repository: InvestmentRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.ANALYST
    var adding by remember { mutableStateOf(false) }; var deletion by remember { mutableStateOf<Trade?>(null) }; var type by remember { mutableStateOf<TradeType?>(null) }; var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }; var min by remember { mutableStateOf("") }; var max by remember { mutableStateOf("") }; val rows = repository.trades(TradeFilter(type = type, dateFrom = from.ifBlank { null }, dateTo = to.ifBlank { null }, amountMin = min.toDoubleOrNull(), amountMax = max.toDoubleOrNull()))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Header("Сделки", "Совершенные сделки не редактируются, только удаление и динамические фильтры", if (readOnly) null else "Новая сделка") { adding = true }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Тип", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(type == null, { type = null }, { Text("Все") }); FilterChip(type == TradeType.BUY, { type = TradeType.BUY }, { Text("Покупки") }); FilterChip(type == TradeType.SELL, { type = TradeType.SELL }, { Text("Продажи") }) } }; SmallField(from, { from = it }, "Дата от", "ГГГГ-ММ-ДД"); SmallField(to, { to = it }, "Дата до", "ГГГГ-ММ-ДД"); SmallField(min, { min = it }, "Сумма от"); SmallField(max, { max = it }, "Сумма до") }; EntityList(listOf("Дата", "Тип", "Клиент", "Счет", "Тикер", "Количество", "Сумма"), rows, { listOf(it.date, if (it.type == TradeType.BUY) "Покупка" else "Продажа", it.clientName, it.accountNumber, it.ticker, number(it.quantity), money(it.amount)) }) { item -> if (!readOnly) IconButton({ deletion = item }) { Icon(Icons.Default.Delete, "Удалить") } } }
    val accounts = repository.accounts().filter { it.status == AccountStatus.OPEN }; val instruments = repository.instruments().filter { it.isActive }; val employees = repository.employees().filter { it.isActive }
    if (adding) TradeDialog(accounts, instruments, employees, null, user, { adding = false }) { trade -> perform(message) { repository.addTrade(trade); adding = false; refresh() } }
    deletion?.let { item -> ConfirmDialog("Удалить сделку?", "Портфель и отчеты будут пересчитаны.", { deletion = null }) { perform(message) { repository.deleteTrade(item.id); deletion = null; refresh() } } }
}

@Composable
private fun PortfoliosScreen(repository: InvestmentRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.ANALYST
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Портфели", "Расчетное представление на основе сделок", if (readOnly) null else "Добавить позицию") { adding = true }; DataTable(listOf("Клиент", "Счет", "Тикер", "Количество", "Средняя цена", "Текущая цена", "Стоимость", "Результат"), repository.positions().map { listOf(it.clientName, it.accountNumber, it.ticker, number(it.quantity), money(it.averageBuyPrice), it.latestPrice?.let(::money) ?: "—", money(it.currentValue), money(it.profit)) }) }
    if (adding) { val accounts = repository.accounts().filter { it.status == AccountStatus.OPEN }; val instruments = repository.instruments().filter { it.isActive }; val employees = repository.employees().filter { it.isActive }
        TradeDialog(accounts, instruments, employees, null, user, { adding = false }) { trade -> perform(message) { repository.addTrade(trade); adding = false; refresh() } } }
}

@Composable
private fun DividendsScreen(repository: InvestmentRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.ANALYST
    var editor by remember { mutableStateOf<Dividend?>(null) }; var adding by remember { mutableStateOf(false) }; var deletion by remember { mutableStateOf<Dividend?>(null) }; val rows = repository.dividends()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Дивиденды", "Создание, изменение и удаление начислений", if (readOnly) null else "Добавить начисление") { adding = true }; EntityList(listOf("Дата", "Клиент", "Счет", "Тикер", "Сумма", "Налог", "К выплате"), rows, { listOf(it.paymentDate, it.clientName, it.accountNumber, it.ticker, money(it.amount), money(it.taxAmount), money(it.netAmount)) }) { item -> if (!readOnly) Actions({ editor = item }, { deletion = item }) } }
    if (adding || editor != null) DividendDialog(repository.accounts(), repository.instruments(), editor, { adding = false; editor = null }) { account, instrument, amount, tax -> perform(message) { if (editor == null) repository.addDividend(account, instrument, amount, tax) else repository.updateDividend(editor!!.id, account, instrument, amount, tax); adding = false; editor = null; refresh() } }
    deletion?.let { item -> ConfirmDialog("Удалить начисление?", "Операцию нельзя отменить.", { deletion = null }) { perform(message) { repository.deleteDividend(item.id); deletion = null; refresh() } } }
}

@Composable
private fun CouponsScreen(repository: InvestmentRepository, user: Employee, refresh: () -> Unit, message: (String) -> Unit) {
    val readOnly = user.role == UserRole.ANALYST
    var editor by remember { mutableStateOf<Coupon?>(null) }; var adding by remember { mutableStateOf(false) }; var deletion by remember { mutableStateOf<Coupon?>(null) }; val rows = repository.coupons()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Header("Купоны", "Создание, изменение и удаление выплат по облигациям", if (readOnly) null else "Добавить выплату") { adding = true }; EntityList(listOf("Дата", "Клиент", "Счет", "Тикер", "Сумма", "Налог", "К выплате"), rows, { listOf(it.paymentDate, it.clientName, it.accountNumber, it.ticker, money(it.amount), money(it.taxAmount), money(it.netAmount)) }) { item -> if (!readOnly) Actions({ editor = item }, { deletion = item }) } }
    if (adding || editor != null) CouponDialog(repository.accounts(), repository.instruments(), editor, { adding = false; editor = null }) { account, instrument, amount, tax -> perform(message) { if (editor == null) repository.addCoupon(account, instrument, amount, tax) else repository.updateCoupon(editor!!.id, account, instrument, amount, tax); adding = false; editor = null; refresh() } }
    deletion?.let { item -> ConfirmDialog("Удалить выплату?", "Операцию нельзя отменить.", { deletion = null }) { perform(message) { repository.deleteCoupon(item.id); deletion = null; refresh() } } }
}

@Composable private fun ReportsScreen(repository: InvestmentRepository) { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { Title("Отчеты", "Группировка и аналитические запросы"); Text("Оборот по месяцам", style = MaterialTheme.typography.titleLarge); DataTable(listOf("Месяц", "Покупки", "Продажи"), repository.monthlyTurnover().map { listOf(it.month, money(it.buy), money(it.sell)) }); Text("Структура активов", style = MaterialTheme.typography.titleLarge); DataTable(listOf("Тип инструмента", "Стоимость"), repository.assetAllocation().map { listOf(it.typeName, money(it.currentValue)) }) } }

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
private fun status(value: String) = when (value) { "ACTIVE", "OPEN" -> "Активен"; "BLOCKED" -> "Заблокирован"; "ARCHIVED" -> "Архив"; "CLOSED" -> "Закрыт"; else -> value }
private inline fun perform(message: (String) -> Unit, action: () -> Unit) = try { action() } catch (error: Exception) { message(error.message ?: "Не удалось выполнить операцию") }
