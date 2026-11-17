#!/bin/sh
# 用 kotlinc 直接编译 core 并打可执行 jar（无需 Gradle）。
# 用法：./build.sh          # 编译 + 打包
#       java -jar aigw.jar serve --data ~/.aigw
set -e
cd "$(dirname "$0")"

KOTLINC=${KOTLINC:-$HOME/kotlinc/kotlinc/bin/kotlinc}
LIBS=libs
OUT=build/core

mkdir -p "$OUT"

# 收集 core 源码
SOURCES=$(find core/src -name '*.kt')

"$KOTLINC" -classpath "$LIBS/gson.jar:$LIBS/nanohttpd.jar" -d "$OUT" $SOURCES

# 打包可执行 jar（Manifest 指定 Main-Class）
cat > "$OUT/MANIFEST.MF" <<EOF
Manifest-Version: 1.0
Main-Class: dev.aigw.MainKt
Class-Path: libs/gson.jar libs/kotlin-stdlib.jar libs/nanohttpd.jar
EOF

jar cfm aigw.jar "$OUT/MANIFEST.MF" -C "$OUT" .
echo "OK: $(ls -lh aigw.jar | awk '{print $5, $9}')"
