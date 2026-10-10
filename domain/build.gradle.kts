import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

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
                implementation(projects.sourceApi)
                implementation(projects.core.common)

                api(kotlinx.coroutines.core)
                implementation(kotlinx.serialization.json)
                implementation(kotlinx.serialization.protobuf)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.io.core)
                implementation(libs.paging.common)
            }
        }
        val iosMain by getting {
            dependencies {
                // coroutines-core is unversioned in the kotlinx catalog;
                // Android gets its version from the BOM platform below.
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.unifile)
                api(libs.sqldelight.android.paging)
                compileOnly(libs.compose.stablemarker)
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.bundles.test)
                implementation(kotlinx.coroutines.test)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-Xexpect-actual-classes",
        )
    }
}

android {
    namespace = "tachiyomi.domain"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}
