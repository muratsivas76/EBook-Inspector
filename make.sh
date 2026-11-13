#!/bin/bash

# ==============================================================================
# ANDROID CLI BUILD SCRIPT
# Project: net.murat.decodebook
#
# Build environment:
#   OpenJDK 27
#   Android Platform API 36
#   Android Build Tools 36.0.0
#   Java source/target compatibility: 1.8
#
# Minimum SDK : API 29
# Target SDK  : API 29
#
# DEX compiler:
#   D8 from build-tools 36.0.0/lib/d8.jar
#
# ==============================================================================

# ------------------------------------------------------------------------------
# TERMINAL COLORS
# ------------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'


# ------------------------------------------------------------------------------
# ANDROID SDK PATHS
# ------------------------------------------------------------------------------
ANDROID_HOME="/usr/java/android"
export JDK_JAVA_OPTIONS="--enable-native-access=ALL-UNNAMED"

BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"
D8_JAR="$BUILD_TOOLS/lib/d8.jar"

AAPT="$BUILD_TOOLS/aapt"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"


# ------------------------------------------------------------------------------
# PROJECT FILES
# ------------------------------------------------------------------------------
PACKAGE_NAME="net.murat.decodebook"

KEYSTORE="Inspector.keystore"
KEY_ALIAS="Inspector.keystore"

UNSIGNED_APK="bin/Inspector.unsigned.apk"
SIGNED_APK="bin/Inspector.signed.apk"
FINAL_APK="bin/Inspector.apk"

DEX_OUTPUT_DIR="bin"


# ------------------------------------------------------------------------------
# HELPER FUNCTIONS
# ------------------------------------------------------------------------------
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}


log_success() {
    echo -e "${GREEN}[OK]${NC} $1"
}


check_error() {
    if [ $? -ne 0 ]; then
        echo -e "${RED}[ERROR] Critical failure encountered during: $1${NC}"
        echo -e "${YELLOW}[WARN] Build aborted.${NC}"
        exit 1
    fi
}


