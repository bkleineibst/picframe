#!/bin/bash
# :::::::::::::::::::::::::::::::::::::::::::
# ::     SCRIPT TO CREATE PICFRAME APK     ::
# :::::::::::::::::::::::::::::::::::::::::::

clear
echo ""
echo "============================="
echo "  PicFrame Build Script"
echo "============================="
echo ""

# :::VARIABLES:::
CURR_PATH="$(cd "$(dirname "$0")" && pwd)"

# Android SDK Version and Path
PLATFORM_VERSION_NEEDED=21
PLATFORM_VERSION_NEEDED_STRING="Android 5.0.1"
SUPPORT_V4_STRING="support-v4:23.0.1"
SUPPORT_V7_STRING="appcompat-v7:21.0.0"

# Build options
APK_TYPE="DEBUG"
GRADLE_ARGUMENTS="assembleDebug"
FILENAME_INFO="debug"
PROPERTIES_FILE="$CURR_PATH/local.properties"

# Keystore variables
KEYSTORE_FILE=""
KEY_ALIAS=""

EVERYTHING_OK=true

# Function to prompt user for input
prompt_input() {
    local prompt_message="$1"
    local input_var
    read -p "$prompt_message" input_var
    echo "$input_var"
}

# Function to wait for keypress
wait_for_key() {
    read -n 1 -s -r -p "Press any key to continue..."
    echo ""
}

# Function to prompt yes/no choice
prompt_choice() {
    local prompt_message="$1"
    local choice
    while true; do
        read -p "$prompt_message [y/n]: " choice
        case "$choice" in
            [Yy]* ) return 0;;
            [Nn]* ) return 1;;
            * ) echo "Please answer y or n.";;
        esac
    done
}

# Function to prompt for debug/release
prompt_build_type() {
    local choice
    while true; do
        read -p "Do you want to build a [d]ebug or [r]elease apk? [d/r]: " choice
        case "$choice" in
            [Dd]* ) echo "debug"; return;;
            [Rr]* ) echo "release"; return;;
            * ) echo "Please answer d or r.";;
        esac
    done
}

# ::::::::::::::::::::::::::::::::
# :: Check for ANDROID_HOME     ::
# ::::::::::::::::::::::::::::::::
check_android_home() {
    echo "Welcome to the PicFrame build script."
    echo "It prepares and creates a picframe apk."
    echo ""
    echo "To start off, please verify the following directories:"
    
    if [ -n "$ANDROID_HOME" ]; then
        echo "Android SDK...."
        echo "ANDROID_HOME: \"$ANDROID_HOME\""
        ANDROID_SDK_PATH="$ANDROID_HOME"
        wait_for_key
    elif [ -n "$ANDROID_SDK_ROOT" ]; then
        echo "Android SDK...."
        echo "ANDROID_SDK_ROOT: \"$ANDROID_SDK_ROOT\""
        ANDROID_SDK_PATH="$ANDROID_SDK_ROOT"
        wait_for_key
    else
        echo "ANDROID_HOME environment variable is not set."
        ANDROID_SDK_PATH=$(prompt_input "Please enter the path to your android sdk: ")
        if [ -z "$ANDROID_SDK_PATH" ]; then
            echo "No path provided. Exiting."
            exit 1
        fi
    fi
    
    PLATFORM_PATH="$ANDROID_SDK_PATH/platforms/android-$PLATFORM_VERSION_NEEDED/"
    echo "$PLATFORM_PATH"
}

# ::::::::::::::::::::::::::::::::
# :: Check for JAVA_HOME        ::
# ::::::::::::::::::::::::::::::::
check_java_home() {
    # For this old project (Gradle 2.10), we need JDK 8
    JDK8_PATH=$(/usr/libexec/java_home -v 1.8 2>/dev/null)
    
    if [ -n "$JDK8_PATH" ]; then
        echo "Java directory...."
        echo "Using JDK 8 (required for Gradle 2.10): \"$JDK8_PATH\""
        JAVA_SDK_PATH="$JDK8_PATH"
        export JAVA_HOME="$JDK8_PATH"
        wait_for_key
    elif [ -n "$JAVA_HOME" ]; then
        echo "Java directory...."
        echo "JAVA_HOME: \"$JAVA_HOME\""
        echo "Warning: This project requires JDK 8 for Gradle 2.10"
        JAVA_SDK_PATH="$JAVA_HOME"
        wait_for_key
    else
        echo "JAVA_HOME environment variable is not set."
        JAVA_SDK_PATH=$(prompt_input "Please enter the path to your java jdk folder: ")
        if [ -z "$JAVA_SDK_PATH" ]; then
            echo "No path provided. Exiting."
            exit 1
        fi
    fi
}

