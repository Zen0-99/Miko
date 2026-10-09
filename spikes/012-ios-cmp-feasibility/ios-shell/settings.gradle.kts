// Spike 012 harness — STANDALONE Gradle build. Intentionally NOT part of
// Miko's root settings: proves iOS targets compile without touching the
// real build, and CI can run it in isolation.
rootProject.name = "miko-ios-spike"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
