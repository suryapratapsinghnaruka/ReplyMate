plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.replymate.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.replymate.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Model file is large (1.5-4 GB) - don't let Android Studio try to compress it
    androidResources {
        noCompress += "task"
        noCompress += "bin"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // MediaPipe LLM Inference API - on-device Gemma/Phi/Falcon/StableLM runner
    implementation("com.google.mediapipe:tasks-genai:0.10.14")

    // Coroutines for background LLM calls
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
