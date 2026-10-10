import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.sqldelight)
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
                implementation(projects.domain)
                implementation(projects.core.common)
                implementation(projects.i18n)

                api(kotlinx.coroutines.core)
                implementation(kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.io.core)
                implementation(libs.ktor.client.core)
                implementation(libs.paging.common)

                implementation(libs.sqldelight.coroutines.kmp)
                api(libs.sqldelight.android.paging)
            }
        }
        val iosMain by getting {
            dependencies {
                implementation(libs.sqldelight.native.driver)
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.sqldelight.android.driver)
                implementation(libs.sqldelight.coroutines)
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
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-Xexpect-actual-classes",
        )
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("tachiyomi.data")
            dialect(libs.sqldelight.dialects.sql)
            schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
            srcDirs.from(project.file("./src/main/sqldelight"))
        }
        create("AnimeDatabase") {
            packageName.set("tachiyomi.mi.data")
            dialect(libs.sqldelight.dialects.sql)
            schemaOutputDirectory.set(project.file("./src/main/sqldelightanime"))
            srcDirs.from(project.file("./src/main/sqldelightanime"))
        }
        create("NovelDatabase") {
            packageName.set("tachiyomi.novel.data")
            dialect(libs.sqldelight.dialects.sql)
            schemaOutputDirectory.set(project.file("./src/main/sqldelightnovel"))
            srcDirs.from(project.file("./src/main/sqldelightnovel"))
        }
        create("AchievementsDatabase") {
            packageName.set("tachiyomi.db.achievement")
            dialect(libs.sqldelight.dialects.sql)
            schemaOutputDirectory.set(project.file("./src/main/sqldelightachievements"))
            srcDirs.from(project.file("./src/main/sqldelightachievements"))
        }
    }
}

android {
    namespace = "tachiyomi.data"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}
