plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.worldlineintegrationsdk"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.worldlineintegrationsdk"
        minSdk = 25
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

// Fix:
// Duplicate class com.google.common.util.concurrent.ListenableFuture
configurations.all {
    exclude(
        group = "com.google.guava",
        module = "listenablefuture"
    )
}

dependencies {

    // --------------------------------------------------
    // Jetpack Compose
    // --------------------------------------------------

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.compose.ui)

    implementation(libs.androidx.compose.ui.graphics)

    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.junit.ktx)


    // --------------------------------------------------
    // Tests
    // --------------------------------------------------

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // WorkManager (HeartbeatWorker, LogUploadWorker)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.concurrent:concurrent-futures:1.1.0")
    implementation("com.google.guava:listenablefuture:1.0")
    // --------------------------------------------------
    // Worldline Validation SDK
    // --------------------------------------------------

    implementation(
        files("lib/validation-sdk-release_1.0.7_20260730.aar")
    )


    // --------------------------------------------------
    // Room Database
    // --------------------------------------------------

    val roomVersion = "2.8.4"

    implementation(
        "androidx.room:room-runtime:$roomVersion"
    )

    implementation(
        "androidx.room:room-ktx:$roomVersion"
    )

    // Required to generate AppDatabase_Impl
    ksp(
        "androidx.room:room-compiler:$roomVersion"
    )
}