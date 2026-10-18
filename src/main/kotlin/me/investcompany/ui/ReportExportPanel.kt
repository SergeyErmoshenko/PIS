package me.investcompany.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.investcompany.domain.Account
import me.investcompany.report.ReportFormat
import me.investcompany.report.ReportKind
import me.investcompany.report.ReportRequest
import me.investcompany.report.ReportService
import java.awt.Desktop
import java.io.File

/**
 * Export panel of the reports screen: report selection, optional account/period parameters and
 * saving the generated document to disk.
 */
@Composable
fun ReportExportPanel(
    service: ReportService,
    accounts: List<Account>,
    message: (String) -> Unit,
) {
    var kind by remember { mutableStateOf(ReportKind.BROKER_TRADES) }
    var format by remember { mutableStateOf(ReportFormat.WORD) }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun export(chooseLocation: Boolean) {
        if (busy) return
        val request = ReportRequest(
            kind = kind,
            format = format,
            accountId = if (kind.requiresAccount) accountId else null,
            dateFrom = dateFrom.ifBlank { null },
            dateTo = dateTo.ifBlank { null },
        )
        busy = true
        scope.launch {
            try {
                val target = withContext(Dispatchers.IO) {
                    val destination = if (chooseLocation) {
                        chooseSaveLocation(service.suggestedFileName(request)) ?: return@withContext null
                    } else {
                        File(downloadsDirectory(), service.suggestedFileName(request))
                    }
                    service.saveTo(request, destination)
                }
                if (target == null) {
                    message("Сохранение отменено")
                } else {
                    openInSystem(target)
                    message("Отчет сохранен: ${target.absolutePath}")
                }
            } catch (error: Exception) {
                message(error.message ?: "Не удалось сформировать отчет")
            } finally {
                busy = false
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("Экспорт отчетной документации", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Формирование документов Word и Excel по данным системы",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PanelSelector("Отчет", ReportKind.entries, kind.title, Modifier.fillMaxWidth(), { it.title }) { kind = it }
            Text(
                kind.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PanelSelector("Формат", ReportFormat.entries, format.title, Modifier.width(260.dp), { it.title }) {
                    format = it
                }
                if (kind.requiresAccount) {
                    val selected = accounts.firstOrNull { it.id == accountId }
                    PanelSelector(
                        label = "Счет",
                        values = accounts,
                        selected = selected?.let { "${it.accountNumber} — ${it.clientName}" } ?: "не выбран",
                        modifier = Modifier.width(360.dp),
                        text = { "${it.accountNumber} — ${it.clientName}" },
                    ) { accountId = it.id }
                }
            }

            if (kind.requiresPeriod) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = dateFrom,
                        onValueChange = { dateFrom = it },
                        label = { Text("Дата от") },
                        placeholder = { Text("ГГГГ-ММ-ДД", maxLines = 1, overflow = TextOverflow.Clip) },
                        singleLine = true,
                        modifier = Modifier.width(180.dp),
                    )
                    OutlinedTextField(
                        value = dateTo,
                        onValueChange = { dateTo = it },
                        label = { Text("Дата до") },
                        placeholder = { Text("ГГГГ-ММ-ДД", maxLines = 1, overflow = TextOverflow.Clip) },
                        singleLine = true,
                        modifier = Modifier.width(180.dp),
                    )
                    Text(
                        "Пустой период — выгрузка за все время",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button({ export(chooseLocation = true) }, enabled = !busy) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Экспортировать в файл")
                }
                OutlinedButton({ export(chooseLocation = false) }, enabled = !busy) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сохранить в Загрузки")
                }
                if (busy) CircularProgressIndicator(Modifier.width(24.dp))
            }
        }
    }
}

@Composable
private fun <T> PanelSelector(
    label: String,
    values: List<T>,
    selected: String,
    modifier: Modifier = Modifier,
    text: (T) -> String,
    change: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) {
            Text("$label: $selected", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded, { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem({ Text(text(value)) }, { change(value); expanded = false })
            }
        }
    }
}

private fun chooseSaveLocation(fileName: String): File? {
    val chooser = javax.swing.JFileChooser(downloadsDirectory()).apply {
        dialogTitle = "Сохранить отчет"
        selectedFile = File(fileName)
    }
    return if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun downloadsDirectory(): File {
    val home = File(System.getProperty("user.home"))
    val downloads = File(home, "Downloads")
    return if (downloads.isDirectory) downloads else home
}

private fun openInSystem(file: File) {
    // Opening a document is a convenience: a headless or restricted desktop must not fail the export.
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
        }
    }
}
