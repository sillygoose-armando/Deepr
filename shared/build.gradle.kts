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

    // iroh's Gradle metadata requires JVM 21; Android keeps its Java 11 bytecode target.
    jvmToolchain(21)

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
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.runtime)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.coroutines.extensions)
            api(libs.androidx.datastore.preferences.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
        }
        // javax.crypto implementation of P2pCrypto; the sync track keeps it in src/jvmShared.
        getByName("jvmSharedMain").kotlin.srcDir("src/jvmShared/kotlin")
        getByName("jvmSharedMain").dependencies {
            // Exclude the plain JNA jar: Android needs the @aar artifact (added in androidMain),
            // and the JVM target gets the plain jar in jvmMain.
            implementation("computer.iroh:iroh:${libs.versions.iroh.get()}") {
                exclude(group = "net.java.dev.jna", module = "jna")
            }
            implementation(libs.ktor.client.cio)
            implementation(libs.jsoup)
        }
        jvmMain.dependencies {
            implementation(libs.jna)
        }
        androidMain.dependencies {
            implementation(libs.androidx.datastore.preferences)
            // iroh's Android AAR ships libiroh_ffi.so per ABI; JNA must be the @aar so libjnidispatch.so
            // is packaged per ABI too (the plain jar's desktop natives do not work on Android).
            implementation("computer.iroh:iroh-android:${libs.versions.iroh.get()}") {
                exclude(group = "net.java.dev.jna", module = "jna")
            }
            implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.cryptokit)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.koin.test)
            implementation(libs.ktor.client.mock)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqldelight.native.driver)
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
