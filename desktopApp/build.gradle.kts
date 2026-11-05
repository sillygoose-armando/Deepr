plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
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
}

compose.desktop {
    application {
        mainClass = "com.yogeshpaliyal.deepr.desktop.MainKt"
    }
}
