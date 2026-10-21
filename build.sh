#!/bin/sh
set -eu
TV_BUILD_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$TV_BUILD_ROOT"
export JAVA_HOME="$TV_BUILD_ROOT/.local/jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
TV_TOOLS="$TV_BUILD_ROOT/.local/sdk/build-tools/android-15"
TV_PLATFORM="$TV_BUILD_ROOT/.local/sdk/platforms/android-34-ext12/android.jar"
TV_BUILD="$TV_BUILD_ROOT/.local/build"
TV_KEY="$TV_BUILD_ROOT/.local/keys/atelier-tv.p12"
TV_VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' app/src/main/AndroidManifest.xml)
if [ ! -f "$TV_PLATFORM" ] || [ ! -x "$JAVA_HOME/bin/javac" ] || [ ! -f "$TV_TOOLS/aapt2" ]; then
  printf '%s\n' 'SDK Android 34, build-tools 35.0.0 et JDK 17 requis dans .local.' >&2
  exit 1
fi
mkdir -p "$TV_BUILD/classes" "$TV_BUILD/dex" .local/keys dist
find "$TV_BUILD/classes" -name '*.class' -type f -delete
find "$TV_BUILD/dex" -name '*.dex' -type f -delete
rm -f "$TV_BUILD/resources.zip"
chmod +x "$TV_TOOLS/aapt2" "$TV_TOOLS/zipalign" "$TV_TOOLS/d8" "$TV_TOOLS/apksigner"
"$TV_TOOLS/aapt2" compile --dir app/src/main/res -o "$TV_BUILD/resources.zip"
"$TV_TOOLS/aapt2" link -o "$TV_BUILD/base.apk" -I "$TV_PLATFORM" --manifest app/src/main/AndroidManifest.xml -A app/src/main/assets "$TV_BUILD/resources.zip"
find app/src/main/java -name '*.java' -print > "$TV_BUILD/sources.txt"
javac -encoding UTF-8 --release 8 -classpath "$TV_PLATFORM" -d "$TV_BUILD/classes" @"$TV_BUILD/sources.txt"
find "$TV_BUILD/classes" -name '*.class' -print > "$TV_BUILD/classes.txt"
"$TV_TOOLS/d8" --min-api 26 --lib "$TV_PLATFORM" --output "$TV_BUILD/dex" @"$TV_BUILD/classes.txt"
cp "$TV_BUILD/base.apk" "$TV_BUILD/unsigned.apk"
(cd "$TV_BUILD/dex" && zip -q -j "$TV_BUILD/unsigned.apk" classes*.dex)
"$TV_TOOLS/zipalign" -f 4 "$TV_BUILD/unsigned.apk" "$TV_BUILD/aligned.apk"
if [ ! -f "$TV_KEY" ]; then
  keytool -genkeypair -keystore "$TV_KEY" -alias atelier-tv -storepass android -keypass android \
    -dname 'CN=Atelier TV local development' -keyalg RSA -keysize 2048 -validity 10000
  chmod 600 "$TV_KEY"
fi
"$TV_TOOLS/apksigner" sign --ks "$TV_KEY" --ks-key-alias atelier-tv --ks-pass pass:android \
  --out "dist/atelier-tv-v$TV_VERSION.apk" "$TV_BUILD/aligned.apk"
"$TV_TOOLS/apksigner" verify --verbose "dist/atelier-tv-v$TV_VERSION.apk"
printf '%s\n' "APK créé : dist/atelier-tv-v$TV_VERSION.apk"
