plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.studytimer"
    // 编译使用的 SDK 版本（API 35 = Android 15）
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.studytimer"
        minSdk = 26
        // 目标 SDK 版本（不超过 compileSdk）
        targetSdk = 35
        versionCode = 4
        versionName = "1.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}