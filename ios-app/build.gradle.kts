// iOS application module — pure Apple-target KMP, no androidTarget.
// Produces the static MikoKit framework that iosApp/ (xcodegen shell)
// embeds via embedAndSignAppleFrameworkForXcode.
//
// NOTE: the org.jetbrains.compose Gradle plugin is deliberately NOT
// applied — it registers a `compose` extension that collides with the
// project's `compose` version catalog (settings.gradle.kts). We apply
// the Kotlin compose-compiler plugin (buildSrc classpath) and reference
// the CMP artifacts by coordinates instead; same libraries, no clash.
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("mihon.code.lint")
    alias(libs.plugins.moko)
}

val cmpVersion = "1.8.2" // JetBrains Compose Multiplatform — spike-012 proven

kotlin {
    listOf(iosArm64(), iosX64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "MikoKit"
            isStatic = true
        }
    }

    // Template is otherwise applied lazily after evaluation; apply now so
    // the iosMain accessor exists in sourceSets {}.
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.network)
            implementation(projects.data)
            implementation(projects.domain)
            implementation(projects.sourceApi)
            implementation(projects.sourceJs)
            implementation(projects.ui)
            implementation("org.jetbrains.compose.runtime:runtime:$cmpVersion")
            implementation("org.jetbrains.compose.foundation:foundation:$cmpVersion")
            implementation("org.jetbrains.compose.material3:material3:$cmpVersion")
            implementation("org.jetbrains.compose.ui:ui:$cmpVersion")
            implementation(kotlinx.coroutines.core)
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
        )
    }
}

// Static frameworks can't carry resources — moko packs them into the
// framework dir and the Xcode build phase copies them into the app via
// copyFrameworkResourcesToApp.
multiplatformResources {
    resourcesPackage.set("miko.ios")
}
