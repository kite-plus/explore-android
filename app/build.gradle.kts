import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release builds are signed when a keystore is configured, by the release
// workflow from its secrets or locally from keystore.properties.
val signingFile = rootProject.file("keystore.properties")
val signingValues = mutableMapOf<String, String>()
if (signingFile.exists()) {
    signingFile.readLines().forEach { line ->
        val parts = line.split("=", limit = 2)
        if (parts.size == 2) signingValues[parts[0].trim()] = parts[1].trim()
    }
}
fun signingValue(key: String, env: String): String? = signingValues[key] ?: System.getenv(env)?.takeIf { it.isNotEmpty() }

android {
    namespace = "plus.kite.explore"
    compileSdk = 37

    defaultConfig {
        applicationId = "plus.kite.explore"
        minSdk = 29
        targetSdk = 37
        // The release workflow sets both from the tag and the run number.
        versionCode = (findProperty("explore.versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("explore.versionName") as String?) ?: "0.1.0"
    }

    signingConfigs {
        val store = signingValue("storeFile", "ANDROID_KEYSTORE_FILE")
        if (store != null) {
            create("release") {
                storeFile = file(store)
                storePassword = signingValue("storePassword", "ANDROID_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "ANDROID_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Installs beside a release build, as the iOS dev builds do.
            applicationIdSuffix = ".dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = false
        // New versions of libraries are adopted on purpose, not by lint.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.glance.appwidget)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Reads share card QR codes back in tests; the app draws its own.
    testImplementation(libs.zxing.core)
}
