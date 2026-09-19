package me.investcompany.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.investcompany.domain.*
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
    var manager by remember { mutableStateOf(value?.managerId ?: employees.firstOrNull()?.id) }
    var lastName by remember { mutableStateOf(value?.lastName.orEmpty()) }; var firstName by remember { mutableStateOf(value?.firstName.orEmpty()) }; var middleName by remember { mutableStateOf(value?.middleName.orEmpty()) }
    var birthDate by remember { mutableStateOf(value?.birthDate ?: "1990-01-01") }; var phone by remember { mutableStateOf(value?.phone.orEmpty()) }; var email by remember { mutableStateOf(value?.email.orEmpty()) }; var passport by remember { mutableStateOf(value?.passportNumber.orEmpty()) }
    FormDialog(if (value == null) "Новый клиент" else "Редактирование клиента", close, { manager?.let { save(NewClient(it, lastName, firstName, middleName.ifBlank { null }, birthDate, phone, email, passport)) } }) {
        Selector("Менеджер", employees, manager, { it.id }, { it.fullName }) { manager = it }
        FormField(lastName, { lastName = it }, "Фамилия"); FormField(firstName, { firstName = it }, "Имя"); FormField(middleName, { middleName = it }, "Отчество")
        FormField(birthDate, { birthDate = it }, "Дата рождения, ГГГГ-ММ-ДД"); FormField(phone, { phone = it }, "Телефон"); FormField(email, { email = it }, "Email"); FormField(passport, { passport = it }, "Номер паспорта")
    }
}

@Composable
fun AccountDialog(clients: List<Client>, value: Account? = null, close: () -> Unit, save: (Long, String) -> Unit) {
    var client by remember { mutableStateOf(value?.clientId ?: clients.firstOrNull()?.id) }; var number by remember { mutableStateOf(value?.accountNumber.orEmpty()) }
    FormDialog(if (value == null) "Открытие счета" else "Редактирование счета", close, { client?.let { save(it, number) } }) {
        Selector("Клиент", clients, client, { it.id }, { it.fullName }) { client = it }; FormField(number, { number = it }, "Номер счета")
    }
}

@Composable
fun InstrumentDialog(types: List<InstrumentType>, value: Instrument? = null, close: () -> Unit, save: (Long, String, String, String, Double) -> Unit) {
    var type by remember { mutableStateOf(types.firstOrNull { it.id == value?.typeId }?.id ?: types.firstOrNull()?.id) }
    var ticker by remember { mutableStateOf(value?.ticker.orEmpty()) }; var name by remember { mutableStateOf(value?.name.orEmpty()) }; var issuer by remember { mutableStateOf(value?.issuer.orEmpty()) }; var price by remember { mutableStateOf(value?.latestPrice?.toString().orEmpty()) }
    FormDialog(if (value == null) "Новый инструмент" else "Редактирование инструмента", close, { type?.let { save(it, ticker, name, issuer, price.toDoubleOrNull() ?: 0.0) } }) {
        Selector("Тип инструмента", types, type, { it.id }, { it.name }) { type = it }
        FormField(ticker, { ticker = it }, "Тикер"); FormField(name, { name = it }, "Название"); FormField(issuer, { issuer = it }, "Эмитент"); if (value == null) FormField(price, { price = it }, "Текущая цена")
    }
}

@Composable fun PriceDialog(instrument: Instrument, close: () -> Unit, save: (Double) -> Unit) { var price by remember { mutableStateOf("") }; FormDialog("Новая цена: ${instrument.ticker}", close, { save(price.toDoubleOrNull() ?: 0.0) }) { FormField(price, { price = it }, "Цена на сегодня") } }

@Composable
fun TradeDialog(accounts: List<Account>, instruments: List<Instrument>, employees: List<Employee>, value: Trade? = null, currentEmployee: Employee? = null, close: () -> Unit, save: (NewTrade) -> Unit) {
    var account by remember { mutableStateOf(value?.accountId ?: accounts.firstOrNull()?.id) }; var instrument by remember { mutableStateOf(value?.instrumentId ?: instruments.firstOrNull()?.id) }; var employee by remember { mutableStateOf(value?.employeeId ?: currentEmployee?.id ?: employees.firstOrNull()?.id) }
    var type by remember { mutableStateOf(value?.type ?: TradeType.BUY) }; var date by remember { mutableStateOf(value?.date ?: LocalDate.now().toString()) }; var quantity by remember { mutableStateOf(value?.quantity?.toString().orEmpty()) }; var price by remember { mutableStateOf(value?.unitPrice?.toString().orEmpty()) }; var commission by remember { mutableStateOf(value?.commission?.toString() ?: "0") }; var comment by remember { mutableStateOf(value?.comment.orEmpty()) }
    FormDialog(if (value == null) "Новая сделка" else "Редактирование сделки", close, { if (account != null && instrument != null && employee != null) save(NewTrade(account!!, instrument!!, employee!!, type, date, quantity.toDoubleOrNull() ?: 0.0, price.toDoubleOrNull() ?: 0.0, commission.toDoubleOrNull() ?: 0.0, comment.ifBlank { null })) }) {
        Selector("Счет", accounts, account, { it.id }, { "${it.accountNumber} — ${it.clientName}" }) { account = it }
        Selector("Инструмент", instruments, instrument, { it.id }, { "${it.ticker} — ${it.name}" }) { selected -> instrument = selected; if (value == null) price = instruments.first { it.id == selected }.latestPrice?.toString().orEmpty() }
        Selector("Сотрудник", employees, employee, { it.id }, { it.fullName }) { employee = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(type == TradeType.BUY, { type = TradeType.BUY }, { Text("Покупка") }); FilterChip(type == TradeType.SELL, { type = TradeType.SELL }, { Text("Продажа") }) }
        FormField(date, { date = it }, "Дата, ГГГГ-ММ-ДД"); FormField(quantity, { quantity = it }, "Количество"); FormField(price, { price = it }, "Цена"); FormField(commission, { commission = it }, "Комиссия"); FormField(comment, { comment = it }, "Комментарий")
    }
}

