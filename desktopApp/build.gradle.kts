import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}


dependencies {
    implementation(project(":ui"))
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.koin.test)
    // Compose UI tests on the desktop JVM (M3-03): JUnit4 rule + JUnit4 framework.
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.yogeshpaliyal.deepr.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Deepr"
            packageVersion = "1.0.28"
            description = "Deepr link manager"
            vendor = "Deepr"
            modules("java.instrument", "java.management", "java.net.http", "java.sql", "jdk.unsupported")
            linux { menuGroup = "Utility" }
            windows { menuGroup = "Deepr"; perUserInstall = true }
        }
    }
}
