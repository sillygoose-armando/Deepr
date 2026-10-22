// Standalone JVM build: compiles shared's Deepr.sq + migrations + p2p/core without the Android SDK.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
rootProject.name = "sync-harness"