# ::::::::::::::::::::::::::::::::
# :: Check platform version     ::
# ::::::::::::::::::::::::::::::::
check_platform_version() {
    clear
    echo "Checking for the required android platform version."
    echo "$PLATFORM_PATH"
    
    while [ ! -d "$PLATFORM_PATH" ]; do
        echo "Couldn't find the required android platform version."
        echo "Please download '$PLATFORM_VERSION_NEEDED_STRING -- API $PLATFORM_VERSION_NEEDED'."
        echo "Once successfully downloaded, please press any key."
        wait_for_key
    done
    
    echo "Required version '$PLATFORM_VERSION_NEEDED_STRING -- API $PLATFORM_VERSION_NEEDED' found."
    echo ""
}

# ::::::::::::::::::::::::::::::::
# :: Check support libraries    ::
# ::::::::::::::::::::::::::::::::
check_support_libraries() {
    SUPPORT_LIBRARY_V4_PATH="$ANDROID_SDK_PATH/extras/android/m2repository/com/android/support/support-v4/23.0.1/"
    SUPPORT_LIBRARY_V7_PATH="$ANDROID_SDK_PATH/extras/android/m2repository/com/android/support/appcompat-v7/21.0.0/"
    
    echo "Checking for the required support libraries..."
    
    # Check support-v4
    echo "Checking for '$SUPPORT_V4_STRING'..."
    while [ ! -d "$SUPPORT_LIBRARY_V4_PATH" ]; do
        echo "Couldn't find the support library at $SUPPORT_LIBRARY_V4_PATH"
        echo "Please download Android Support Repository."
        echo "Once successfully downloaded, please press any key."
        wait_for_key
    done
    echo "Required support library '$SUPPORT_V4_STRING' found."
    echo ""
    
    # Check support-v7
    echo "Checking for '$SUPPORT_V7_STRING'..."
    while [ ! -d "$SUPPORT_LIBRARY_V7_PATH" ]; do
        echo "Couldn't find the support library at $SUPPORT_LIBRARY_V7_PATH"
        echo "Please download Android Support Repository."
        echo "Once successfully downloaded, please press any key."
        wait_for_key
    done
    echo "Required support library '$SUPPORT_V7_STRING' found."
    wait_for_key
}

# ::::::::::::::::::::::::::::::::
# :: Create new keystore        ::
# ::::::::::::::::::::::::::::::::
create_new_keystore() {
    clear
    echo "For now the new keyStore file will be in this directory."
    echo "After this script finished, feel free to move it."
    
    KEYSTORE_NAME=$(prompt_input "Please enter a name for the new keyStore: ")
    if [ -z "$KEYSTORE_NAME" ]; then
        create_new_keystore
        return
    fi
    KEYSTORE_FILE="$KEYSTORE_NAME.keystore"
    
    KEY_ALIAS=$(prompt_input "Please enter a new alias for the key in the keyStore: ")
    if [ -z "$KEY_ALIAS" ]; then
        create_new_keystore
        return
    fi
    
    echo "New key will now be generated: KeystoreFile:$KEYSTORE_FILE - Alias:$KEY_ALIAS"
    echo "$JAVA_SDK_PATH..."
    
    # Generate Keystore file
    "$JAVA_SDK_PATH/bin/keytool" -genkey -v -keystore "$CURR_PATH/$KEYSTORE_FILE" -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000
    
    if [ $? -ne 0 ]; then
        echo "Key generation failed, keytool exited with an error."
        exit 1
    fi
}

# ::::::::::::::::::::::::::::::::
# :: Use existing keystore      ::
# ::::::::::::::::::::::::::::::::
use_existing_keystore() {
    KEYSTORE_FILE=$(prompt_input "Please enter the full path plus extension to your keystore file: ")
    if [ -z "$KEYSTORE_FILE" ]; then
        use_existing_keystore
        return
    fi
    
    KEY_ALIAS=$(prompt_input "Please enter the alias for the key in the keyStore: ")
    if [ -z "$KEY_ALIAS" ]; then
        use_existing_keystore
        return
    fi
}

# ::::::::::::::::::::::::::::::::
# :: Handle release build       ::
# ::::::::::::::::::::::::::::::::
handle_release_build() {
    APK_TYPE="RELEASE"
    GRADLE_ARGUMENTS="assembleRelease"
    
    echo "To build a release apk, you first need to"
    echo "create a keystore file with a key and a password."
    
    if prompt_choice "Do you want to use an existing .keystore file?"; then
        use_existing_keystore
    else
        create_new_keystore
    fi
}

