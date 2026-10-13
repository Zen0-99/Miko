import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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
                implementation(projects.i18nAniyomi)
                implementation(projects.sourceApi)

                // Compose Multiplatform — same packages as androidx.*
                implementation("org.jetbrains.compose.foundation:foundation:$cmpVersion")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation("org.jetbrains.compose.material3:material3:$cmpVersion")
                implementation("org.jetbrains.compose.runtime:runtime:$cmpVersion")
                implementation("org.jetbrains.compose.ui:ui:$cmpVersion")
                implementation("org.jetbrains.compose.ui:ui-util:$cmpVersion")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.5")

                implementation(kotlinx.coroutines.core)
                implementation(kotlinx.immutables)
                implementation(kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.paging.common)
                implementation(libs.sqldelight.coroutines.kmp)
                // project.dependencies.platform — bare platform() is a
                // compile error in KMP sourceSet blocks (KT-58759).
                implementation(project.dependencies.platform(libs.coil.bom))
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor3)
                // Chapter-reader HTML→text — same Ksoup pin source-js uses.
                implementation("com.fleeksoft.ksoup:ksoup:0.2.5")
                // DownloadManager — shared fs + http for item downloads.
                implementation(libs.kotlinx.io.core)
                implementation(libs.ktor.client.core)
                // Manage-catalogs drag reorder — KMP since 2.x.
                implementation(libs.reorderable)
            }
        }

        val androidMain by getting {
            dependencies {
                // VideoPlayer.actual — Android's episode player surface.
                implementation(libs.media3.exoplayer)
                implementation(libs.media3.ui)
                implementation(libs.media3.common)
                implementation(libs.ktor.client.okhttp)
            }
        }

        val iosMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
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
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-Xexpect-actual-classes",
        )
    }
}

android {
    namespace = "miko.ui"
}
