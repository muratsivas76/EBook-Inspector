#!/bin/bash

# ==============================================================================
# PROFESSIONAL ANDROID CLI BUILD SCRIPT
# Target Architecture: Minimalist Text Editor (net.murat.notepad)
# Minimum/Target Platform: API 29+ (Using API 33 Bootclasspath Parameters)
# License: GNU GPL v3 Compatibility Pipeline
# ==============================================================================

# ANSI Color codes for clean and professional terminal diagnostics output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Absolute system paths for Android platform SDK tools and binaries
ANDROID_JAR="/usr/java/android/platforms/android-33/android.jar"
DX_TOOL="/usr/java/android/build-tools/29.0.0/dx"

# Helper function to print operation milestones gracefully
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

# Helper function to catch step failures, run garbage collection, and terminate safely
check_error() {
    if [ $? -ne 0 ]; then
        echo -e "${RED}[ERROR] Critical failure encountered during: $1${NC}"
        echo -e "${YELLOW}[WARN] Executing emergency pipeline cleanup before aborting...${NC}"
        run_cleanup
        exit 1
    fi
}

# Standardized routine to sweep volatile runtime memory footprints and temporary build blocks
run_cleanup() {
    log_info "Purging volatile compilation artifacts and cached resources..."
    rm -fv *.keystore.old
    rm -fv bin/classes.dex
    rm -fv bin/*.signed.*
    rm -fv bin/*.unsigned.*
    rm -fv src/net/murat/notepad/R.java
    rm -rf obj/net
}

# ------------------------------------------------------------------------------
# STEP 1: INITIAL CLEANING PHASE
# ------------------------------------------------------------------------------
log_info "Initiating build workspace sweep... Removing legacy APK references."
rm -fv bin/*.apk

# ------------------------------------------------------------------------------
# STEP 2: RESOURCE PROVISIONING & R.JAVA GENERATION
# ------------------------------------------------------------------------------
log_info "Processing resource nodes via AAPT to generate R.java file mappings..."
aapt package -v -f -m \
    -S res \
    -J src \
    -M AndroidManifest.xml \
    -I "$ANDROID_JAR"
check_error "AAPT Resource Code Generation (R.java)"

# ------------------------------------------------------------------------------
# STEP 3: SOURCE JAVA COMPILATION (1.8 COMPATIBILITY MODE)
# ------------------------------------------------------------------------------
log_info "Compiling raw source code sheets under strict Java 1.8 compatibility directives..."
javac -source 1.8 -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -sourcepath "src" \
    -cp "$ANDROID_JAR":"obj" \
    -g:none -proc:none -nowarn -O -Xmaxwarns 1 \
    -d "obj" "src/net/murat/notepad/Notepad.java"
check_error "Java Source Code Compilation (javac)"

# ------------------------------------------------------------------------------
# STEP 4: DALVIK BYTECODE DEXING TRANSFORMATION
# ------------------------------------------------------------------------------
log_info "Translating standard Java bytecode arrays into optimized Dalvik Executable (DEX) formats..."
sleep 5
$DX_TOOL --dex --verbose --output="bin/classes.dex" "obj"
check_error "Dalvik Bytecode Conversion (dx)"

# ------------------------------------------------------------------------------
# STEP 5: INITIAL APK PACKAGING BOUNDARY
# ------------------------------------------------------------------------------
log_info "Packaging system components, layout assets, and DEX records into an unsigned binary envelope..."
aapt package -v -f \
    -M "AndroidManifest.xml" \
    -A "assets" \
    -S "res" \
    -I "$ANDROID_JAR" \
    -F "bin/Notepad.unsigned.apk" "bin"
check_error "Initial Package Serialization (aapt pack)"

# ------------------------------------------------------------------------------
# STEP 6: CRYPTOGRAPHIC SIGNATURE APPLICATION
# ------------------------------------------------------------------------------
log_info "Applying cryptographic signatures onto the targeted archive matrix structure..."
jarsigner -keystore Notepad.keystore \
    -storepass 5EmrE432 \
    -keypass 5EmrE432 \
    -signedjar "bin/Notepad.signed.apk" \
    -digestalg SHA1 -sigalg MD5withRSA \
    "bin/Notepad.unsigned.apk" Notepad.keystore
check_error "Cryptographic Security Authentication (jarsigner)"

# ------------------------------------------------------------------------------
# STEP 7: ZIPALIGN MEMORY BOUNDARY OPTIMIZATION
# ------------------------------------------------------------------------------
log_info "Aligning storage blocks to 4-byte boundaries to reduce runtime memory footprints..."
zipalign -v -f 4 "bin/Notepad.signed.apk" "bin/Notepad.apk"
check_error "Binary Block Structural Alignment (zipalign)"

# ------------------------------------------------------------------------------
# STEP 8: POST-BUILD PURGE & DIRECTORY LISTING
# ------------------------------------------------------------------------------
run_cleanup

echo -e "\n${GREEN}[SUCCESS] Compilation loop finished successfully without errors! Output binaries:${NC}"
ls -lh bin
