package me.investcompany.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import me.investcompany.persistence.SqlDelightInvestmentRepository
import me.investcompany.ui.InvestmentApp
import java.io.File

fun main() = application {
    val repository = SqlDelightInvestmentRepository.open(databaseFile())
    Window(onCloseRequest = ::exitApplication, title = "Инвестиционная компания") {
        window.minimumSize = java.awt.Dimension(1100, 700)
        DisposableEffect(repository) { onDispose(repository::close) }
        InvestmentApp(repository)
    }
}

private fun databaseFile(): File {
    val home = System.getProperty("user.home")
    return File(home, ".investcompany/invest-company.db")
}
