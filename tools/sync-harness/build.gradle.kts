plugins {
    kotlin("jvm") version "2.2.20"
    id("app.cash.sqldelight") version "2.1.0"
}

val appMain = rootDir.resolve("../../app/src/main")

kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.srcDir(appMain.resolve("java/com/yogeshpaliyal/deepr/p2p/core"))
}

sqldelight {
    databases {
        create("DeeprDB") {
            packageName.set("com.yogeshpaliyal.deepr")
            srcDirs.setFrom(appMain.resolve("sqldelight"))
        }
    }
}

dependencies {
    implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
