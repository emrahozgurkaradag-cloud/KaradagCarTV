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
        versionCode = 8
        versionName = "1.2.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    /*
     * distribution boyutu
     *
     * play (varsayılan): Google Play / Android Auto kurallarına uygun.
     *   - Android Auto: medya kategorisi (Radyo + Canlı TV sesi).
     *   - Park hâlinde video: android:appCategory="video"
     *     (Google belgelerine göre AAOS'ta; Android Auto durumu
     *     belgelerde "beta", bu projede doğrulanmadı).
     *
     * projection: Android Auto araç ekranında GÖRÜNTÜLÜ oynatma.
     *   aauto.aar CarActivity (CATEGORY_PROJECTION_OEM) penceresine
     *   ExoPlayer SurfaceView; araç sensörlerine bağlı park kilidi.
     *   Google'ın resmî üçüncü taraf API'si DEĞİLDİR. Görünürlük
     *   şartı (Play yükleyicisi / geliştirici head unit) bir kullanıcının
     *   AA 17.7 analizine dayanır, doğrulanmadı. Ayrı applicationId
     *   (.projection) ile yan yana kurulur.
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

    /*
     * Release imzası — debug anahtarından AYRI.
     *
     * Değerler yalnızca ortam değişkenlerinden okunur (GitHub Actions
     * Secrets veya yerel kabuk); depoda anahtar/parola YOKTUR:
     *   KARADAG_RELEASE_KEYSTORE           keystore dosya yolu
     *   KARADAG_RELEASE_KEYSTORE_PASSWORD
     *   KARADAG_RELEASE_KEY_ALIAS
     *   KARADAG_RELEASE_KEY_PASSWORD
     * Biri eksikse release APK'ları İMZASIZ üretilir
     * (app-*-release-unsigned.apk) ve derleme bunu uyarı olarak yazar.
     */
    val releaseKeystorePath = System.getenv("KARADAG_RELEASE_KEYSTORE").orEmpty()
    val releaseKeystorePassword = System.getenv("KARADAG_RELEASE_KEYSTORE_PASSWORD").orEmpty()
    val releaseKeyAlias = System.getenv("KARADAG_RELEASE_KEY_ALIAS").orEmpty()
    val releaseKeyPassword = System.getenv("KARADAG_RELEASE_KEY_PASSWORD").orEmpty()
    val releaseSigningAvailable =
        releaseKeystorePath.isNotBlank() &&
            file(releaseKeystorePath).isFile &&
            releaseKeystorePassword.isNotBlank() &&
            releaseKeyAlias.isNotBlank() &&
            releaseKeyPassword.isNotBlank()

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    if (releaseSigningAvailable) {
        logger.lifecycle("KaradagCarTV: release imzası YAPILANDIRILDI (alias=$releaseKeyAlias).")
    } else {
        logger.warn(
            "KaradagCarTV: release imza bilgileri YOK — release APK'ları " +
                "İMZASIZ üretilecek. KARADAG_RELEASE_KEYSTORE, " +
                "KARADAG_RELEASE_KEYSTORE_PASSWORD, KARADAG_RELEASE_KEY_ALIAS, " +
                "KARADAG_RELEASE_KEY_PASSWORD gerekli."
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig =
                if (releaseSigningAvailable) signingConfigs.getByName("release") else null
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

    androidTestImplementation("androidx.test:core-ktx:1.6.1")

    androidTestImplementation("androidx.test:runner:1.6.2")

    androidTestImplementation("androidx.test:rules:1.6.1")

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