/#!/usr/bin/env bash
# Wrapper around IntelliJ's bundled Maven.
# Usage: ./build.sh [maven goals]   e.g.  ./build.sh compile
#                                         ./build.sh clean package -DskipTests
#                                         ./build.sh package-dist   ← builds Windows installer
MVN="/d/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/plugins/maven/lib/maven3/bin/mvn.cmd"
REPO="C:/Users/Tharwani/.m2/repository"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── Distribution packaging config ────────────────────────────────────────────
JAVAFX_JMODS="D:/Projects/javafx-jmods-21.0.10"
WIX_BIN="C:/Program Files (x86)/WiX Toolset v3.14/bin"
APP_NAME="Sanchay"
APP_VENDOR="Girish Tharwani"

if [ "$1" = "package-dist" ]; then
    set -e  # abort on any error

    # Extract version and build name from pom.xml, e.g. "v1.0.0-Agami"
    # → APP_VERSION_NUM="1.0.0"  APP_BUILD="Agami"
    RAW=$(grep '<version>v' "$SCRIPT_DIR/pom.xml" | head -1 \
          | sed 's/.*<version>v\([^<]*\)<\/version>.*/\1/')
    APP_VERSION_NUM="${RAW%%-*}"
    APP_BUILD="${RAW#*-}"
    INSTALLER_NAME="Sanchay-rel-${APP_BUILD}-v${APP_VERSION_NUM}"
    echo ">>> Building installer: ${INSTALLER_NAME}.exe"

    # ── Step 1: Build the fat JAR (JavaFX excluded — comes via runtime image) ──
    echo ">>> [1/3] Maven package..."
    "$MVN" -f "$SCRIPT_DIR/pom.xml" -Dmaven.repo.local="$REPO" clean package -DskipTests

    # ── Step 2: jlink — build trimmed JRE with JavaFX modules ────────────────
    echo ">>> [2/3] jlink..."
    rm -rf "$SCRIPT_DIR/target/runtime"
    jlink \
        --module-path "$JAVAFX_JMODS" \
        --add-modules java.base,java.desktop,java.logging,java.prefs,java.xml,jdk.unsupported,javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
        --output "$SCRIPT_DIR/target/runtime" \
        --strip-debug \
        --compress=zip-6 \
        --no-header-files \
        --no-man-pages

    # ── Step 3: jpackage — build Windows installer ───────────────────────────
    echo ">>> [3/3] jpackage..."
    export PATH="$WIX_BIN:$PATH"

    # jpackage --input picks up everything in the folder; use a clean staging dir
    rm -rf "$SCRIPT_DIR/target/pkg-input"
    mkdir -p "$SCRIPT_DIR/target/pkg-input"
    cp "$SCRIPT_DIR/target/sanchay-app.jar" "$SCRIPT_DIR/target/pkg-input/"

    # Clear installer/ contents so only the latest build is present
    mkdir -p "$SCRIPT_DIR/installer"
    rm -f "$SCRIPT_DIR/installer/"*

    # jpackage requires a plain numeric version; we rename the output to add "v" prefix
    jpackage \
        --type exe \
        --name "Sanchay" \
        --app-version "$APP_VERSION_NUM" \
        --vendor "$APP_VENDOR" \
        --input "$SCRIPT_DIR/target/pkg-input" \
        --main-jar sanchay-app.jar \
        --runtime-image "$SCRIPT_DIR/target/runtime" \
        --java-options "--add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base" \
        --dest "$SCRIPT_DIR/installer" \
        --icon "$SCRIPT_DIR/src/main/resources/icon.ico" \
        --win-dir-chooser \
        --win-menu \
        --win-shortcut

    # Rename to add "v" prefix: Sanchay-build-Agami-1.0.0.exe → Sanchay-build-Agami-v1.0.0.exe
    mv "$SCRIPT_DIR/installer/Sanchay-${APP_VERSION_NUM}.exe" \
       "$SCRIPT_DIR/installer/${INSTALLER_NAME}.exe"

    echo ""
    echo ">>> Done. Installer: installer/${INSTALLER_NAME}.exe"
else
    "$MVN" -f "$SCRIPT_DIR/pom.xml" -Dmaven.repo.local="$REPO" "$@"
fi