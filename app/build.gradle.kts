import java.io.FileInputStream
import java.util.Properties


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "1.9.24"
}

// load signing key for release
val keystoreProperties = Properties()
var keystorePropertiesFile = rootProject.file("/e/android/release_keys/SpamBlocker.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        create("release") {
            enableV2Signing = false
            enableV3Signing = true

            storeFile = file(keystoreProperties["storeFile"].toString())
            storePassword = keystoreProperties["storePassword"].toString()
            keyPassword = keystoreProperties["keyPassword"].toString()
            keyAlias = keystoreProperties["keyAlias"].toString()
        }
    }
    namespace = "spam.blocker"
    compileSdk = 35

    defaultConfig {
        applicationId = "spam.blocker"
        minSdk = 29
        targetSdk = 35
        versionCode = 303
        versionName = "3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        // for github action only
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // the keystore file doesn't exist on github action,
            // use "debug" instead and it will be signed by signing action later.
            signingConfig =
                signingConfigs.getByName(if (keystorePropertiesFile.exists()) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        packagingOptions {
            resources.excludes.add("META-INF/LICENSE.md")
            resources.excludes.add("META-INF/LICENSE-notice.md")
            jniLibs {
                useLegacyPackaging = true
            }
        }
    }
    buildToolsVersion = "35.0.0"
}

dependencies {
    // third-party
    implementation(libs.lazycolumnscrollbar) // for scroll bar
    implementation(libs.reorderable) // for reordering Action items with drag & drop

    // jetbrains kotlinx
    implementation(libs.serialization.json) // for backup/restore json serialization
    implementation(libs.androidx.work.runtime.ktx) // for WorkManager

    // jetpack compose
    implementation(platform(libs.compose.bom)) // auto compose version control
    implementation(libs.compose.activity) // for ComponentActivity
    implementation(libs.compose.material3) // for components like Scaffold, Surface
    implementation(libs.compose.ui)

    // testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.mockk)
}