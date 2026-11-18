import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 只用 JDK 自带 API（HttpServer + HttpURLConnection）与纯 Java 库，
    // 使 :core 既能本机 JVM 直接运行/测试，也能原样打进 Android 应用。
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // 视频通道要绕开 JSSE 的 TLS 指纹（字节语义风控只拦 Java 默认指纹）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
