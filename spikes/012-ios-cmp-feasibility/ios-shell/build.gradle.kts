// Spike 012 — iOS compile/eval proof. Versions mirror Miko's catalogs:
// Kotlin 2.2.0, coroutines 1.11.0, serialization 1.9.0. Compose uses the
// JetBrains CMP plugin (proven iOS path; androidx artifacts in commonMain
// also publish darwin targets — candidate for the real port).
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    kotlin("plugin.compose") version "2.2.0"
    id("org.jetbrains.compose") version "1.8.2"
}

kotlin {
    listOf(iosArm64(), iosX64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "MikoSpike"
            isStatic = true
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.ktor:ktor-client-core:3.1.3")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.1.3")
            implementation("io.ktor:ktor-client-curl:3.1.3")
        }
        iosTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
