plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")

    // Firebase için bu satırları ekle
    id("com.google.gms.google-services")
}

android {
    namespace = "com.avnikahraman.safedose"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.avnikahraman.safedose"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    viewBinding {
        enable = true
    }
}

dependencies {
    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Firebase BOM - Tüm Firebase kütüphanelerinin versiyonlarını yönetir
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth-ktx")

    // Firebase Firestore
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Google Sign In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Barcode Scanning (ML Kit)
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

    // Activity KTX
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Fragment KTX
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Glide (Image loading - ileride ilaç görselleri için)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.androidx.activity)

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}