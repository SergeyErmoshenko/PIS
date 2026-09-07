package me.investcompany.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

class ArchitectureTest {
    @Test
    fun `domain does not depend on UI or persistence`() {
        Konsist.scopeFromPackage("me.investcompany.domain")
            .files
            .assertFalse { file -> file.imports.any { it.name.contains("compose") || it.name.contains("sqldelight") || it.name.contains("persistence") } }
    }
}
