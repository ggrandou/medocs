#!/usr/bin/env bash
# Installs the build dependencies without Android Studio: JDK 21, Android SDK command-line tools,
# SDK components (platform, build-tools, platform-tools), then android/local.properties.
# The script can be rerun: whatever is already in place is left untouched.
#
# Environment variables (optional):
#   ANDROID_HOME / ANDROID_SDK_ROOT   SDK directory (default: ~/Android/Sdk)
#   CMDLINE_TOOLS_VERSION             number of the "commandlinetools-linux-<n>_latest.zip" zip (default below)
#   SKIP_JDK=1                        do not check for or install the JDK
set -euo pipefail

JDK_MIN=21
CMDLINE_TOOLS_VERSION="${CMDLINE_TOOLS_VERSION:-11076708}"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
PACKAGES=("platform-tools" "platforms;android-36" "build-tools;36.0.0")

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_PROPERTIES="$ROOT_DIR/android/local.properties"

info() { printf '\n==> %s\n' "$*"; }
fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }

# --- JDK --------------------------------------------------------------------
install_jdk() {
    info "JDK $JDK_MIN"
    local major=""
    if command -v javac >/dev/null 2>&1; then
        major="$(javac -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p')"
    fi
    if [ -n "$major" ] && [ "$major" -ge "$JDK_MIN" ]; then
        echo "javac $major already present."
        return
    fi
    if command -v apt-get >/dev/null 2>&1; then
        echo "Installing openjdk-$JDK_MIN-jdk (sudo required)."
        sudo apt-get install -y "openjdk-$JDK_MIN-jdk"
    else
        fail "JDK $JDK_MIN not found: install it with the distribution's package manager, then rerun."
    fi
}

# --- SDK command-line tools ---------------------------------------------------
install_cmdline_tools() {
    info "SDK command-line tools ($SDK_DIR)"
    if [ -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
        echo "sdkmanager already present."
        return
    fi
    for c in curl unzip; do
        command -v "$c" >/dev/null 2>&1 || fail "\"$c\" is required (sudo apt install $c)."
    done
    local tmp
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN
    local url="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
    echo "Downloading: $url"
    curl -fL --progress-bar -o "$tmp/cmdline-tools.zip" "$url"
    mkdir "$tmp/extracted"
    unzip -q "$tmp/cmdline-tools.zip" -d "$tmp/extracted"
    mkdir -p "$SDK_DIR/cmdline-tools"
    # sdkmanager requires the "latest" directory (or a version number) under cmdline-tools/
    mv "$tmp/extracted/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
}

# --- SDK components -----------------------------------------------------------
install_components() {
    local sdkmanager="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
    info "SDK licenses"
    # "yes" gets SIGPIPE when sdkmanager exits: this is not an error
    ( set +o pipefail; yes | "$sdkmanager" --sdk_root="$SDK_DIR" --licenses >/dev/null )
    info "Components: ${PACKAGES[*]}"
    "$sdkmanager" --verbose --sdk_root="$SDK_DIR" "${PACKAGES[@]}"
}

# --- local.properties -------------------------------------------------------------
write_local_properties() {
    info "android/local.properties"
    if [ -f "$LOCAL_PROPERTIES" ]; then
        echo "Already present, left unchanged:"
        sed 's/^/    /' "$LOCAL_PROPERTIES"
        grep -q "^sdk.dir=$SDK_DIR\$" "$LOCAL_PROPERTIES" || echo "Warning: sdk.dir differs from $SDK_DIR."
        return
    fi
    mkdir -p "$(dirname "$LOCAL_PROPERTIES")"
    echo "sdk.dir=$SDK_DIR" > "$LOCAL_PROPERTIES"
    echo "Created with sdk.dir=$SDK_DIR"
}

[ "${SKIP_JDK:-0}" = "1" ] || install_jdk
install_cmdline_tools
install_components
write_local_properties

info "Done. Check with: cd android && ./gradlew assembleDebug"