# ------------------------------------------------------------------------------
# CLEANUP
# ------------------------------------------------------------------------------
run_cleanup() {

    log_info "Removing temporary build artifacts..."

    rm -fv bin/classes.dex
    rm -fv bin/*.signed.apk
    rm -fv bin/*.unsigned.apk
    rm -fv bin/*.idsig 
    rm -fv src/net/murat/decodebook/R.java
    rm -rf obj/net
}


# ------------------------------------------------------------------------------
# STEP 0: ENVIRONMENT CHECK
# ------------------------------------------------------------------------------
log_info "Checking Android build environment..."

if [ ! -f "$ANDROID_JAR" ]; then
    echo -e "${RED}[ERROR] android-36/android.jar not found:${NC}"
    echo "$ANDROID_JAR"
    exit 1
fi

if [ ! -f "$D8_JAR" ]; then
    echo -e "${RED}[ERROR] D8 JAR not found:${NC}"
    echo "$D8_JAR"
    exit 1
fi

if [ ! -x "$AAPT" ]; then
    echo -e "${RED}[ERROR] aapt not found:${NC}"
    echo "$AAPT"
    exit 1
fi

if [ ! -x "$ZIPALIGN" ]; then
    echo -e "${RED}[ERROR] zipalign not found:${NC}"
    echo "$ZIPALIGN"
    exit 1
fi

if [ ! -x "$APKSIGNER" ]; then
    echo -e "${RED}[ERROR] apksigner not found:${NC}"
    echo "$APKSIGNER"
    exit 1
fi

log_success "Android API 36 build environment found."


# ------------------------------------------------------------------------------
# STEP 1: INITIAL CLEANING
# ------------------------------------------------------------------------------
log_info "Cleaning previous APK and DEX files..."

rm -fv bin/*.apk
rm -fv bin/classes.dex

check_error "Initial cleanup"


# ------------------------------------------------------------------------------
# STEP 2: AAPT - GENERATE R.JAVA
# ------------------------------------------------------------------------------
log_info "Generating R.java using AAPT 36.0.0..."

"$AAPT" package -v -f -m \
    -S res \
    -J src \
    -M AndroidManifest.xml \
    -I "$ANDROID_JAR"

check_error "AAPT resource processing"

log_success "R.java generated."


# ------------------------------------------------------------------------------
# STEP 3: JAVAC - COMPILE JAVA SOURCE
# ------------------------------------------------------------------------------
log_info "Compiling Java sources using OpenJDK 27 / Java 8 compatibility..."

javac \
    -source 1.8 \
    -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -sourcepath "src" \
    -cp "$ANDROID_JAR:obj" \
    -g:none \
    -proc:none \
    -nowarn \
    -O \
    -Xmaxwarns 1 \
    -d "obj" \
    src/net/murat/decodebook/DecodeBook.java

check_error "Java source compilation"

log_success "Java compilation completed."


# ------------------------------------------------------------------------------
# STEP 4: D8 - JAVA BYTECODE -> DEX
# ------------------------------------------------------------------------------
log_info "Converting Java bytecode to DEX using D8..."

rm -rf "$DEX_OUTPUT_DIR/d8-output"
mkdir -p "$DEX_OUTPUT_DIR/d8-output"

java \
    -cp "$D8_JAR" \
    com.android.tools.r8.D8 \
    --min-api 29 \
    --release \
    --output "$DEX_OUTPUT_DIR/d8-output" \
    $(find obj -type f -name '*.class')

check_error "D8 bytecode conversion"


if [ ! -f "$DEX_OUTPUT_DIR/d8-output/classes.dex" ]; then
    echo -e "${RED}[ERROR] D8 did not create classes.dex.${NC}"
    exit 1
fi

mv "$DEX_OUTPUT_DIR/d8-output/classes.dex" "$DEX_OUTPUT_DIR/classes.dex"

rm -rf "$DEX_OUTPUT_DIR/d8-output"

log_success "classes.dex generated by D8."


# ------------------------------------------------------------------------------
# STEP 5: AAPT - CREATE UNSIGNED APK
# ------------------------------------------------------------------------------
log_info "Creating unsigned APK using AAPT 36.0.0..."

"$AAPT" package -v -f \
    -M "AndroidManifest.xml" \
    -A "assets" \
    -S "res" \
    -I "$ANDROID_JAR" \
    -F "$UNSIGNED_APK" \
    "bin"

check_error "Unsigned APK packaging"

log_success "Unsigned APK created."


# ------------------------------------------------------------------------------
# STEP 6: ZIPALIGN
# ------------------------------------------------------------------------------
log_info "Aligning APK using zipalign 36.0.0..."

"$ZIPALIGN" \
    -v \
    -f \
    4 \
    "$UNSIGNED_APK" \
    "$FINAL_APK"

check_error "APK zip alignment"

log_success "APK alignment completed."


# ------------------------------------------------------------------------------
# STEP 7: APKSIGNER - SIGN FINAL APK
# ------------------------------------------------------------------------------
log_info "Signing aligned APK using apksigner 36.0.0..."

"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass pass:5EmrE432 \
    --key-pass pass:5EmrE432 \
    --v1-signing-enabled false \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --v4-signing-enabled false \
    "$FINAL_APK"

check_error "APK signing"

log_success "APK signed."


# ------------------------------------------------------------------------------
# STEP 8: VERIFY APK SIGNATURE
# ------------------------------------------------------------------------------
log_info "Verifying APK signature..."

"$APKSIGNER" verify \
    --verbose \
    "$FINAL_APK"

check_error "APK signature verification"

log_success "APK signature verified."

# ------------------------------------------------------------------------------
# STEP 9: FINAL CLEANUP
# ------------------------------------------------------------------------------
log_info "Removing temporary build artifacts..."

run_cleanup

# ------------------------------------------------------------------------------
# STEP 10: RESULT
# ------------------------------------------------------------------------------
echo
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}[SUCCESS] Android API 36 build completed successfully.${NC}"
echo -e "${GREEN}============================================================${NC}"
echo

ls -lh bin
