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

exec "$JAVA" \
    -Dskiko.library.path="$LIB_DIR" \
    -Dcompose.application.configure.swing.globals=true \
    -cp "$LIB_DIR/*" \
    de.jepfa.yapm.desktop.MainKt "$@"
