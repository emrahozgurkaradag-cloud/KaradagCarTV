plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.karadaglabs.cartv"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.karadaglabs.cartv"
        minSdk = 29
        targetSdk = 37
        versionCode = 47
        versionName = "1.0.47"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    // ============================================================
    // FERMATA / ANDROID AUTO VIDEO SDK
    // ============================================================

    implementation(files("libs/aauto.aar"))

    // ============================================================
    // ANDROIDX CORE
    // ============================================================

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.activity.ktx)

    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.appcompat)

    implementation(libs.androidx.constraintlayout)

    // ============================================================
    // COMPOSE
    // ============================================================

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.compose.ui)

    implementation(libs.androidx.compose.ui.graphics)

    implementation(libs.androidx.compose.ui.tooling.preview)

    // ============================================================
    // ANDROID AUTO - MODERN CAR APP API
    //
    // CarMirrorService.kt tarafından kullanılıyor.
    // aauto.aar ile birlikte şimdilik tutuluyor.
    // ============================================================

    implementation("androidx.car.app:app:1.7.0")

    implementation("androidx.car.app:app-projected:1.7.0")

    // Jetifier rewrites aauto.aar onto AndroidX FragmentManager.
    implementation("androidx.fragment:fragment-ktx:1.8.8")

    // ============================================================
    // MEDIA3 / EXOPLAYER
    // ============================================================

    implementation("androidx.media3:media3-exoplayer:1.10.1")

    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")

    implementation("androidx.media3:media3-ui:1.10.1")

    implementation("androidx.media3:media3-session:1.10.1")

    // ============================================================
    // MATERIAL
    // ============================================================

    implementation(libs.material)

    // ============================================================
    // TEST
    // ============================================================

    testImplementation(libs.junit)

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    // ============================================================
    // DEBUG
    // ============================================================

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
}