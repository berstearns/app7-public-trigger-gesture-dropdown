#!/usr/bin/env bash
# Build a self-contained Linux distributable of the desktop app.
# Output: dist/manga-reader-linux-<arch>.tar.gz  (+ .sha256)
# The tarball bundles its own JRE — no system Java needed on the target.
# Tested on Arch Linux + i3.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
PKG="manga-reader"
ARCH="$(uname -m)"
OUT_DIR="dist"
TARBALL="${OUT_DIR}/${PKG}-linux-${ARCH}.tar.gz"
SHAFILE="${TARBALL}.sha256"

echo "[1/4] Gradle :desktopApp:createDistributable"
./gradlew :desktopApp:createDistributable

SRC="desktopApp/build/compose/binaries/main/app/${PKG}"
[ -d "$SRC" ] || { echo "ERR: expected bundle at $SRC was not produced"; exit 1; }

echo "[2/4] Packing -> $TARBALL"
mkdir -p "$OUT_DIR"
tar -czf "$TARBALL" -C "$(dirname "$SRC")" "$(basename "$SRC")"

echo "[3/4] Hashing -> $SHAFILE"
( cd "$OUT_DIR" && sha256sum "$(basename "$TARBALL")" > "$(basename "$SHAFILE")" )

SIZE=$(du -h "$TARBALL" | cut -f1)

echo "[4/4] Optional AppImage (if appimagetool is on PATH)"
if command -v appimagetool >/dev/null 2>&1; then
  APPDIR="${OUT_DIR}/${PKG}.AppDir"
  rm -rf "$APPDIR"
  mkdir -p "$APPDIR/usr"
  cp -r "$SRC/." "$APPDIR/usr/"
  cat > "$APPDIR/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/manga-reader" "$@"
EOF
  chmod +x "$APPDIR/AppRun"
  cat > "$APPDIR/${PKG}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Manga Reader
Exec=${PKG}
Icon=${PKG}
Categories=Utility;
EOF
  # 1x1 transparent png as placeholder icon
  printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\rIDATx\x9cc\xfc\xff\xff?\x03\x00\x06\xfc\x02\xfe\xa7\x35\x81\x84\x00\x00\x00\x00IEND\xaeB`\x82' > "$APPDIR/${PKG}.png"
  APPIMAGE_OUT="${OUT_DIR}/${PKG}-linux-${ARCH}.AppImage"
  ARCH="$ARCH" appimagetool "$APPDIR" "$APPIMAGE_OUT" >/dev/null
  chmod +x "$APPIMAGE_OUT"
  APPIMAGE_SIZE=$(du -h "$APPIMAGE_OUT" | cut -f1)
  ( cd "$OUT_DIR" && sha256sum "$(basename "$APPIMAGE_OUT")" > "$(basename "$APPIMAGE_OUT").sha256" )
  echo "  AppImage produced: $APPIMAGE_OUT ($APPIMAGE_SIZE)"
else
  echo "  (skipped — install with: sudo pacman -S appimagetool, or grab from"
  echo "   https://github.com/AppImage/AppImageKit/releases)"
fi

echo
echo "DONE"
echo "  tarball:  $ROOT/$TARBALL  ($SIZE)"
echo "  sha256:   $ROOT/$SHAFILE"
echo
echo "User on Linux runs:"
echo "  tar xzf ${PKG}-linux-${ARCH}.tar.gz"
echo "  ./${PKG}/bin/${PKG}"
