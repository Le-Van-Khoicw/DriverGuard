import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val localFirebase = providers.gradleProperty("firebaseEmulators").orElse("false").get().toBooleanStrict()
val localFirebaseHost = providers.gradleProperty("firebaseEmulatorHost").orElse("10.0.2.2").get()
val onlineProperties = Properties().apply {
    val configFile = rootProject.file("online.properties")
    if (configFile.exists()) configFile.inputStream().use { load(it) }
}
val dispatchApiUrl = providers.gradleProperty("dispatchApiUrl").orElse(providers.environmentVariable("DISPATCH_API_URL"))
    .orElse(onlineProperties.getProperty("dispatchApiUrl", "")).get().trimEnd('/')
require(dispatchApiUrl.isEmpty() || dispatchApiUrl.matches(Regex("https://[A-Za-z0-9.-]+(:[0-9]+)?"))) { "dispatchApiUrl must be an HTTPS origin" }
require(localFirebaseHost.matches(Regex("[A-Za-z0-9.-]+"))) { "Invalid emulator host" }

android {
    namespace = "com.example.driverguard"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.driverguard"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.5"
        buildConfigField("boolean", "FIREBASE_EMULATORS", localFirebase.toString())
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$localFirebaseHost\"")
        buildConfigField("String", "DISPATCH_API_URL", "\"$dispatchApiUrl\"")
        manifestPlaceholders["firebaseInitEnabled"] = (!localFirebase).toString()
        manifestPlaceholders["driverGuardLabel"] = if (localFirebase) "DriverGuard Local" else "@string/app_name"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            if (localFirebase) applicationIdSuffix = ".local"
        }
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
        compose = true
        buildConfig = true
    }
}

val requireOnlineConfig by tasks.registering {
    doLast {
        check(localFirebase || dispatchApiUrl.isNotEmpty()) { "Configure Render URL: run Configure-Online.ps1 or pass -PdispatchApiUrl=https://...onrender.com" }
    }
}
tasks.named("preBuild").configure { dependsOn(requireOnlineConfig) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-storage")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    val cameraXVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("com.google.mediapipe:tasks-vision:0.10.29")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.compose.material:material-icons-extended")
}




