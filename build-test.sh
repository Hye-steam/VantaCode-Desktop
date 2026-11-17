#!/bin/sh
# 测试版构建：一条命令出可共存的 com.vantacode.test 包。
#
# 关掉独立 Kotlin 守护进程：本机 7.6G 内存下，Gradle 守护进程 + Kotlin 守护进程
# 同时驻留容易把 App 挤掉（构建随之被 SIGKILL），单 JVM 反而更稳。
set -e
cd "$(dirname "$0")"

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}
export ANDROID_HOME=${ANDROID_HOME:-/root/android/sdk}

sh ./gradlew assembleArmsoloRelease --console=plain \
    -Dkotlin.compiler.execution.strategy=in-process \
    -Dorg.gradle.jvmargs=-Xmx3g "$@"

APK=app/build/outputs/apk/armsolo/release/app-armsolo-release.apk
OUT="$(dirname "$0")/../Download/VantaCode-test-$(date +%m%d-%H%M).apk"
mkdir -p "$(dirname "$OUT")"
cp "$APK" "$OUT"
echo "产物: $OUT"
