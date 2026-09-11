#!/usr/bin/env bash
# Builds quotation-sideload.apk from the web app using only open-source tools:
#   aapt, apksigner, zipalign, dalvik-exchange (dx)  — Ubuntu/Debian:
#     sudo apt-get install aapt apksigner zipalign dalvik-exchange
#   plus a JDK, and the Android API stub jar from Maven Central.
#
# Usage: ./build.sh            (from the android-quote/ directory)
# Output: ../dist/quotation-sideload.apk
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:-build/android-stub.jar}"
DX="$(command -v dalvik-exchange || command -v dx)"
KEYSTORE="${KEYSTORE:-build/quote.keystore}"
KS_PASS="${KS_PASS:-quote-dev}"

rm -rf build/gen build/obj build/apk assets
mkdir -p build/gen build/obj ../dist

# The Android API stub jar (compile-time only, never shipped).
if [ ! -f "$ANDROID_JAR" ]; then
  curl -sSL -o "$ANDROID_JAR" \
    "https://repo1.maven.org/maven2/com/google/android/android/4.1.1.4/android-4.1.1.4.jar"
fi

# Bundle the web app into assets/www (a single self-contained page).
mkdir -p assets/www
cp ../quote.html assets/www/

# Resources -> R.java -> bytecode -> dex.
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I "$ANDROID_JAR"
javac --release 8 -classpath "$ANDROID_JAR" -d build/obj \
  build/gen/com/beyondexplain/quote/R.java \
  src/com/beyondexplain/quote/MainActivity.java
"$DX" --dex --output=build/classes.dex build/obj

# Package, add dex, align, sign.
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$ANDROID_JAR" \
  -F build/app.unsigned.apk
(cd build && aapt add app.unsigned.apk classes.dex)
zipalign -f 4 build/app.unsigned.apk build/app.aligned.apk

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" \
    -keypass "$KS_PASS" -alias quote -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Work Quotation Calculator"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KS_PASS" --out ../dist/quotation-sideload.apk build/app.aligned.apk

apksigner verify --print-certs ../dist/quotation-sideload.apk | head -3
echo "Built ../dist/quotation-sideload.apk"
