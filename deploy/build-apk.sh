#!/usr/bin/env bash
#===============================================================================
# build-apk.sh — read deploy/.env, materialize server-config.yaml across the
# three locations the build expects, then run gradle to produce APKs.
#
#   ./deploy/build-apk.sh debug          # ./gradlew :androidApp:assembleDebug
#   ./deploy/build-apk.sh release        # ./gradlew :androidApp:assembleRelease
#   ./deploy/build-apk.sh both           # both flavors
#
# Output APKs are copied to $APK_OUT_DIR (default: ./build/apks).
#===============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
ENV_FILE="$HERE/.env"
TPL_FILE="$HERE/server-config.yaml.template"

[[ -f "$ENV_FILE" ]] || { echo "missing $ENV_FILE — cp deploy/.env.example deploy/.env"; exit 1; }
[[ -f "$TPL_FILE" ]] || { echo "missing $TPL_FILE"; exit 1; }

set -o allexport; . "$ENV_FILE"; set +o allexport

: "${BACKEND_URL:?set in deploy/.env}"
: "${QUEUE_HOST:?set in deploy/.env}"
: "${QUEUE_PORT:?set in deploy/.env}"

flavor="${1:-debug}"
case "$flavor" in
    debug|release|both) ;;
    *) echo "usage: $0 {debug|release|both}"; exit 1 ;;
esac

# Materialize server-config.yaml at the three paths the build reads.
materialize() {
    local out="$1"
    envsubst < "$TPL_FILE" > "$out"
    echo "  wrote $out"
}

echo "=== materializing server-config.yaml ==="
materialize "$ROOT/androidApp/server-config.yaml"
materialize "$ROOT/desktopApp/server-config.yaml"
materialize "$ROOT/desktopApp/src/main/resources/server-config.yaml"

echo
echo "=== gradle assemble $flavor ==="
cd "$ROOT"
case "$flavor" in
    debug)   ./gradlew :androidApp:assembleDebug ;;
    release) ./gradlew :androidApp:assembleRelease ;;
    both)    ./gradlew :androidApp:assembleDebug :androidApp:assembleRelease ;;
esac

# Copy outputs to APK_OUT_DIR
out_dir="${APK_OUT_DIR:-./build/apks}"
mkdir -p "$out_dir"
echo
echo "=== copying APKs → $out_dir ==="
find androidApp/build/outputs/apk -name '*.apk' -print -exec cp {} "$out_dir/" \;
ls -la "$out_dir"
