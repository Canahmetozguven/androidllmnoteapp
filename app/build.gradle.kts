import java.util.Properties
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.Calendar

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.googleServices)
}

// Auto-generated Version Code: Minute-based logic to fit Integer.MAX_VALUE
// Strategy: 2026000000 + Minutes_Since_Start_Of_2026
val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
val year = 2026
val startOfYear = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
startOfYear.set(year, 0, 1, 0, 0, 0) // Jan 1, 2026 00:00:00

val diffMillis = calendar.timeInMillis - startOfYear.timeInMillis
val minutesSinceStart = (diffMillis / 60000).toInt()

val baseVersion = 2026000000
val autoVersionCode = baseVersion + minutesSinceStart

android {
    namespace = "com.synapsenotes.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.synapsenotes.ai"
        minSdk = 26
        targetSdk = 35
        
        // Load API Key from local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }
        val googleDriveApiKey = localProperties.getProperty("GOOGLE_DRIVE_API_KEY") ?: ""
        buildConfigField("String", "GOOGLE_DRIVE_API_KEY", "\"$googleDriveApiKey\"")

        versionCode = autoVersionCode
        versionName = "1.9.7"

        ndkVersion = "26.1.10909125"


        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val props = Properties()
                props.load(FileInputStream(keystorePropertiesFile))
                storeFile = file(props["storeFile"] as String)
                storePassword = props["storePassword"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPassword"] as String
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        externalNativeBuild {
            cmake {
                // FORCE VULKAN: Always enable Vulkan for GPU acceleration on supported devices
                // We default to ON to prevent CPU-only fallbacks that crash on large models (S22 OOM)
                val useVulkan = project.findProperty("useVulkan") != "false" 
                if (useVulkan) {
                    arguments += listOf(
                        "-DGGML_VULKAN=ON", "-DVulkan_FOUND=ON", "-DLLAMA_VULKAN=ON"
                    )
                } else {
                    arguments += listOf(
                        "-DGGML_VULKAN=OFF", "-DVulkan_FOUND=OFF", "-DLLAMA_VULKAN=OFF"
                    )
                }

                // ENABLE OPENCL: Secondary backend for Adreno/Mali
                val useOpenCL = project.findProperty("useOpenCL") != "false"
                if (useOpenCL) {
                    arguments += listOf(
                        "-DGGML_OPENCL=ON", "-DGGML_OPENCL_USE_ADRENO_KERNELS=ON", "-DGGML_OPENCL_EMBED_KERNELS=ON"
                    )
                } else {
                    arguments += listOf(
                        "-DGGML_OPENCL=OFF"
                    )
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8" // Matches Kotlin 1.9.22 roughly, or check mapping. 
        // With KSP and newer Kotlin, we might not need this if using the new compose compiler plugin, 
        // but for safety with standard setup:
        // Kotlin 1.9.22 -> Compose Compiler 1.5.8
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Network
    implementation(libs.okhttp)
    implementation(libs.retrofit)

    // Markdown
    implementation(libs.compose.markdown)

    // Google Drive
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.guava)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("io.mockk:mockk:1.13.9")
    
    // JUnit 5
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine) // For JUnit 4 support
    
    // Mockito
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    
    // Turbine
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

tasks.register("ciTest") {
    description = "Runs all unit and instrumented tests for CI"
    group = "verification"
    
    // Run unit tests
    dependsOn("testDebugUnitTest")
    
    // Run instrumented tests (requires emulator/device)
    // defined in a way that doesn't fail configuration if task doesn't exist (though it should)
    dependsOn("connectedDebugAndroidTest")
}
