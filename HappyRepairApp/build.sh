#!/bin/bash
# Builds HappyRepair-Admin.apk and HappyRepair-Mechanic.apk
# Requires: ANDROID_SDK env var pointing at an SDK with build-tools 34.0.0
# and platforms/android-34, plus a JDK (javac/keytool) and python3+Pillow.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK:?set ANDROID_SDK to your Android SDK root}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT="$HERE/apk"
BUILD="${BUILD_DIR:-$HERE/build}"
KEYSTORE="$HERE/happyrepair.keystore"
STOREPASS=happyrepair123

mkdir -p "$OUT" "$BUILD"

# One keystore for both apps — reused on every build so updates install over
# the previous version instead of demanding an uninstall.
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias happyrepair \
    -keyalg RSA -keysize 2048 -validity 3650 \
    -storepass "$STOREPASS" -keypass "$STOREPASS" \
    -dname "CN=HappyRepairApp, O=HAPPY ENTERPRISE"
fi

build_one() {
  local role="$1" pkg="$2" label="$3" letter="$4" color="$5" apkname="$6"
  local W="$BUILD/$role"
  rm -rf "$W"
  mkdir -p "$W/assets" "$W/res/mipmap-xxhdpi" "$W/src" "$W/gen" "$W/classes" "$W/dex"

  echo "=== Building $label ($pkg) ==="

  # 1. Role-specific web app + bundled offline Excel engine
  sed "s/__ROLE__/$role/" "$HERE/app-template.html" > "$W/assets/index.html"
  cp "$HERE/xlsx.full.min.js" "$W/assets/xlsx.full.min.js"

  # 2. Launcher icon
  python3 "$HERE/make_icon.py" "$W/res/mipmap-xxhdpi/ic_launcher.png" "$letter" "$color"

  # 3. Manifest + Java source with package baked in
  sed -e "s/__PKG__/$pkg/" -e "s/__LABEL__/$label/" \
    "$HERE/android/AndroidManifest.xml.tmpl" > "$W/AndroidManifest.xml"
  sed "s/__PKG__/$pkg/" "$HERE/android/MainActivity.java.tmpl" > "$W/src/MainActivity.java"

  # 4. Resources → base APK (assets included at link time)
  "$BT/aapt2" compile --dir "$W/res" -o "$W/res.zip"
  "$BT/aapt2" link -o "$W/app.unaligned.apk" \
    -I "$PLATFORM" \
    --manifest "$W/AndroidManifest.xml" \
    -A "$W/assets" \
    --min-sdk-version 24 --target-sdk-version 34 \
    --java "$W/gen" \
    "$W/res.zip"

  # 5. javac → d8 → classes.dex
  javac --release 11 -classpath "$PLATFORM" -d "$W/classes" \
    "$W/src/MainActivity.java" "$W/gen/${pkg//.//}/R.java"
  find "$W/classes" -name '*.class' -print0 | xargs -0 \
    "$BT/d8" --release --lib "$PLATFORM" --min-api 24 --output "$W/dex"
  (cd "$W/dex" && zip -q -j "$W/app.unaligned.apk" classes.dex)

  # 6. Align + sign
  "$BT/zipalign" -f 4 "$W/app.unaligned.apk" "$W/app.aligned.apk"
  "$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:$STOREPASS \
    --out "$OUT/$apkname" "$W/app.aligned.apk"
  "$BT/apksigner" verify "$OUT/$apkname"
  echo "OK  → $OUT/$apkname"
}

build_one admin    com.happyenterprise.repairapp.admin    "HappyRepair Admin"    A "#0f172a" HappyRepair-Admin.apk
build_one mechanic com.happyenterprise.repairapp.mechanic "HappyRepair Mechanic" M "#d97706" HappyRepair-Mechanic.apk

echo "Done. APKs in $OUT/"
