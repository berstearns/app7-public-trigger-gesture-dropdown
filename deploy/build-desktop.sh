#!/usr/bin/env bash
#===============================================================================
# build-desktop.sh — read deploy/.env, materialize server-config.yaml across the
# three locations, then build the desktop release distributables.
#
#   ./deploy/build-desktop.sh                 # current OS native + uber jar
#   ./deploy/build-desktop.sh deb             # just .deb (Linux)
#   ./deploy/build-desktop.sh rpm             # just .rpm (Linux)
#   ./deploy/build-desktop.sh uberjar         # just the cross-platform fat jar
#   ./deploy/build-desktop.sh dist            # both Deb+Rpm via packageReleaseDistributionForCurrentOS
#
# Outputs land in $DESKTOP_OUT_DIR (default: ./build/desktop).
# Targets: BACKEND_URL/QUEUE_HOST/QUEUE_PORT from deploy/.env are baked in
# the same way as the Android APK build.
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

flavor="${1:-dist}"
case "$flavor" in
    dist|deb|rpm|uberjar) ;;
    *) echo "usage: $0 {dist|deb|rpm|uberjar}"; exit 1 ;;
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
echo "=== gradle desktop release ($flavor) ==="
cd "$ROOT"
case "$flavor" in
    dist)    ./gradlew :desktopApp:packageReleaseDistributionForCurrentOS ;;
    deb)     ./gradlew :desktopApp:packageReleaseDeb ;;
    rpm)     ./gradlew :desktopApp:packageReleaseRpm ;;
    uberjar) ./gradlew :desktopApp:packageReleaseUberJarForCurrentOS ;;
esac

# Copy outputs to DESKTOP_OUT_DIR
out_dir="${DESKTOP_OUT_DIR:-./build/desktop}"
mkdir -p "$out_dir"
echo
echo "=== copying outputs → $out_dir ==="
# Only the packaged distributables. Compose Desktop's `compose/` tree
# also contains every staged dependency jar — those must NOT be copied.
#   - native pkgs:  desktopApp/build/compose/binaries/main-release/{deb,rpm,msi,...}/
#   - uber jar:     desktopApp/build/compose/jars/<package>-<arch>-<ver>-release.jar
shopt -s nullglob
for f in desktopApp/build/compose/binaries/main-release/*/* \
         desktopApp/build/compose/jars/*-release.jar; do
    [[ -f "$f" ]] && { cp "$f" "$out_dir/"; echo "  $f"; }
done
ls -la "$out_dir"
