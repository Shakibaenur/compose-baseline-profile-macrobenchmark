plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.llmbench"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.llmbench"
        minSdk = 26          // MediaPipe tasks-genai requires API 24+; 26 keeps the target honest
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
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
}

dependencies {
    // MediaPipe LLM Inference API. Maintenance-only per Google's docs; LiteRT-LM is the successor.
    implementation("com.google.mediapipe:tasks-genai:0.10.35")
}
