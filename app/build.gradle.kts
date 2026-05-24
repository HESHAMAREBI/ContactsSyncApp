plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "1.9.23-1.0.19"
}

// Kotlin stdlib version is constrained declaratively in `settings.gradle.kts`.
// As a practical and reliable interim measure ensure the compiler/classpath used by KSP
// resolves kotlin-stdlib to the Kotlin version the project compiles with.
configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:1.9.23",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.23",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.23"
        )
    }
}

android {
    namespace = "com.example.contactssyncapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.contactssyncapp"
        minSdk = 24
        targetSdk = 36
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
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    lint {
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Google Sign-In
    implementation(libs.google.play.services.auth)

    // Google Sheets API
    // Use centralized version catalog entries for Google APIs
    implementation(libs.google.api.client.android)
    implementation(libs.google.apis.sheets)
    implementation(libs.google.oauth.client.jetty)

    // WorkManager for background tasks
    implementation(libs.androidx.work)
    // WorkManager for background tasks
    implementation(libs.google.auth.library.oauth2.http)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)


    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}