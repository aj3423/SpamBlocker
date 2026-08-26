plugins {
    alias(libs.plugins.android.application)
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
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        versionCode = 515
        versionName = "5.15"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "market"
    productFlavors {
        create("fdroid") {
            dimension = "market"
            applicationId = "spam.blocker"
        }
        create("googleplay") {
            dimension = "market"
            applicationId = "spam.blocker.googleplay"
        }
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
        create("releaseNoR8") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        packaging {
            resources.excludes.add("META-INF/LICENSE.md")
            resources.excludes.add("META-INF/LICENSE-notice.md")
            jniLibs {
                useLegacyPackaging = true
            }
        }
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    packaging {
        jniLibs {
            excludes += setOf(
                "**/armeabi-v7a/**",
                "**/x86/**",
                "**/x86_64/**",
            )
            pickFirsts += setOf(
                "**/libc++_shared.so",
            )
        }
    }
}

tasks.configureEach {
    if (name.contains("ReleaseNoR8") && name.contains("lintVital", ignoreCase = true)) {
        enabled = false
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    val eval = project.findProperty("smsAiEval")?.toString() == "true"
    if (eval) {
        systemProperty("smsAiEval", "true")
        System.getenv("SMS_AI_MODEL")?.let { systemProperty("smsAiModel", it) }
        System.getenv("SMS_AI_SAMPLES")?.let { systemProperty("smsAiSamples", it) }
        System.getenv("SMS_AI_LLAMA_SERVER")?.let { systemProperty("smsAiLlamaServer", it) }
        environment("PATH", System.getenv("PATH") ?: "")
        testLogging {
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            events("passed", "failed", "skipped")
        }
    }
}

dependencies {
    implementation(libs.androidx.browser)
    implementation(libs.androidx.runtime)
    // third-party
    implementation(libs.lazycolumnscrollbar) // for scroll bar
    implementation(libs.reorderable) // for reordering Action items with drag & drop

    // google
    implementation(libs.libphonenumber) // for checking whether 33123 and +33123 are the same number
    implementation(libs.geocoder) // geo database from libphonenumber
    implementation(libs.carrier) // carrier database from libphonenumber

    // jetbrains kotlinx
    implementation(libs.serialization.json) // for backup/restore json serialization
    implementation(libs.androidx.work.runtime.ktx) // for WorkManager

    // jetpack compose
    implementation(platform(libs.compose.bom)) // auto compose version control
    implementation(libs.compose.activity) // for ComponentActivity
    implementation(libs.compose.material3) // for components like Scaffold, Surface
    implementation(libs.compose.ui)

    implementation(libs.litertlm.android)

    // testing
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.mockk)
}
