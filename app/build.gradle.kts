plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kuromh.controller"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kuromh.controller"

        minSdk = 26
        targetSdk = 34

        versionCode = 6
        versionName = "1.3.2"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
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
    }

    composeOptions {
        // Kompatibel dengan Kotlin 1.9.0
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // AndroidX Core
    implementation(
        "androidx.core:core-ktx:1.12.0"
    )

    // Lifecycle
    implementation(
        "androidx.lifecycle:lifecycle-runtime-ktx:2.7.0"
    )

    implementation(
        "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0"
    )

    implementation(
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0"
    )

    // Activity Compose
    implementation(
        "androidx.activity:activity-compose:1.8.2"
    )

    // Compose BOM
    implementation(
        platform("androidx.compose:compose-bom:2023.10.01")
    )

    // Compose UI
    implementation(
        "androidx.compose.ui:ui"
    )

    implementation(
        "androidx.compose.ui:ui-graphics"
    )

    implementation(
        "androidx.compose.ui:ui-tooling-preview"
    )

    // Material Design 3
    implementation(
        "androidx.compose.material3:material3"
    )

    // Navigation
    implementation(
        "androidx.navigation:navigation-compose:2.7.7"
    )

    // Material Icons Extended
    implementation(
        "androidx.compose.material:material-icons-extended"
    )

    // Debug Tools
    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}
