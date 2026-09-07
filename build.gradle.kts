import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.sqldelight)
}

group = "me.investcompany"
version = "1.0.0"

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.bundles.kotlinx)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.bundles.sqldelight)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
}

sqldelight {
    databases {
        create("InvestDatabase") {
            packageName.set("me.investcompany.persistence.db")
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "me.investcompany.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "InvestCompany"
            packageVersion = project.version.toString()
            description = "Информационная система инвестиционной компании"
            vendor = "InvestCompany"
        }
    }
}

val sqliteNativeDir = layout.buildDirectory.dir("tmp/sqlite-native")

tasks.test {
    useJUnitPlatform()
    systemProperty("org.sqlite.tmpdir", sqliteNativeDir.get().asFile.absolutePath)
    doFirst {
        sqliteNativeDir.get().asFile.mkdirs()
    }
}
