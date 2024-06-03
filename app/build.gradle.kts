import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
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
            storeFile = file(keystoreProperties["storeFile"].toString())
            storePassword = keystoreProperties["storePassword"].toString()
            keyPassword = keystoreProperties["keyPassword"].toString()
            keyAlias = keystoreProperties["keyAlias"].toString()
        }
    }
    namespace = "spam.blocker"
    compileSdk = 34

    defaultConfig {
        applicationId = "spam.blocker"
        minSdk = 29
        targetSdk = 34
        versionCode = 19
        versionName = "1.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        // for github action only
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // the keystore file doesn't exist on github action,
            // use "debug" instead and it will be signed by signing action later.
            signingConfig = signingConfigs.getByName(if (keystorePropertiesFile.exists()) "release" else "debug" )
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
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // For control over item selection of both touch and mouse driven selection
    implementation("androidx.recyclerview:recyclerview-selection:1.1.0")

    implementation("me.leolin:ShortcutBadger:1.1.22@aar") // for the number indicator on app icon

    implementation("il.co.theblitz:observablecollections:1.4.2") // for UI observer pattern, in jcenter only

    implementation("com.github.skydoves:balloon:1.6.4") // for tooltip

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.0") // for backup/restore

    implementation("me.grantland:autofittextview:0.2.1") // for auto-fit label/button

    implementation("com.github.DavidProdinger:weekdays-selector:1.1.1") // for weekday picker

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.activity)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    androidTestImplementation("io.mockk:mockk-android:1.13.10")


}