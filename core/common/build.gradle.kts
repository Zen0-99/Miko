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
                implementation(projects.i18n)

                api(kotlinx.coroutines.core)
                api(kotlinx.serialization.json)
            }
        }
        val iosMain by getting {
            dependencies {
                // coroutines-core is unversioned in the kotlinx catalog;
                // Android gets its version from the BOM platform below.
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
                implementation("dev.whyoleg.cryptography:cryptography-provider-apple:0.5.0")
            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.logcat)

                api(libs.rxjava)

                api(libs.okhttp.core)
                api(libs.okhttp.logging)
                api(libs.okhttp.brotli)
                api(libs.okhttp.dnsoverhttps)
                api(libs.okio)

                implementation("local.lib:image-decoder:16kb@aar")

                implementation(libs.unifile)
                implementation(libs.libarchive)

                api(kotlinx.serialization.json.okio)

                api(libs.preferencektx)

                implementation(libs.jsoup)

                // Sort
                implementation(libs.natural.comparator)

                // JavaScript engine
                implementation(libs.bundles.js.engine)

                // FFmpeg-kit
                implementation(aniyomilibs.ffmpeg.kit)

                // TorrServer
                implementation("local.lib:torrserver:0.1.0-16kb@aar")

                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-Xexpect-actual-classes",
        )
    }
}

android {
    namespace = "eu.kanade.tachiyomi.core.common"
}
