import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.phincall.webrtc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.phincall.webrtc"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "4.0.10"

        ndk {
            // Match the 16 KB ELF-aligned native ABIs shipped inside oceanacustomerwebvoicevideo.aar.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
    val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
    val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")?.takeIf { it.isNotBlank() }
    val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
    val hasReleaseSigning = keystorePath != null &&
        keystorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            // Store native .so libraries uncompressed and aligned on 16 KB boundaries (Android 15+ compliance).
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES.txt",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/DEPENDENCIES",
                "META-INF/notice.txt",
                "META-INF/license.txt",
                "META-INF/dependencies.txt",
                "META-INF/LGPL2.1",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Avaya WebRTC Connect SDK 4.0.10 (CSDK 605.1.10, Android SDK 36 & 16 KB page aligned)
    implementation(files("libs/oceanacustomerwebvoicevideo.aar"))

    // Patched Netty 4.1.x replacing legacy 4.1.17.Final to remediate known HTTP/TLS CVEs.
    implementation("io.netty:netty-all:4.1.119.Final")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // AndroidX UI, lifecycle, and coroutines
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
