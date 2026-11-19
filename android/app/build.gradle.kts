import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.phincall.webrtc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.phincall.webrtc"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "4.0.10"

        ndk {
            // Match the 16 KB ELF-aligned native ABIs shipped inside oceanacustomerwebvoicevideo.aar.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    val keystoreProps = Properties().apply {
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.isFile) {
            propsFile.inputStream().use(::load)
        }
    }
    fun resolveSigningProp(envName: String, propName: String): String? =
        System.getenv(envName)?.takeIf { it.isNotBlank() }
            ?: keystoreProps.getProperty(propName)?.takeIf { it.isNotBlank() }

    val keystorePath = resolveSigningProp("ANDROID_KEYSTORE_PATH", "storeFile")
    val keystorePassword = resolveSigningProp("ANDROID_KEYSTORE_PASSWORD", "storePassword")
    val releaseKeyAlias = resolveSigningProp("ANDROID_KEY_ALIAS", "keyAlias")
    val releaseKeyPassword = resolveSigningProp("ANDROID_KEY_PASSWORD", "keyPassword")
    val hasReleaseSigning = keystorePath != null &&
        keystorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystorePath!!)
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
        compose = true
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
    implementation(libs.netty.all)
    implementation(libs.slf4j.api)
    implementation(libs.okhttp)

    // Core AndroidX, Lifecycle, and Coroutines
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.google.material)

    // Jetpack Compose BOM + Material 3 + Type-Safe Navigation
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt Dependency Injection (KSP)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
