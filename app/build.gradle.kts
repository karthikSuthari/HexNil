plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.hexnil"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.hexnil"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Appwrite configuration (no client-side key)
        buildConfigField("String", "APPWRITE_ENDPOINT", "\"https://sgp.cloud.appwrite.io/v1\"")
        buildConfigField("String", "APPWRITE_PROJECT_ID", "\"696459cc001abc691815\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    
    // Room Database
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    
    // WorkManager for background tasks
    implementation(libs.workmanager)
    
    // Lifecycle components
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Appwrite backend
    implementation(libs.appwrite)
    
    // JSON parsing
    implementation(libs.gson)
    
    // Charts for visualization
    implementation(libs.mpandroidchart)
    
    // UI components
    implementation(libs.cardview)
    implementation(libs.recyclerview)
    implementation(libs.viewpager2)
    implementation(libs.preference)
    
    // Report generation
    implementation(libs.itext)
    implementation(libs.opencsv)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}