plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "1.9.24"
}

android {
    // https://github.com/aj3423/SpamBlocker/issues/184
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            enableV2Signing = false
            enableV3Signing = true

            storeFile = file("../../keystore.jks")
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
        }
    }
    namespace = "spam.blocker"
    compileSdk = 35

    defaultConfig {
        applicationId = "spam.blocker"
        minSdk = 29
        targetSdk = 35
        versionCode = 305
        versionName = "3.5"

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

            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.libphonenumber) // for checking whether 33123 and +33123 are the same number

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
