plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    android {
        namespace = "com.zakaria5729.flash_table_lamp.shared"
        compileSdk = 37
        minSdk = 24
        withHostTestBuilder {}.configure {}
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
            implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.ui:ui:1.12.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.13.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
