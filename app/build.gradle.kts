plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "cz.kuclab.hertzchat"
    compileSdk = 34

    defaultConfig {
        applicationId = "cz.kuclab.hertzchat"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Real phones are effectively all arm64-v8a today; keep armeabi-v7a
            // for older devices and x86_64 for emulators. Skip 32-bit x86 - it's
            // only relevant to very old emulator images, not real hardware.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    // Deliberately NOT split per-ABI: picking the wrong one of several
    // per-architecture APKs from a release page is confusing, and installing
    // a mismatched one means its native libs (WebRTC/libsignal/SQLCipher)
    // are simply missing, which crashes the app immediately on launch. One
    // universal APK that works on every device is worth the larger download.
    val releaseKeystorePath = file("../keystore/hertzchat-release.jks")
    signingConfigs {
        if (releaseKeystorePath.exists()) {
            create("release") {
                storeFile = releaseKeystorePath
                storePassword = "hertzchat123"
                keyAlias = "hertzchat"
                keyPassword = "hertzchat123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystorePath.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // libsignal ships a *_testing variant of its native lib that's
            // only for the library's own test suite - it's dead weight
            // (tens of MB per ABI) in a shipped app and pulls in nothing we use.
            excludes += "**/libsignal_jni_testing.so"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // DI
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Local persistence (contacts, messages, settings - all on-device only)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Local database is encrypted at rest (SQLCipher) - even the on-device
    // copy of messages/keys is unreadable without the Keystore-protected passphrase.
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // Serialization / coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // P2P transport: WebRTC (ICE/STUN/TURN + DataChannel)
    implementation("io.getstream:stream-webrtc-android:1.3.10")

    // Signaling client (WebSocket to blind rendezvous relay)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // End-to-end encryption: Signal Protocol (X3DH + Double Ratchet)
    implementation("org.signal:libsignal-client:0.86.5")
    implementation("org.signal:libsignal-android:0.86.5")

    // QR code (device migration, contact/identity sharing)
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // Media: image loading/playback
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.media3:media3-exoplayer:1.4.0")
    implementation("androidx.media3:media3-ui:1.4.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
