plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.terminalmodule"
    compileSdk {
        version = release(37)

        flavorDimensions += "device"
        productFlavors {
            create("pax")   { dimension = "device" }
            create("nexgo") { dimension = "device" }
        }
    }

    defaultConfig {
        applicationId = "com.example.terminalmodule"
        minSdk = 24
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
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    //implementation(files("libs/nexgo-smartpos-sdk-v3.04.001_20211014.aar"))
    "nexgoCompileOnly"(fileTree("libs/nexgo") { include("*.jar") })

}