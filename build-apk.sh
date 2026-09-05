#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_TOOLS_DIR="${ANDROID_TOOLS_DIR:-$PROJECT_DIR/../android-tools}"
BUILD_TOOLS_DIR="$ANDROID_TOOLS_DIR/build-tools/35.0.0"
ANDROID_JAR="$ANDROID_TOOLS_DIR/platforms/android-35/android.jar"

mkdir -p "$PROJECT_DIR/build/compiled" "$PROJECT_DIR/build/gen" \
  "$PROJECT_DIR/build/classes" "$PROJECT_DIR/build/dex" "$PROJECT_DIR/build/out"

"$BUILD_TOOLS_DIR/aapt2" compile \
  --dir "$PROJECT_DIR/res" \
  -o "$PROJECT_DIR/build/compiled/resources.zip"

"$BUILD_TOOLS_DIR/aapt2" link \
  -o "$PROJECT_DIR/build/out/VioletBank-unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$PROJECT_DIR/AndroidManifest.xml" \
  --min-sdk-version 26 \
  --target-sdk-version 35 \
  --version-code 2 \
  --version-name 1.1 \
  --java "$PROJECT_DIR/build/gen" \
  "$PROJECT_DIR/build/compiled/resources.zip"

mapfile -t JAVA_FILES < <(find "$PROJECT_DIR/build/gen" "$PROJECT_DIR/src" -name '*.java' -print)
java com.sun.tools.javac.Main \
  -encoding UTF-8 -source 8 -target 8 \
  -classpath "$ANDROID_JAR" \
  -d "$PROJECT_DIR/build/classes" \
  "${JAVA_FILES[@]}"

mapfile -t CLASS_FILES < <(find "$PROJECT_DIR/build/classes" -name '*.class' -print)
"$BUILD_TOOLS_DIR/d8" \
  --lib "$ANDROID_JAR" \
  --output "$PROJECT_DIR/build/dex" \
  "${CLASS_FILES[@]}"

(cd "$PROJECT_DIR/build/dex" && zip -q -u "$PROJECT_DIR/build/out/VioletBank-unsigned.apk" classes.dex)

KEYSTORE="$PROJECT_DIR/build/violet-demo.keystore"
if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass violetdemo \
    -keypass violetdemo \
    -alias violet \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Violet Demo, O=Violet Bank, C=RU"
fi

"$BUILD_TOOLS_DIR/zipalign" -f 4 \
  "$PROJECT_DIR/build/out/VioletBank-unsigned.apk" \
  "$PROJECT_DIR/build/out/VioletBank-aligned.apk"

"$BUILD_TOOLS_DIR/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:violetdemo \
  --key-pass pass:violetdemo \
  --ks-key-alias violet \
  --out "$PROJECT_DIR/build/out/VioletBank.apk" \
  "$PROJECT_DIR/build/out/VioletBank-aligned.apk"

"$BUILD_TOOLS_DIR/apksigner" verify --verbose "$PROJECT_DIR/build/out/VioletBank.apk"
echo "APK: $PROJECT_DIR/build/out/VioletBank.apk"
