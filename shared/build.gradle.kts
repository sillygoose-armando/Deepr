import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    // Expect/actual classes (HtmlParser) are used here.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Default template plus a "jvmShared" group (android + jvm) for code using java.* / javax.crypto.
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
            api(libs.sqldelight.runtime)
            api(libs.coroutines.extensions)
            api(libs.androidx.datastore.preferences.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }
        // javax.crypto implementation of P2pCrypto; the sync track keeps it in src/jvmShared.
        getByName("jvmSharedMain").kotlin.srcDir("src/jvmShared/kotlin")
        getByName("jvmSharedMain").dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.jsoup)
        }
        androidMain.dependencies {
            implementation(libs.androidx.datastore.preferences)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.yogeshpaliyal.deepr.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

sqldelight {
    databases {
        create("DeeprDB") {
            packageName.set("com.yogeshpaliyal.deepr")
        }
    }
}
