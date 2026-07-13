plugins {
    id("com.android.application")
}

android {
    namespace = "app.brainy.personal"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.brainy.personal"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