# ::::::::::::::::::::::::::::::::
# :: Build APK                  ::
# ::::::::::::::::::::::::::::::::
build_apk() {
    echo "Creating/Overwriting local.properties file."
    echo "Writing '$ANDROID_SDK_PATH' into properties file."
    
    # Write sdk.dir to local.properties (escape colons and backslashes for properties file format)
    echo "sdk.dir=$ANDROID_SDK_PATH" > "$PROPERTIES_FILE"
    
    # Build .apk file
    cd "$CURR_PATH"
    
    # Use JDK 8 for building (old Gradle 2.10 doesn't support newer Java versions)
    # Check for JDK 8 first, then fall back to JAVA_HOME
    JDK8_PATH=$(/usr/libexec/java_home -v 1.8 2>/dev/null)
    if [ -n "$JDK8_PATH" ]; then
        echo "Using JDK 8 for Gradle build: $JDK8_PATH"
        export JAVA_HOME="$JDK8_PATH"
    else
        echo "Warning: JDK 8 not found. Build may fail with newer Java versions."
        echo "This project uses Gradle 2.10 which requires Java 8."
    fi
    
    ./gradlew $GRADLE_ARGUMENTS
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "Build failed, gradlew exited with an error."
        exit 1
    fi
    
    echo ""
    echo ""
    
    PATH_TO_APK="$CURR_PATH/app/build/outputs/apk/"
    OLD_FILENAME="app-release-unsigned.apk"
    
    if [ "$APK_TYPE" = "RELEASE" ]; then
        # Use apksigner instead of jarsigner for modern Android compatibility
        # apksigner is located in the Android SDK build-tools
        APKSIGNER="$ANDROID_SDK_PATH/build-tools/30.0.3/apksigner"
        ZIPALIGN="$ANDROID_SDK_PATH/build-tools/30.0.3/zipalign"
        
        ALIGNED_APK="${PATH_TO_APK}app-release-aligned.apk"
        SIGNED_APK="${PATH_TO_APK}app-release-signed.apk"
        
        # First, zipalign the APK (required before signing with apksigner)
        echo "Aligning APK..."
        "$ZIPALIGN" -v -p 4 "$PATH_TO_APK$OLD_FILENAME" "$ALIGNED_APK"
        
        if [ $? -ne 0 ]; then
            echo "Zipalign failed."
            exit 1
        fi
        
        # Sign with apksigner (uses SHA-256 by default)
        echo "Signing APK with apksigner..."
        echo "Enter keystore password when prompted:"
        "$APKSIGNER" sign --ks "$KEYSTORE_FILE" --ks-key-alias "$KEY_ALIAS" --out "$SIGNED_APK" "$ALIGNED_APK"
        
        if [ $? -ne 0 ]; then
            echo "Signing of the apk failed, apksigner exited with an error."
            exit 1
        fi
        
        # Verify the signature
        echo "Verifying signature..."
        "$APKSIGNER" verify --verbose "$SIGNED_APK"
        
        # Clean up intermediate files
        rm -f "$ALIGNED_APK"
        rm -f "$PATH_TO_APK$OLD_FILENAME"
        
        FILENAME_INFO="signed"
    fi
    
    echo ""
    echo ""
    
    # Move the .apk into project root
    if [ "$APK_TYPE" = "RELEASE" ]; then
        mv "${PATH_TO_APK}"app-release-signed.apk ./
    else
        mv "$PATH_TO_APK"app-debug.apk ./
    fi
    
    if [ $? -ne 0 ]; then
        echo "Moving the file failed, please check this directory:"
        echo "./app/build/outputs/apk/"
        EVERYTHING_OK=false
    fi
    
    # Rename the .apk to a more appealing filename
    for apk in ./app*.apk; do
        if [ -f "$apk" ]; then
            mv "$apk" "./PicFrame-$FILENAME_INFO.apk"
            break
        fi
    done
    
    if [ $? -ne 0 ]; then
        echo "Renaming the file failed, please check for an .apk file"
        EVERYTHING_OK=false
    fi
    
    if [ "$EVERYTHING_OK" = true ]; then
        echo "Build finished, your generated .apk file should be located in:"
        echo ": $CURR_PATH"
    fi
    
    echo ""
    echo "Thank you for using this script."
    echo "We hope you enjoy our app 'PicFrame'."
}

# ::::::::::::::::::::::::::::::::
# :: Main script execution      ::
# ::::::::::::::::::::::::::::::::
main() {
    check_android_home
    check_java_home
    check_platform_version
    check_support_libraries
    
    clear
    echo ""
    BUILD_TYPE=$(prompt_build_type)
    
    if [ "$BUILD_TYPE" = "release" ]; then
        handle_release_build
    fi
    
    build_apk
    
    echo ""
    echo "Press any key to exit this script."
    wait_for_key
}

# Run main function
main
