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
        minSdk = 25
        targetSdk = 35
        versionCode = 5
        versionName = "0.04-beta.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    buildFeatures { compose = true }
    packaging {
        jniLibs {
            // libmpv/FFmpeg are delivered by the optional MPV player plugin.
            excludes += setOf(
                "**/libavcodec.so",
                "**/libavdevice.so",
                "**/libavfilter.so",
                "**/libavformat.so",
                "**/libavutil.so",
                "**/libmpv.so",
                "**/libplayer.so",
                "**/libswresample.so",
                "**/libswscale.so",
            )
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time / java.nio.file are used by the media and plugin layers.
        // Desugar them so those paths remain available on Android 7.1 (API 25).
        isCoreLibraryDesugaringEnabled = true
    }
}
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.10.4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.4")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.ui:ui:1.10.4")
    implementation("androidx.compose.foundation:foundation:1.10.4")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("dev.chrisbanes.haze:haze:1.7.2")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("dev.jdtech.mpv:libmpv:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.i2p.crypto:eddsa:0.3.0")
    implementation("app.cash.quickjs:quickjs-android:0.9.2")
}
