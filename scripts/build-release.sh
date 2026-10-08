#!/bin/sh
# Builds the release APK for the tag being released: the tag sets the
# version name and the workflow's run number the version code. With the
# keystore in the repository's secrets the APK is signed and kept for the
# GitHub Release; without it the build still proves the release compiles.
set -eu
cd "$(dirname "$0")/.."

case "${GITHUB_REF_NAME:-}" in
    v[0-9]*) version=${GITHUB_REF_NAME#v} ;;
    *) version=0.0.0 ;;
esac
# A suffix such as -rc.1 marks a prerelease on GitHub; the app shows the plain version.
version=${version%%-*}
code=${GITHUB_RUN_NUMBER:-1}

if [ -n "${KEYSTORE_BASE64:-}" ]; then
    keystore="${RUNNER_TEMP:-/tmp}/release.jks"
    printf '%s' "$KEYSTORE_BASE64" | base64 -d > "$keystore"
    export ANDROID_KEYSTORE_FILE="$keystore"
fi

./gradlew assembleRelease -Pexplore.versionName="$version" -Pexplore.versionCode="$code"

mkdir -p release
signed=app/build/outputs/apk/release/app-release.apk
summary=${GITHUB_STEP_SUMMARY:-/dev/stdout}
if [ -f "$signed" ]; then
    cp "$signed" "release/Kite-$version.apk"
    echo "Built and signed Kite $version ($code)." >> "$summary"
else
    echo "Built Kite $version ($code) unsigned: the keystore is not in the repository's secrets, so no APK is attached. RELEASE.md has the steps." >> "$summary"
fi
