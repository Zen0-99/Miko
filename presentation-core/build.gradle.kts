import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("multiplatform")
}

val cmpVersion = "1.8.2" // JetBrains Compose Multiplatform — matches ios-app

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
                api(projects.core.common)
                api(projects.i18n)

                // Compose Multiplatform — same packages as androidx.*, so
                // shared imports are unchanged; CMP -android artifacts are
                // thin redirects onto the androidx artifacts app already uses.
                implementation("org.jetbrains.compose.foundation:foundation:$cmpVersion")
                implementation("org.jetbrains.compose.material3:material3:$cmpVersion")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.5")
                implementation("org.jetbrains.compose.animation:animation:$cmpVersion")
                implementation("org.jetbrains.compose.ui:ui:$cmpVersion")
                implementation("org.jetbrains.compose.ui:ui-util:$cmpVersion")
                api(compose.haze)

                implementation(kotlinx.immutables)
            }
        }
        val iosMain by getting {
            dependencies {
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(compose.activity)
                implementation(compose.ui.tooling.preview)
                implementation(androidx.paging.runtime)
                implementation(androidx.paging.compose)
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
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
    namespace = "tachiyomi.presentation.core"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}
