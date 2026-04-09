/#!/usr/bin/env bash
# Wrapper around Maven.
# Usage: ./build.sh [maven goals]   e.g.  ./build.sh compile
#                                         ./build.sh clean package -DskipTests
#                                         ./build.sh package-dist   ← builds Windows installer
#                                         ./build.sh setup-installer-checkbox  ← one-time setup for launch checkbox
MVN="/d/Program Files/apache-maven-3.9.14/bin/mvn.cmd"
REPO="C:/Users/Tharwani/.m2/repository"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── Distribution packaging config ────────────────────────────────────────────
JAVAFX_JMODS="D:/Projects/javafx-jmods-21.0.10"
WIX_BIN="C:/Program Files (x86)/WiX Toolset v3.14/bin"
APP_NAME="Sanchay"
APP_VENDOR="Girish Tharwani"
JPACKAGE_WORK="$SCRIPT_DIR/target/jpackage-work"
CUSTOM_WXS="$SCRIPT_DIR/resources/wix/Sanchay.wxs"

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

    # --temp dir must be empty; clear and recreate so jpackage preserves its
    # intermediate files (including the .wxs template) for setup-installer-checkbox
    rm -rf "$JPACKAGE_WORK"

    # Build jpackage command as an array so --resource-dir can be added conditionally
    JPACKAGE_CMD=(
        jpackage
        --type exe
        --name "$APP_NAME"
        --app-version "$APP_VERSION_NUM"
        --vendor "$APP_VENDOR"
        --input "$SCRIPT_DIR/target/pkg-input"
        --main-jar sanchay-app.jar
        --runtime-image "$SCRIPT_DIR/target/runtime"
        --java-options "--add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base"
        --dest "$SCRIPT_DIR/installer"
        --icon "$SCRIPT_DIR/src/main/resources/icon.ico"
        --win-dir-chooser
        --win-menu
        --win-shortcut
        --temp "$JPACKAGE_WORK"
    )

    if [ -f "$CUSTOM_WXS" ]; then
        JPACKAGE_CMD+=(--resource-dir "$SCRIPT_DIR/resources/wix")
        echo ">>>   Custom WiX template active — launch checkbox enabled"
    else
        echo ">>>   Tip: run './build.sh setup-installer-checkbox' after this build to enable the launch-on-finish checkbox"
    fi

    "${JPACKAGE_CMD[@]}"

    # Rename to add "v" prefix: Sanchay-1.0.0.exe → Sanchay-rel-Agami-v1.0.0.exe
    mv "$SCRIPT_DIR/installer/Sanchay-${APP_VERSION_NUM}.exe" \
       "$SCRIPT_DIR/installer/${INSTALLER_NAME}.exe"

    echo ""
    echo ">>> Done. Installer: installer/${INSTALLER_NAME}.exe"

elif [ "$1" = "setup-installer-checkbox" ]; then
    set -e

    # ── Find the .wxs jpackage generated in the last package-dist run ─────────
    WXS_SRC=$(find "$JPACKAGE_WORK" -name "*.wxs" 2>/dev/null | head -1)
    if [ -z "$WXS_SRC" ]; then
        echo ">>> Error: no .wxs file found in $JPACKAGE_WORK"
        echo ">>>        Run './build.sh package-dist' first, then re-run this command."
        exit 1
    fi

    mkdir -p "$SCRIPT_DIR/resources/wix"
    cp "$WXS_SRC" "$CUSTOM_WXS"
    echo ">>> Copied $(basename "$WXS_SRC") → resources/wix/Sanchay.wxs"

    # ── Patch: insert checkbox XML before the closing </Product> tag ──────────
    PATCH_FILE=$(mktemp)
    cat > "$PATCH_FILE" << 'ENDPATCH'
  <!-- Launch after install checkbox — added by setup-installer-checkbox -->
  <Property Id="WIXUI_EXITDIALOGOPTIONALCHECKBOXTEXT" Value="Launch Sanchay now" />
  <Property Id="WIXUI_EXITDIALOGOPTIONALCHECKBOX" Value="1" />
  <CustomAction Id="LaunchApplication" FileKey="APPLICATIONMAINEXE" ExeCommand="" Return="asyncNoWait" />
  <UI>
    <Publish Dialog="ExitDialog" Control="Finish" Event="DoAction" Value="LaunchApplication">WIXUI_EXITDIALOGOPTIONALCHECKBOX = 1 AND NOT Installed</Publish>
  </UI>
ENDPATCH

    # awk: print patch content immediately before the </Product> line
    awk -v pf="$PATCH_FILE" '
        /<\/Product>/ { while ((getline ln < pf) > 0) print ln; close(pf) }
        { print }
    ' "$CUSTOM_WXS" > "${CUSTOM_WXS}.tmp"
    mv "${CUSTOM_WXS}.tmp" "$CUSTOM_WXS"
    rm "$PATCH_FILE"

    echo ">>> Patched Sanchay.wxs with launch-on-finish checkbox"
    echo ">>> Run './build.sh package-dist' to build the installer with the checkbox active"

else
    "$MVN" -f "$SCRIPT_DIR/pom.xml" -Dmaven.repo.local="$REPO" "$@"
fi
