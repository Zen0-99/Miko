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
                api(kotlinx.serialization.json)
                api(kotlinx.coroutines.core)
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
                implementation(projects.core.common)
                api(libs.preferencektx)
                api(libs.injekt)

                // Extensions-lib ABI is JVM-bound: APKs link these exact
                // types (RxJava fetch* methods, okhttp3 Request/Response/
                // Headers, nanohttpd HttpServer, jsoup in parsed sources).
                api(libs.rxjava)
                api(libs.jsoup)
                api(aniyomilibs.nanohttpd)

                implementation(project.dependencies.platform(compose.bom))
                implementation(compose.runtime)

                // Workaround for https://youtrack.jetbrains.com/issue/KT-57605
                implementation(kotlinx.coroutines.android)
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

android {
    namespace = "eu.kanade.tachiyomi.source"

    defaultConfig {
        consumerProguardFile("consumer-proguard.pro")
    }
}
