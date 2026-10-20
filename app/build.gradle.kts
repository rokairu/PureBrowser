plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.purebrowser.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.purebrowser.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    // Hanya set ikon inti (bukan material-icons-extended yang memakan puluhan MB dex).
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Mesin Gecko (Firefox), bukan System WebView. v153 = terbaru yang lolos compileSdk 36.
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:153.0.20260810162159")
    // Klien WireGuard resmi untuk fitur VPN
    implementation("com.wireguard.android:tunnel:1.0.20260102")
}
