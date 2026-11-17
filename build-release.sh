#!/bin/sh
# 正式版构建：com.vantacode + 纯净包，出包后只需分发这一个 APK。
#
# 与测试版的区别只有 -PtestBuild=false：包名、应用名、版本名都跟着切换。
set -e
cd "$(dirname "$0")"

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}
export ANDROID_HOME=${ANDROID_HOME:-/root/android/sdk}

sh ./gradlew assembleArmsoloRelease --console=plain \
    -PtestBuild=false \
    -Dkotlin.compiler.execution.strategy=in-process \
    -Dorg.gradle.jvmargs=-Xmx3g "$@"

APK=app/build/outputs/apk/armsolo/release/app-armsolo-release.apk
OUT="$(dirname "$0")/../Download/VantaCode-release-$(date +%m%d-%H%M).apk"
mkdir -p "$(dirname "$OUT")"
cp "$APK" "$OUT"
echo "产物: $OUT"
