#!/usr/bin/env bash
set -euo pipefail

# 西瓜影院轻量版 APK 构建脚本。
# 作者：gc
# 创建时间：2026-05-23 12:30:32
# 说明：本脚本使用 Android SDK 自带命令直接构建，避免为了一个老盒子 WebView 壳引入完整 Gradle 工程。

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BUILD_TOOLS="${BUILD_TOOLS:-$ANDROID_HOME/build-tools/36.1.0}"
PLATFORM="${PLATFORM:-$ANDROID_HOME/platforms/android-36.1/android.jar}"
JAVA17_HOME="${JAVA17_HOME:-/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home}"
KEYSTORE="$PROJECT_DIR/wxjsw-debug.keystore"

if [ ! -x "$BUILD_TOOLS/aapt" ] || [ ! -f "$PLATFORM" ]; then
  echo "缺少构建环境，请先安装 platforms;android-36.1 和 build-tools;36.1.0。" >&2
  exit 1
fi

rm -rf "$PROJECT_DIR/build"
mkdir -p "$PROJECT_DIR/build/gen" "$PROJECT_DIR/build/classes" "$PROJECT_DIR/build/dex" "$PROJECT_DIR/build/outputs"

# 生成 R.java。旧版 aapt 需要 Manifest 中显式声明 package，因此 Manifest 保留了包名属性。
"$BUILD_TOOLS/aapt" package \
  -f \
  -m \
  -J "$PROJECT_DIR/build/gen" \
  -M "$PROJECT_DIR/app/src/main/AndroidManifest.xml" \
  -S "$PROJECT_DIR/app/src/main/res" \
  -I "$PLATFORM"

# 使用 Java 7 字节码目标，保持电视盒子系统运行时兼容性。
javac \
  -source 1.7 \
  -target 1.7 \
  -bootclasspath "$PLATFORM" \
  -classpath "$PROJECT_DIR/build/gen" \
  -d "$PROJECT_DIR/build/classes" \
  $(find "$PROJECT_DIR/app/src/main/java" "$PROJECT_DIR/build/gen" -name '*.java')

# D8 需要 Java 11+ 运行；本机默认可能是 Java 8，所以这里显式把 Java 17 放到 PATH 前面。
PATH="$JAVA17_HOME/bin:$PATH" "$BUILD_TOOLS/d8" \
  --min-api 16 \
  --lib "$PLATFORM" \
  --output "$PROJECT_DIR/build/dex" \
  $(find "$PROJECT_DIR/build/classes" -name '*.class')

"$BUILD_TOOLS/aapt" package \
  -f \
  -M "$PROJECT_DIR/app/src/main/AndroidManifest.xml" \
  -S "$PROJECT_DIR/app/src/main/res" \
  -I "$PLATFORM" \
  -F "$PROJECT_DIR/build/outputs/wxjsw-tv-unsigned.apk" \
  "$PROJECT_DIR/build/dex"

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias wxjswdebug \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=gc, OU=dev, O=gc, L=Shanghai, ST=Shanghai, C=CN"
fi

"$BUILD_TOOLS/zipalign" \
  -f \
  4 \
  "$PROJECT_DIR/build/outputs/wxjsw-tv-unsigned.apk" \
  "$PROJECT_DIR/build/outputs/wxjsw-tv-aligned.apk"

PATH="$JAVA17_HOME/bin:$PATH" "$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --ks-key-alias wxjswdebug \
  --out "$PROJECT_DIR/build/outputs/wxjsw-tv-shell.apk" \
  "$PROJECT_DIR/build/outputs/wxjsw-tv-aligned.apk"

PATH="$JAVA17_HOME/bin:$PATH" "$BUILD_TOOLS/apksigner" verify \
  --verbose \
  "$PROJECT_DIR/build/outputs/wxjsw-tv-shell.apk"

echo "APK 已生成：$PROJECT_DIR/build/outputs/wxjsw-tv-shell.apk"
