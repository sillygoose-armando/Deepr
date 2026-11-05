import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Default template plus a "jvmShared" group (android + jvm) for code using java.*
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withAndroidTarget()
                withJvm()
            }
        }
    }
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.components.resources)
            api(libs.compose.material3)
            api(libs.jetbrains.navigation3.ui)
            api(libs.androidx.navigation3.runtime)
            api(libs.jetbrains.lifecycle.viewmodel.navigation3)
            implementation(libs.coil.compose)
            implementation(libs.qrose)
            implementation(libs.tabler.icons)
            implementation(libs.compose.material.icons.extended.mp)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.haze)
            implementation(libs.haze.materials)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.compose.mp)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        // java.text implementation of formatDateTime
        getByName("jvmSharedMain").kotlin.srcDir("src/jvmShared/kotlin")
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.zxing.scanner)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = "DeeprUI"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "DeeprUI"
            isStatic = true
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.yogeshpaliyal.deepr.ui.resources"
    generateResClass = always
}

android {
    namespace = "com.yogeshpaliyal.deepr.ui"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
