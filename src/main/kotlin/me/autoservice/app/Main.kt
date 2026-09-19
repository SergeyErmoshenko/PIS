package me.autoservice.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import me.autoservice.persistence.SqlDelightAutoServiceRepository
import me.autoservice.ui.AutoServiceApp
import java.io.File

fun main() = application {
    val repository = SqlDelightAutoServiceRepository.open(databaseFile())
    Window(onCloseRequest = ::exitApplication, title = "Автосервис") {
        window.minimumSize = java.awt.Dimension(1100, 700)
        DisposableEffect(repository) { onDispose(repository::close) }
        AutoServiceApp(repository)
    }
}

private fun databaseFile(): File {
    val home = System.getProperty("user.home")
    return File(home, ".autoservice/auto-service.db")
}
