#!/usr/bin/env bash
# Build an Arch-style staged package tree under app/build/local-dist without
# running makepkg. Useful for testing the launcher and desktop integration.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"

if [ -x "$ROOT/gradlew" ]; then
    "$ROOT/gradlew" -p "$ROOT" --no-daemon --console=plain :app:stageSystemDistribution
else
    (cd "$ROOT" && gradle --no-daemon --console=plain :app:stageSystemDistribution)
fi

DIST="$ROOT/app/build/system-dist"
STAGE="$ROOT/app/build/local-dist"
PKGNAME="anotherpass-desktop-git"

rm -rf "$STAGE"
install -d \
    "$STAGE/usr/lib/anotherpass/lib" \
    "$STAGE/usr/bin" \
    "$STAGE/usr/share/applications" \
    "$STAGE/usr/share/metainfo" \
    "$STAGE/usr/share/licenses/$PKGNAME"

for size in 16 24 32 48 64 128 256 512; do
    install -Dm644 "$HERE/anotherpass-$size.png" \
        "$STAGE/usr/share/icons/hicolor/${size}x${size}/apps/anotherpass.png"
done

cp -a "$DIST/lib/." "$STAGE/usr/lib/anotherpass/lib/"
install -Dm755 "$DIST/bin/anotherpass" "$STAGE/usr/lib/anotherpass/bin/anotherpass"
ln -sf ../lib/anotherpass/bin/anotherpass "$STAGE/usr/bin/anotherpass"
install -Dm644 "$HERE/anotherpass.desktop" "$STAGE/usr/share/applications/anotherpass.desktop"
install -Dm644 "$HERE/anotherpass.metainfo.xml" "$STAGE/usr/share/metainfo/anotherpass.metainfo.xml"
install -Dm644 "$ROOT/../COPYING" "$STAGE/usr/share/licenses/$PKGNAME/COPYING"

echo "Staged at: $STAGE"
echo "Test with:"
echo "  $STAGE/usr/bin/anotherpass --version"
echo "  $STAGE/usr/bin/anotherpass --self-test"
