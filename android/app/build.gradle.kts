import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing is opt-in and comes from either android/keystore.properties (gitignored,
// for local builds) or BACKCHANNEL_KEYSTORE* environment variables (for CI secrets).
// With neither, release builds stay unsigned.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use(::load)
}

fun signingSetting(propertyKey: String, envKey: String): String? =
    keystoreProps.getProperty(propertyKey) ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingSetting("storeFile", "BACKCHANNEL_KEYSTORE")

// CI tags drive the version; local builds fall back to the values checked in here.
val buildVersionName = System.getenv("BACKCHANNEL_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "0.3.0"
val buildVersionCode = System.getenv("BACKCHANNEL_VERSION_CODE")?.toIntOrNull() ?: 3

android {
    namespace = "com.zkwokleung.backchannel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zkwokleung.backchannel"
        minSdk = 26
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingSetting("storePassword", "BACKCHANNEL_KEYSTORE_PASSWORD")
                keyAlias = signingSetting("keyAlias", "BACKCHANNEL_KEY_ALIAS")
                keyPassword = signingSetting("keyPassword", "BACKCHANNEL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
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
    packaging {
        jniLibs {
            useLegacyPackaging = true
            // youtubedl-android ships its Python/ffmpeg payloads as .so-named zips; the NDK
            // strip step can't parse them and must leave them untouched.
            keepDebugSymbols += listOf(
                "**/libpython.zip.so",
                "**/libffmpeg.zip.so",
                "**/libaria2c.zip.so",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.youtubedl.library)

    testImplementation(libs.junit)
}
