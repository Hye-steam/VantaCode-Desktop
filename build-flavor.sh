#!/bin/sh
# 按用途切换并构建 VantaCode
#
#   sh build-flavor.sh self    自用版：带上 ~/vantacode-private/seed（你的配置）
#   sh build-flavor.sh public  外发版：不含任何个人数据
#
# 两个版本共用同一份源码，区别只在 app/src/main/assets/seed 这一层目录：
# 自用构建时从仓库外的私有目录复制进来，外发构建时移除。seed 已在 .gitignore 里，
# 不会进版本库，也不会污染外发版。
set -e
cd "$(dirname "$0")"

FLAVOR="${1:-public}"
SEED_SRC="$HOME/vantacode-private/seed"
SEED_DST="app/src/main/assets/seed"

case "$FLAVOR" in
  self)
    if [ ! -d "$SEED_SRC" ]; then
      echo "缺少私有种子目录：$SEED_SRC" >&2
      exit 1
    fi
    rm -rf "$SEED_DST"
    cp -r "$SEED_SRC" "$SEED_DST"
    echo "自用版：已注入种子（$(du -sh "$SEED_DST" | cut -f1)）"
    ;;
  public)
    rm -rf "$SEED_DST"
    echo "外发版：不带任何种子"
    ;;
  *)
    echo "用法：sh build-flavor.sh self|public" >&2
    exit 1
    ;;
esac

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=/root/android/sdk

sh ./gradlew assembleArmsoloRelease --console=plain

VERSION=$(grep -m1 'versionName = ' app/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
OUT="$HOME/workspace/Download/VantaCode-${VERSION}-${FLAVOR}.apk"
cp app/build/outputs/apk/armsolo/release/app-armsolo-release.apk "$OUT"
# 清掉同 flavor 的旧包，只留本次产物（宿主明确要求：旧版本不占空间）
for old in "$HOME/workspace/Download"/VantaCode-*-"$FLAVOR".apk; do
  [ -f "$old" ] || continue
  [ "$old" = "$OUT" ] && continue
  rm -f "$old"
done

echo "已输出：$OUT"
