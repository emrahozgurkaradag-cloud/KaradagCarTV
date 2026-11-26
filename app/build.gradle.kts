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
        versionCode = 7
        versionName = "1.1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    /*
     * distribution boyutu
     *
     * play (varsayılan): Google Play / Android Auto kurallarına uygun.
     *   - Android Auto: medya kategorisi (Radyo + Canlı TV sesi).
     *   - Park hâlinde video: android:appCategory="video"
     *     (Android Automotive OS'ta genel kullanım, Android Auto'da
     *     Google beta / erken erişim programına bağlı).
     *
     * projection: Android Auto araç ekranında GÖRÜNTÜLÜ oynatma.
     *   aauto.aar CarActivity (CATEGORY_PROJECTION_OEM) penceresine
     *   ExoPlayer SurfaceView; araç sensörlerine bağlı park kilidi.
     *   Google'ın resmî üçüncü taraf API'si DEĞİLDİR. Android Auto
     *   17.x bu tür uygulamaları yalnızca Play Store yükleyicisiyle
     *   kurulmuşsa (ör. Play iç/kapalı test kanalı) veya geliştirici
     *   head unit'te listeler. Ayrı applicationId ile yan yana kurulur.
     */
    flavorDimensions += "distribution"

    productFlavors {
        create("play") {
            dimension = "distribution"
            isDefault = true
        }
        create("projection") {
            dimension = "distribution"
            applicationIdSuffix = ".projection"
            versionNameSuffix = "-projection"
        }
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

    // Yalnızca projection yapısı (resmî olmayan Google Auto SDK).
    "projectionImplementation"(files("libs/aauto.aar"))

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
    // ANDROID AUTO - CAR APP LIBRARY (yalnızca projection)
    //
    // CarMirrorService.kt (NAVIGATION şablonu + MediaProjection)
    // Google kurallarına uymadığı için play yapısında yok.
    // ============================================================

    "projectionImplementation"("androidx.car.app:app:1.7.0")

    "projectionImplementation"("androidx.car.app:app-projected:1.7.0")

    // Jetifier rewrites aauto.aar onto AndroidX FragmentManager.
    "projectionImplementation"("androidx.fragment:fragment-ktx:1.8.8")

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