@Composable
fun DividendDialog(accounts: List<Account>, instruments: List<Instrument>, value: Dividend? = null, close: () -> Unit, save: (Long, Long, Double, Double) -> Unit) {
    var account by remember { mutableStateOf(value?.accountId ?: accounts.firstOrNull()?.id) }; var instrument by remember { mutableStateOf(value?.instrumentId ?: instruments.firstOrNull()?.id) }; var amount by remember { mutableStateOf(value?.amount?.toString().orEmpty()) }; var tax by remember { mutableStateOf(value?.taxAmount?.toString() ?: "0") }
    FormDialog(if (value == null) "Новое начисление" else "Редактирование начисления", close, { if (account != null && instrument != null) save(account!!, instrument!!, amount.toDoubleOrNull() ?: 0.0, tax.toDoubleOrNull() ?: 0.0) }) {
        Selector("Счет", accounts, account, { it.id }, { "${it.accountNumber} — ${it.clientName}" }) { account = it }; Selector("Инструмент", instruments, instrument, { it.id }, { "${it.ticker} — ${it.name}" }) { instrument = it }; FormField(amount, { amount = it }, "Сумма"); FormField(tax, { tax = it }, "Налог")
    }
}

@Composable
fun CouponDialog(accounts: List<Account>, instruments: List<Instrument>, value: Coupon? = null, close: () -> Unit, save: (Long, Long, Double, Double) -> Unit) {
    var account by remember { mutableStateOf(value?.accountId ?: accounts.firstOrNull()?.id) }; var instrument by remember { mutableStateOf(value?.instrumentId ?: instruments.firstOrNull()?.id) }; var amount by remember { mutableStateOf(value?.amount?.toString().orEmpty()) }; var tax by remember { mutableStateOf(value?.taxAmount?.toString() ?: "0") }
    FormDialog(if (value == null) "Новая выплата купона" else "Редактирование выплаты купона", close, { if (account != null && instrument != null) save(account!!, instrument!!, amount.toDoubleOrNull() ?: 0.0, tax.toDoubleOrNull() ?: 0.0) }) {
        Selector("Счет", accounts, account, { it.id }, { "${it.accountNumber} — ${it.clientName}" }) { account = it }; Selector("Инструмент", instruments, instrument, { it.id }, { "${it.ticker} — ${it.name}" }) { instrument = it }; FormField(amount, { amount = it }, "Сумма"); FormField(tax, { tax = it }, "Налог")
    }
}

@Composable fun ConfirmDialog(title: String, text: String, close: () -> Unit, confirm: () -> Unit) = AlertDialog(close, title = { Text(title) }, text = { Text(text) }, confirmButton = { Button(confirm) { Text("Подтвердить") } }, dismissButton = { TextButton(close) { Text("Отмена") } })

@Composable private fun FormDialog(title: String, close: () -> Unit, save: () -> Unit, content: @Composable ColumnScope.() -> Unit) = AlertDialog(close, title = { Text(title) }, text = { Column(Modifier.width(480.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }, confirmButton = { Button(save) { Text("Сохранить") } }, dismissButton = { TextButton(close) { Text("Отмена") } })
@Composable private fun FormField(value: String, change: (String) -> Unit, label: String) = OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
@Composable private fun <T> Selector(label: String, values: List<T>, selected: Long?, id: (T) -> Long, text: (T) -> String, change: (Long) -> Unit) { var expanded by remember { mutableStateOf(false) }; Box { OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text("$label: ${values.firstOrNull { id(it) == selected }?.let(text) ?: "не выбран"}") }; DropdownMenu(expanded, { expanded = false }) { values.forEach { value -> DropdownMenuItem({ Text(text(value)) }, { change(id(value)); expanded = false }) } } } }
@Composable private fun StringSelector(label: String, values: List<String>, selected: String, change: (String) -> Unit) { var expanded by remember { mutableStateOf(false) }; Box { OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text("$label: $selected") }; DropdownMenu(expanded, { expanded = false }) { values.forEach { value -> DropdownMenuItem({ Text(value) }, { change(value); expanded = false }) } } } }
