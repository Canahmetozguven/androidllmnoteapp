#!/bin/bash
# Add Android SDK environment variables to ~/.bashrc

if ! grep -q 'ANDROID_HOME' ~/.bashrc; then
    echo '' >> ~/.bashrc
    echo '# Android SDK (added by setup script)' >> ~/.bashrc
    echo 'export ANDROID_HOME=$HOME/android-sdk' >> ~/.bashrc
    echo 'export ANDROID_SDK_ROOT=$ANDROID_HOME' >> ~/.bashrc
    echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin' >> ~/.bashrc
    echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools' >> ~/.bashrc
    echo "Environment variables added to ~/.bashrc"
else
    echo "ANDROID_HOME already in ~/.bashrc"
fi

# Source it for current session
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

# Accept licenses and install SDK components
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses 2>/dev/null || true

echo "Installing SDK components..."
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;26.1.10909125"

echo "SDK setup complete!"
