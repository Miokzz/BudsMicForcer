plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.miokzz.budsmicforcer"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.miokzz.budsmicforcer"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-test"
    }
}
