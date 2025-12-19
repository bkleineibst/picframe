#!/bin/bash
# :::::::::::::::::::::::::::::::::::::::::::::::::::::::
# ::  SCRIPT TO BUILD RELEASE APK (NON-INTERACTIVE)   ::
# :::::::::::::::::::::::::::::::::::::::::::::::::::::::

set -e

echo ""
echo "=================================="
echo "  PicFrame Release Build Script"
echo "=================================="
echo ""

# :::VARIABLES:::
CURR_PATH="$(cd "$(dirname "$0")" && pwd)"

# Android SDK Version and Path
PLATFORM_VERSION_NEEDED=21

# Build options
GRADLE_ARGUMENTS="assembleRelease"
PROPERTIES_FILE="$CURR_PATH/local.properties"
KEYSTORE_PROPERTIES_FILE="$CURR_PATH/release-keystore.properties"

# ::::::::::::::::::::::::::::::::
# :: Load keystore properties   ::
# ::::::::::::::::::::::::::::::::
load_keystore_properties() {
    if [ ! -f "$KEYSTORE_PROPERTIES_FILE" ]; then
        echo "ERROR: Keystore properties file not found: $KEYSTORE_PROPERTIES_FILE"
        echo "Please create this file with the following properties:"
        echo "  KEYSTORE_FILE=/path/to/your/keystore.keystore"
        echo "  KEY_ALIAS=your_key_alias"
        echo "  KEYSTORE_PASSWORD=your_keystore_password"
        echo "  KEY_PASSWORD=your_key_password"
        exit 1
    fi

    # Read properties file (skip comments and empty lines)
    while IFS='=' read -r key value; do
        # Skip comments and empty lines
        [[ "$key" =~ ^#.*$ ]] && continue
        [[ -z "$key" ]] && continue
        
        # Trim whitespace
        key=$(echo "$key" | xargs)
        value=$(echo "$value" | xargs)
        
        case "$key" in
            KEYSTORE_FILE) KEYSTORE_FILE="$value" ;;
            KEY_ALIAS) KEY_ALIAS="$value" ;;
            KEYSTORE_PASSWORD) KEYSTORE_PASSWORD="$value" ;;
            KEY_PASSWORD) KEY_PASSWORD="$value" ;;
        esac
    done < "$KEYSTORE_PROPERTIES_FILE"

    # Validate required properties
    if [ -z "$KEYSTORE_FILE" ]; then
        echo "ERROR: KEYSTORE_FILE not set in $KEYSTORE_PROPERTIES_FILE"
        exit 1
    fi
    if [ -z "$KEY_ALIAS" ]; then
        echo "ERROR: KEY_ALIAS not set in $KEYSTORE_PROPERTIES_FILE"
        exit 1
    fi
    if [ -z "$KEYSTORE_PASSWORD" ]; then
        echo "ERROR: KEYSTORE_PASSWORD not set in $KEYSTORE_PROPERTIES_FILE"
        exit 1
    fi
    if [ -z "$KEY_PASSWORD" ]; then
        # Default to keystore password if key password not specified
        KEY_PASSWORD="$KEYSTORE_PASSWORD"
    fi

    # Check keystore file exists
    if [ ! -f "$KEYSTORE_FILE" ]; then
        echo "ERROR: Keystore file not found: $KEYSTORE_FILE"
        exit 1
    fi

    echo "Loaded keystore configuration:"
    echo "  Keystore: $KEYSTORE_FILE"
    echo "  Alias: $KEY_ALIAS"
    echo ""
}

# ::::::::::::::::::::::::::::::::
# :: Detect Android SDK         ::
# ::::::::::::::::::::::::::::::::
detect_android_sdk() {
    if [ -n "$ANDROID_HOME" ]; then
        ANDROID_SDK_PATH="$ANDROID_HOME"
    elif [ -n "$ANDROID_SDK_ROOT" ]; then
        ANDROID_SDK_PATH="$ANDROID_SDK_ROOT"
    else
        echo "ERROR: ANDROID_HOME or ANDROID_SDK_ROOT environment variable is not set."
        exit 1
    fi

    PLATFORM_PATH="$ANDROID_SDK_PATH/platforms/android-$PLATFORM_VERSION_NEEDED/"
    if [ ! -d "$PLATFORM_PATH" ]; then
        echo "ERROR: Required Android platform not found: $PLATFORM_PATH"
        exit 1
    fi

    echo "Android SDK: $ANDROID_SDK_PATH"
}

