plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

ksp {
    // Keep Room schema snapshots in source control so future migrations can be reviewed.
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "in.bhajan.bfpbusinesspro"
    compileSdk = 35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    defaultConfig {
        applicationId = "in.bhajan.bfpbusinesspro"
        minSdk = 24
        targetSdk = 35
        versionCode = 552
        versionName = "5.52.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Stable signing is supplied by GitHub Actions secrets. Never commit the keystore.
    val signingStoreFile = System.getenv("BFP_KEYSTORE_FILE")
    val signingStorePassword = System.getenv("BFP_KEYSTORE_PASSWORD")
    val signingKeyAlias = System.getenv("BFP_KEY_ALIAS")
    val signingKeyPassword = System.getenv("BFP_KEY_PASSWORD")
    val hasStableSigning = !signingStoreFile.isNullOrBlank() &&
        !signingStorePassword.isNullOrBlank() &&
        !signingKeyAlias.isNullOrBlank() &&
        !signingKeyPassword.isNullOrBlank()

    signingConfigs {
        if (hasStableSigning) {
            create("stable") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasStableSigning) signingConfig = signingConfigs.getByName("stable")
        }
        getByName("release") {
            if (hasStableSigning) signingConfig = signingConfigs.getByName("stable")
            isMinifyEnabled = false
        }
    }

    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    testImplementation("junit:junit:4.13.2")
}
