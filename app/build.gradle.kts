plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.mcgg.fasthand"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mcgg.fasthand"
        minSdk = 26
        targetSdk = 29
        versionCode = 2
        versionName = "0.2.0"
    }

    buildFeatures { buildConfig = true }

    flavorDimensions += "edition"
    productFlavors {
        create("lite") {
            dimension = "edition"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "ENABLE_FULL_FEATURES", "false")
        }
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "ENABLE_FULL_FEATURES", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.squareup.picasso:picasso:2.8")
}
