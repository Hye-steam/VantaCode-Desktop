import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.aigw.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "dev.aigw.app10"
        minSdk = 26
        targetSdk = 35
        versionCode = 999999
        versionName = "2.3"
    }

    signingConfigs {
        create("vanta") {
            storeFile = file("/root/workspace/work/vanta.keystore")
            storePassword = "vanta123"
            keyAlias = "vanta"
            keyPassword = "vanta123"
        }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("vanta")
            // 不开 R8：调试构建保持可读，且省掉一遍内存吃紧的混淆任务
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            // 开 R8：debug 包里 classes.dex 有 45MB，其中大半是 material-icons-extended
            // 里没用到的几千个图标。混淆只保留实际被引用的，配合 -keep 规则保护反射路径。
            signingConfig = signingConfigs.getByName("vanta")
            isMinifyEnabled = true
            isShrinkResources = true
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    // :core 用 implementation 引 gson/nanohttpd，不传递；app 里直接使用需要
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
