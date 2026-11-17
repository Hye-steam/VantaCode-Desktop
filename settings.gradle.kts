pluginManagement {
    repositories {
        // 容器内 dl.google.com 不可达，走腾讯云聚合镜像（含 Google Maven 与插件门户）
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        google()
        mavenCentral()
    }
}

rootProject.name = "ai-gateway"

include(":core")
include(":app")
