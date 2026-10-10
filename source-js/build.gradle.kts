import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    // No androidTarget: Android keeps its J2V8 runtime in `app` for this
    // phase. jvm() exists so commonMain + commonTest compile and run locally
    // (CI also compiles iosSimulatorArm64 + runs iosTest).
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // Template is otherwise applied lazily after evaluation; apply now so
    // the iosMain/jvmMain accessors exist in sourceSets {}.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinx.coroutines.core)
                api(kotlinx.serialization.json)
                api(kotlinx.serialization.protobuf)
                // Pinned to 0.2.5: 0.2.6's klibs are built with Kotlin 2.3.10
                // (abi_version 2.3.0) which our Kotlin 2.2.0 K/N toolchain
                // cannot consume. 0.2.5 is built with Kotlin 2.2.0.
                api("com.fleeksoft.ksoup:ksoup:0.2.5")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val iosMain by getting {
            dependencies {
                // coroutines-core is unversioned in the kotlinx catalog;
                // the BOM platform supplies the version on non-Android targets.
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
                implementation(projects.core.network)
                implementation(projects.sourceApi)
                implementation(libs.kotlinx.datetime)
                implementation("dev.whyoleg.cryptography:cryptography-core:0.5.0")
                implementation("dev.whyoleg.cryptography:cryptography-provider-apple:0.5.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlin.time.ExperimentalTime",
        )
    }
}
