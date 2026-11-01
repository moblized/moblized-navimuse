import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.navimuse"
    compileSdk = 36

    // GitHub builds pass their run number (-PversionCode=N) so each release is a newer version.
    val buildNumber = (project.findProperty("versionCode") as String?)?.toIntOrNull()

    defaultConfig {
        applicationId = "app.navimuse"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber ?: 1
        versionName = "1.0.${buildNumber ?: 0}"
    }

    signingConfigs {
        // One shared key for every build, so a newer APK always installs over an older one
        // and keeps your login. It's in the repo, so it's only for sideloading.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Bridges Media3's ListenableFutures and coroutines (await / future { }).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Playback: background audio, lock screen / notification controls, Bluetooth, Android Auto.
    val media3 = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    // Streams over the app's OkHttp client, so every song reuses one warm connection to the server.
    implementation("androidx.media3:media3-datasource-okhttp:$media3")

    // Daily update check in the background
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Subsonic API calls to Navidrome (and the optional AudioMuse-AI mood search)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Album art, and the colour pulled from it for page backgrounds
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
