import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tishan.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tishan.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningPropertiesFile.isFile) {
            create("release") {
                storeFile = rootProject.file(
                    releaseSigningProperties.getProperty("storeFile")
                        ?: error("keystore.properties 缺少 storeFile"),
                )
                storePassword = releaseSigningProperties.getProperty("storePassword")
                    ?: error("keystore.properties 缺少 storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                    ?: error("keystore.properties 缺少 keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
                    ?: error("keystore.properties 缺少 keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)

    // Room is part of the approved storage architecture. v1 entities and DAOs live in data/local.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    kapt(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}