# ::::::::::::::::::::::::::::::::
# :: Detect Java SDK            ::
# ::::::::::::::::::::::::::::::::
detect_java_sdk() {
    # For this old project (Gradle 2.10), we need JDK 8
    JDK8_PATH=$(/usr/libexec/java_home -v 1.8 2>/dev/null)
    
    if [ -n "$JDK8_PATH" ]; then
        JAVA_SDK_PATH="$JDK8_PATH"
        export JAVA_HOME="$JDK8_PATH"
        echo "Java SDK (JDK 8): $JAVA_SDK_PATH"
    elif [ -n "$JAVA_HOME" ]; then
        JAVA_SDK_PATH="$JAVA_HOME"
        echo "Warning: JDK 8 not found, using JAVA_HOME: $JAVA_HOME"
        echo "Build may fail with newer Java versions (Gradle 2.10 requires Java 8)"
    else
        echo "ERROR: JAVA_HOME environment variable is not set and JDK 8 not found."
        exit 1
    fi
}

# ::::::::::::::::::::::::::::::::
# :: Build APK                  ::
# ::::::::::::::::::::::::::::::::
build_apk() {
    echo ""
    echo "Building release APK..."
    echo ""

    # Write sdk.dir to local.properties
    echo "sdk.dir=$ANDROID_SDK_PATH" > "$PROPERTIES_FILE"
    
    # Build .apk file
    cd "$CURR_PATH"
    ./gradlew $GRADLE_ARGUMENTS
    
    echo ""
    echo "Build completed successfully."
}

# ::::::::::::::::::::::::::::::::
# :: Sign APK                   ::
# ::::::::::::::::::::::::::::::::
sign_apk() {
    echo ""
    echo "Signing APK..."
    echo ""

    PATH_TO_APK="$CURR_PATH/app/build/outputs/apk/"
    UNSIGNED_APK="${PATH_TO_APK}app-release-unsigned.apk"
    ALIGNED_APK="${PATH_TO_APK}app-release-aligned.apk"
    SIGNED_APK="${PATH_TO_APK}app-release-signed.apk"

    # Find build-tools version
    BUILD_TOOLS_DIR="$ANDROID_SDK_PATH/build-tools"
    BUILD_TOOLS_VERSION=$(ls -1 "$BUILD_TOOLS_DIR" | sort -V | tail -n 1)
    APKSIGNER="$BUILD_TOOLS_DIR/$BUILD_TOOLS_VERSION/apksigner"
    ZIPALIGN="$BUILD_TOOLS_DIR/$BUILD_TOOLS_VERSION/zipalign"

    if [ ! -f "$APKSIGNER" ]; then
        echo "ERROR: apksigner not found at $APKSIGNER"
        exit 1
    fi

    if [ ! -f "$UNSIGNED_APK" ]; then
        echo "ERROR: Unsigned APK not found at $UNSIGNED_APK"
        exit 1
    fi

    # Zipalign the APK
    echo "Aligning APK..."
    "$ZIPALIGN" -v -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"

    # Sign with apksigner
    echo "Signing APK with apksigner..."
    "$APKSIGNER" sign \
        --ks "$KEYSTORE_FILE" \
        --ks-key-alias "$KEY_ALIAS" \
        --ks-pass "pass:$KEYSTORE_PASSWORD" \
        --key-pass "pass:$KEY_PASSWORD" \
        --out "$SIGNED_APK" \
        "$ALIGNED_APK"

    # Verify the signature
    echo "Verifying signature..."
    "$APKSIGNER" verify --verbose "$SIGNED_APK"

    # Clean up intermediate files
    rm -f "$ALIGNED_APK"
    rm -f "$UNSIGNED_APK"

    # Move and rename the signed APK
    mv "$SIGNED_APK" "$CURR_PATH/PicFrame-release.apk"

    echo ""
    echo "=================================="
    echo "  Build Complete!"
    echo "=================================="
    echo ""
    echo "Signed APK: $CURR_PATH/PicFrame-release.apk"
    echo ""
}

# ::::::::::::::::::::::::::::::::
# :: Main script execution      ::
# ::::::::::::::::::::::::::::::::
main() {
    load_keystore_properties
    detect_android_sdk
    detect_java_sdk
    build_apk
    sign_apk
}

# Run main function
main
