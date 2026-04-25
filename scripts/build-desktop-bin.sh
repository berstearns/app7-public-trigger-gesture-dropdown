#!/usr/bin/env bash
# Build a single self-extracting .bin for the desktop app.
# Slim — app + bundled JRE + native launcher only. NO comics, NO data, NO assets.
# Output: dist/manga-reader.bin
#
# User runs:
#   chmod +x manga-reader.bin
#   ./manga-reader.bin
#
# First run extracts to ~/.cache/manga-reader-bundle/<sha>/ and launches.
# Subsequent runs detect the extracted bundle and skip straight to launch.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
PKG="manga-reader"
OUT_DIR="dist"
BIN="${OUT_DIR}/${PKG}.bin"

echo "[1/4] Gradle :desktopApp:createDistributable"
./gradlew :desktopApp:createDistributable

DIST_SRC="desktopApp/build/compose/binaries/main/app/${PKG}"
[ -d "$DIST_SRC" ] || { echo "ERR: bundle not produced at $DIST_SRC"; exit 1; }

echo "[2/4] Tarring slim app bundle (no assets)"
TAR="${OUT_DIR}/.bundle.tar.gz"
mkdir -p "$OUT_DIR"
tar -C "$(dirname "$DIST_SRC")" -czf "$TAR" "$(basename "$DIST_SRC")"

SHA=$(sha256sum "$TAR" | cut -c1-64)
PAYLOAD_SIZE=$(du -h "$TAR" | cut -f1)

echo "[3/4] Building self-extracting .bin (sha=${SHA:0:12}…, payload=$PAYLOAD_SIZE)"
cat > "$BIN" <<HEADER
#!/usr/bin/env bash
# Self-extracting bundle for ${PKG}. Generated $(date -u +%Y-%m-%dT%H:%M:%SZ).
set -e
PKG="${PKG}"
SHA="${SHA}"
CACHE="\${XDG_CACHE_HOME:-\$HOME/.cache}/\${PKG}-bundle/\$SHA"
LAUNCHER="\$CACHE/\$PKG/bin/\$PKG"
if [ ! -x "\$LAUNCHER" ]; then
  echo "[\$PKG] first run — extracting to \$CACHE"
  rm -rf "\$CACHE"
  mkdir -p "\$CACHE"
  ARCH_LINE=\$(awk '/^__ARCHIVE_PAYLOAD_FOLLOWS__\$/ {print NR + 1; exit}' "\$0")
  tail -n +"\$ARCH_LINE" "\$0" | tar xz -C "\$CACHE"
fi
exec "\$LAUNCHER" "\$@"
exit 1
__ARCHIVE_PAYLOAD_FOLLOWS__
HEADER

cat "$TAR" >> "$BIN"
chmod +x "$BIN"
rm -f "$TAR"

BIN_SIZE=$(du -h "$BIN" | cut -f1)
( cd "$OUT_DIR" && sha256sum "$(basename "$BIN")" > "$(basename "$BIN").sha256" )

echo "[4/4] DONE"
echo "  binary: $ROOT/$BIN  ($BIN_SIZE)"
echo "  sha256: $ROOT/$BIN.sha256"
echo
echo "Run:"
echo "  chmod +x $BIN"
echo "  $BIN"
