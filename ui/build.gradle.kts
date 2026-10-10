import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("multiplatform")
}

val cmpVersion = "1.8.2" // JetBrains Compose Multiplatform — matches presentation-core/ios-app

kotlin {
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // Template is otherwise applied lazily after evaluation; apply now so
    // the iosMain accessor exists in sourceSets {}.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.presentationCore)
                api(projects.domain)

                implementation(projects.core.common)
                implementation(projects.data)
                implementation(projects.i18n)
                implementation(projects.sourceApi)

                // Compose Multiplatform — same packages as androidx.*
                implementation("org.jetbrains.compose.foundation:foundation:$cmpVersion")
                implementation("org.jetbrains.compose.material3:material3:$cmpVersion")
                implementation("org.jetbrains.compose.runtime:runtime:$cmpVersion")
                implementation("org.jetbrains.compose.ui:ui:$cmpVersion")
                implementation("org.jetbrains.compose.ui:ui-util:$cmpVersion")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.5")

                implementation(kotlinx.coroutines.core)
                implementation(kotlinx.immutables)
                implementation(libs.paging.common)
                implementation(libs.sqldelight.coroutines.kmp)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-Xexpect-actual-classes",
        )
    }
}

android {
    namespace = "miko.ui"
}
