plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.sndiy.chatfin"
    compileSdk = 36

    defaultConfig {
        applicationId   = "com.sndiy.chatfin"
        minSdk          = 26
        // targetSdk 36 (Android 16) wajib untuk update aplikasi di Play Store —
        // dengan 35, Play menolak upload/update APK apa pun.
        targetSdk       = 36
        versionCode     = 7
        versionName     = "2.8.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
//            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-DEBUG"
        }
    }

    compileOptions {
        sourceCompatibility            = JavaVersion.VERSION_17
        targetCompatibility            = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xlint:-deprecation"))
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // mockk (androidTest) menyeret dependency transitif JUnit Jupiter
            // yang membawa file lisensi duplikat — proyek ini tetap pakai
            // JUnit4 sebagai runner, jupiter-nya tidak pernah dipakai langsung.
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }

    sourceSets {
        // MigrationTest butuh file schema JSON yang di-export Room
        // (app/schemas/) sebagai test asset untuk membangun database versi
        // lama yang persis sesuai riwayat nyata, lalu memvalidasi hasil
        // migrasi terhadap schema versi terbaru.
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)

    implementation(libs.generativeai)

    implementation(libs.vico.compose)

    implementation(libs.coroutines.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.guava)

    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.mockk.android)
}