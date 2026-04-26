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

# Auto-revert the materialized server-config.yaml files at exit so the
# real BACKEND_URL never accidentally gets committed.
_revert_yamls() {
    if command -v git >/dev/null && git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1; then
        git -C "$ROOT" checkout -- \
            androidApp/server-config.yaml \
            desktopApp/server-config.yaml \
            desktopApp/src/main/resources/server-config.yaml 2>/dev/null || true
    fi
}
trap _revert_yamls EXIT

: "${BACKEND_URL:?set in deploy/.env}"
: "${QUEUE_HOST:?set in deploy/.env}"
: "${QUEUE_PORT:?set in deploy/.env}"

flavor="${1:-dist}"
case "$flavor" in
    dist|deb|rpm|uberjar|appimage) ;;
    *) echo "usage: $0 {dist|deb|rpm|uberjar|appimage}"; exit 1 ;;
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
    dist)     ./gradlew :desktopApp:packageReleaseDistributionForCurrentOS ;;
    deb)      ./gradlew :desktopApp:packageReleaseDeb ;;
    rpm)      ./gradlew :desktopApp:packageReleaseRpm ;;
    uberjar)  ./gradlew :desktopApp:packageReleaseUberJarForCurrentOS ;;
    appimage) ./gradlew :desktopApp:createReleaseDistributable ;;
esac

# AppImage post-processing: wrap the jpackage app-image into a single
# .AppImage file. Requires `appimagetool` somewhere on PATH or at
# /tmp/appimage-tools/appimagetool. If missing, we fall back gracefully.
if [[ "$flavor" == "appimage" ]]; then
    APPIMG_SRC="$ROOT/desktopApp/build/compose/binaries/main-release/app/manga-reader"
    [[ -d "$APPIMG_SRC" ]] || { echo "expected app-image not found: $APPIMG_SRC"; exit 1; }
    APPDIR="$(mktemp -d)/manga-reader.AppDir"
    mkdir -p "$APPDIR/usr"
    cp -r "$APPIMG_SRC/bin" "$APPIMG_SRC/lib" "$APPDIR/usr/"
    cat > "$APPDIR/AppRun" <<'AR'
#!/usr/bin/env bash
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/manga-reader" "$@"
AR
    chmod +x "$APPDIR/AppRun"
    cat > "$APPDIR/manga-reader.desktop" <<'DT'
[Desktop Entry]
Name=manga-reader
Exec=manga-reader
Icon=manga-reader
Type=Application
Categories=Utility;
DT
    # Minimal 256x256 transparent PNG (no extra tools).
    python3 -c "
import struct, zlib
def png(w,h,rgba=b'\\x00\\x00\\x00\\x00'):
    sig=b'\\x89PNG\\r\\n\\x1a\\n'
    def chunk(t,d): return struct.pack('!I',len(d))+t+d+struct.pack('!I',zlib.crc32(t+d)&0xffffffff)
    ihdr=struct.pack('!IIBBBBB',w,h,8,6,0,0,0)
    raw=b''.join(b'\\x00'+rgba*w for _ in range(h))
    return sig+chunk(b'IHDR',ihdr)+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
open('$APPDIR/manga-reader.png','wb').write(png(256,256))
"
    AIT="${APPIMAGETOOL:-/tmp/appimage-tools/appimagetool}"
    if [[ ! -x "$AIT" ]]; then
        AIT="$(command -v appimagetool || true)"
    fi
    if [[ -z "$AIT" ]]; then
        echo "appimagetool not found. Either:"
        echo "  1) curl -fsSL -o /tmp/appimage-tools/appimagetool \\"
        echo "       https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
        echo "     chmod +x /tmp/appimage-tools/appimagetool"
        echo "  2) point APPIMAGETOOL=/path/to/appimagetool"
        exit 1
    fi
    out="$ROOT/build/desktop/manga-reader.AppImage"
    mkdir -p "$(dirname "$out")"
    ARCH=x86_64 "$AIT" --appimage-extract-and-run "$APPDIR" "$out"
    rm -rf "$(dirname "$APPDIR")"
    chmod +x "$out"
    echo
    echo "=== single-binary AppImage built: $out ==="
    ls -la "$out"
    return 0 2>/dev/null || exit 0   # skip the generic copy step below
fi

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
