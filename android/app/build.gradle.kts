import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing credentials come from the environment (CI) or gradle.properties (local).
// When absent the release build stays unsigned instead of failing the build.
val ksFile = System.getenv("APP_KEYSTORE") ?: (project.findProperty("app.keystore") as String?)
val ksPass = System.getenv("APP_KEYSTORE_PASSWORD") ?: (project.findProperty("app.keystore.password") as String?)
// NOTE: keep these names distinct from the SigningConfig properties, otherwise
// "keyAlias = keyAlias" resolves the right-hand side to the property itself and
// Gradle fails with: SigningConfig "release" is missing required property "keyAlias".
val aliasName = System.getenv("APP_KEY_ALIAS") ?: (project.findProperty("app.key.alias") as String?)
val aliasPassword = System.getenv("APP_KEY_PASSWORD") ?: (project.findProperty("app.key.password") as String?)
val signingReady = !ksFile.isNullOrBlank() && !ksPass.isNullOrBlank() && !aliasName.isNullOrBlank() && !aliasPassword.isNullOrBlank()

android {
    namespace = "com.senplay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.senplay.player"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("zh", "en")
    }

    signingConfigs {
        if (signingReady) {
            create("release") {
                storeFile = file(ksFile!!)
                storePassword = ksPass
                keyAlias = aliasName
                keyPassword = aliasPassword
                storeType = "PKCS12"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (signingReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
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
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
