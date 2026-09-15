import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// کلید امضای ثابت. بدون این، هر بار که اپ ساخته می‌شود با کلید تصادفی امضا می‌شد
// و نسخهٔ جدید به‌جای «آپدیت»، نیازمند حذف و نصب دوباره بود.
val signingKeyB64 = "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"

val signingKeyFile: File = layout.buildDirectory.get().asFile
    .resolve("payamban.keystore")
    .apply {
        parentFile.mkdirs()
        writeBytes(Base64.getDecoder().decode(signingKeyB64))
    }

android {
    namespace = "ir.payamban.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.payamban.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        create("payamban") {
            storeFile = signingKeyFile
            storePassword = "payamban"
            keyAlias = "payamban"
            keyPassword = "payamban"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("payamban")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("payamban")
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
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
