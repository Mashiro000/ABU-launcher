plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.limi.tvdesktop"
    compileSdk = 36
    compileSdkMinor = 1
    defaultConfig {
        applicationId = "com.limi.tvdesktop"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "0.02"
    }
    buildFeatures { compose = true }
    buildTypes {
        release {
            // R8：代码压缩 / 混淆 / 优化
            isMinifyEnabled = true
            // 移除未被引用的资源（必须在 isMinifyEnabled = true 时才有意义）
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.ui:ui:1.10.4")
    implementation("androidx.compose.foundation:foundation:1.10.4")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("dev.chrisbanes.haze:haze:1.7.2")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("dev.jdtech.mpv:libmpv:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
