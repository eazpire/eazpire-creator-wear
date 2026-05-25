import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

// CI: VERSION_CODE via env or -PVERSION_CODE (lazy — avoids configuration-cache sticking at 1).
val ciVersionCode = providers.gradleProperty("VERSION_CODE")
    .orElse(providers.environmentVariable("VERSION_CODE"))

android {
    namespace = "com.eazpire.creator.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eazpire.creator.wear"
        minSdk = 30
        targetSdk = 35
        versionCode = ciVersionCode.map { raw ->
            val n = raw.toLongOrNull()
                ?: error("Invalid VERSION_CODE: $raw")
            require(n in 1..2_100_000_000L) {
                "VERSION_CODE must be 1..2100000000 (Google Play max), got $n"
            }
            n.toInt()
        }.orElse(1).get()
        versionName = providers.environmentVariable("VERSION_NAME")
            .orElse(ciVersionCode.map { "1.0.0 ($it)" })
            .orElse("1.0.0")
            .get()
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile")!!)
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            ndk {
                debugSymbolLevel = "FULL"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}

/** CI: same resolution as defaultConfig.versionCode (see google-play.yml Verify step). */
tasks.register("printReleaseVersionCode") {
    doLast {
        val code = ciVersionCode.map { it.toInt() }.orElse(1).get()
        println("VERSION_CODE_OUT=$code")
    }
}

dependencies {
    implementation(project(":creator-core"))

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")
    // AmbientLifecycleObserver (androidx.wear.ambient.*)
    implementation("androidx.wear:wear:1.4.0")

    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    testImplementation("junit:junit:4.13.2")
}
