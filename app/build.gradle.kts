import java.util.Properties

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

// Baca local.properties dengan cara yang benar
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.sndiy.chatfin"
    compileSdk = 35

    defaultConfig {
        applicationId   = "com.sndiy.chatfin"
        minSdk          = 26
        targetSdk       = 35
        versionCode     = 1
        versionName     = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProps.getProperty("GEMINI_API_KEY", "")}\""
        )
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

    implementation(libs.compose.markdown)

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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.compose.ui.test.junit4)

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.code.gson:gson:2.10.1")
}