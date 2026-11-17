plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ks: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.pj.signals"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.pj.signals"
        minSdk = 24
        targetSdk = 35
        val code = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionCode = code
        versionName = "1.$code"
        buildConfigField("String", "SITE_URL", "\"https://pj-signals.vercel.app\"")   // the one place the site URL lives
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        ks?.let { path -> create("release") {
            storeFile = file(path)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEYSTORE_PASSWORD")
            storeType = "pkcs12"
        } }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (ks != null) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
