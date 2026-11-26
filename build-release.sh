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
    -Dorg.gradle.jvmargs="-Xmx2560m -XX:MaxMetaspaceSize=768m" \
    --max-workers=2 --no-parallel "$@"

APK=app/build/outputs/apk/armsolo/release/app-armsolo-release.apk
OUT="$(dirname "$0")/../Download/VantaCode-release-$(date +%m%d-%H%M).apk"
mkdir -p "$(dirname "$OUT")"
cp "$APK" "$OUT"
echo "产物: $OUT"

# 把本次 versionCode 落盘进 version-code.txt（防 squash 后提交数回退），
# 发版时务必把更新后的 version-code.txt 一起提交，否则下个版本 versionCode 不涨、
# 用户无法覆盖安装。注意：CI 打 tag 的构建不跑这一步，见 android-release.yml。
sh ./gradlew persistVersionCode --console=plain \
    -Dkotlin.compiler.execution.strategy=in-process \
    -Dorg.gradle.jvmargs="-Xmx2560m -XX:MaxMetaspaceSize=768m" \
    --max-workers=2 --no-parallel
echo "version-code.txt 已更新为: $(cat version-code.txt)"
