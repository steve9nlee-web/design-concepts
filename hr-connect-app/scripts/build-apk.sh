#!/usr/bin/env bash
# Builds hr-connect.apk WITHOUT the Android SDK, using:
#   - apktool (binary AndroidManifest.xml + packaging, bundled aapt/framework)
#   - dalvik-dx (Maven Central) to dex javac output
#   - Robolectric android-all jar (Maven Central) as the compile classpath
#   - apksig (Maven Central) for APK Signature Scheme v2 signing
#
# Prereqs (see repo README): /opt/android-tools with apktool.jar, dx.jar,
# apksig.jar, android-all.jar, signer/Sign.class, signer/Verify.class and a
# PKCS12 keystore. If you have Android Studio, just open hr-connect-app/
# instead and build normally.
set -euo pipefail

TOOLS="${TOOLS:-/opt/android-tools}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/app/src/main"
OUT="${OUT:-$ROOT/build}"
KS="${KS:-$TOOLS/hrconnect.p12}"
KS_PASS="${KS_PASS:-hrconnect}"
KS_ALIAS="${KS_ALIAS:-hrconnect}"
EXPORTS="--add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.pkcs=ALL-UNNAMED --add-exports java.base/sun.security.util=ALL-UNNAMED"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/stage"

echo "==> Compiling Java"
find "$MAIN/java" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -classpath "$TOOLS/android-all.jar" \
      -d "$OUT/classes" @"$OUT/sources.txt"

echo "==> Dexing"
java -cp "$TOOLS/dx.jar" com.android.dx.command.Main --dex \
     --output="$OUT/stage/classes.dex" "$OUT/classes"

echo "==> Staging apktool project"
cp "$MAIN/AndroidManifest.xml" "$OUT/stage/AndroidManifest.xml"
cp -r "$MAIN/res" "$OUT/stage/res"
cp -r "$MAIN/assets" "$OUT/stage/assets"
cat > "$OUT/stage/apktool.yml" <<'YML'
!!brut.androlib.meta.MetaInfo
apkFileName: hr-connect.apk
compressionType: false
doNotCompress:
isFrameworkApk: false
packageInfo:
  forcedPackageId: '127'
  renameManifestPackage: null
sdkInfo:
  minSdkVersion: '24'
  targetSdkVersion: '29'
sharedLibrary: false
sparseResources: false
unknownFiles: {}
usesFramework:
  ids:
  - 1
  tag: null
version: 2.4.1
versionInfo:
  versionCode: '2'
  versionName: '1.1'
YML

echo "==> Building APK (apktool)"
java -jar "$TOOLS/apktool.jar" b "$OUT/stage" -o "$OUT/hr-connect-unsigned.apk"

echo "==> Signing (v2)"
java $EXPORTS -cp "$TOOLS/apksig.jar:$TOOLS/signer" Sign \
     "$KS" "$KS_PASS" "$KS_ALIAS" \
     "$OUT/hr-connect-unsigned.apk" "$OUT/hr-connect.apk"

echo "==> Verifying"
java $EXPORTS -cp "$TOOLS/apksig.jar:$TOOLS/signer" Verify "$OUT/hr-connect.apk"

ls -la "$OUT/hr-connect.apk"
echo "Done: $OUT/hr-connect.apk"
