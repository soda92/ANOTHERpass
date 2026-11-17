#!/bin/sh
# Launcher for the ANOTHERpass desktop application.
# Installed either as <prefix>/lib/anotherpass/bin/anotherpass (with a
# /usr/bin/anotherpass symlink) or in a staged package tree.
set -eu

SELF="$(readlink -f "$0" 2>/dev/null || printf '%s' "$0")"
APP_HOME="$(cd "$(dirname "$SELF")/.." && pwd -P)"
LIB_DIR="$APP_HOME/lib"

if [ -x /usr/lib/jvm/java-21-openjdk/bin/java ]; then
    JAVA=/usr/lib/jvm/java-21-openjdk/bin/java
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi

# --- UI scale -----------------------------------------------------------
# The app treats this as a *target effective scale*, not a multiplier, so it
# cannot double-scale when Java already honours the desktop scale.
#
# Priority in the app:
#   ~/.config/anotherpass/ui.properties (in-app button)
#   > ANOTHERPASS_UI_SCALE / ~/.config/anotherpass/ui-scale
#   > ANOTHERPASS_DETECTED_SCALE
#   > current system density
CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/anotherpass"

EXPLICIT_SCALE="${ANOTHERPASS_UI_SCALE:-}"
if [ -z "$EXPLICIT_SCALE" ] && [ -f "$CONFIG_DIR/ui-scale" ]; then
    EXPLICIT_SCALE="$(tr -d '[:space:]' < "$CONFIG_DIR/ui-scale" 2>/dev/null || true)"
fi

DETECTED_SCALE=""
if command -v kscreen-doctor >/dev/null 2>&1; then
    # KDE: take the maximum enabled-output scale. KDE stores fractional scales
    # (1.5/1.7) that Java does not always expose to Compose.
    DETECTED_SCALE="$(kscreen-doctor -o 2>/dev/null \
        | sed 's/\x1b\[[0-9;]*m//g' \
        | awk '
            /Scale:/ { if (($NF + 0) > max) max = $NF + 0 }
            END { if (max > 0) printf "%.2f", max }
        ')"
fi

if [ -z "$DETECTED_SCALE" ] && command -v gsettings >/dev/null 2>&1; then
    factor="$(gsettings get org.gnome.desktop.interface scaling-factor 2>/dev/null | tr -dc '0-9' || true)"
    text="$(gsettings get org.gnome.desktop.interface text-scaling-factor 2>/dev/null | tr -dc '0-9.' || true)"
    if [ -n "${factor:-}" ] && [ -n "${text:-}" ]; then
        DETECTED_SCALE="$(awk "BEGIN { printf \"%.2f\", ${factor} * ${text} }" 2>/dev/null || true)"
    fi
fi

TARGET_SCALE="${EXPLICIT_SCALE:-$DETECTED_SCALE}"

export ANOTHERPASS_DETECTED_SCALE="${DETECTED_SCALE:-}"
if [ -n "${EXPLICIT_SCALE:-}" ]; then
    export ANOTHERPASS_UI_SCALE="$EXPLICIT_SCALE"
fi

JAVA_SCALE_OPT=""
if [ -n "${TARGET_SCALE:-}" ]; then
    JAVA_SCALE_OPT="-Dsun.java2d.uiScale=$TARGET_SCALE"
fi

exec "$JAVA" $JAVA_SCALE_OPT \
    -Dskiko.library.path="$LIB_DIR" \
    -Dcompose.application.configure.swing.globals=true \
    -cp "$LIB_DIR/*" \
    de.jepfa.yapm.desktop.MainKt "$@"
