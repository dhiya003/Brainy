# Test Brainy on Android

1. Open the latest **Build Brainy Android APK** workflow run.
2. Download the **brainy-debug-apk** artifact and unzip it.
3. Install `app-debug.apk` on the Android phone. Allow installation from the browser/files app if prompted.
4. Open Brainy and allow notifications.
5. Sign in to the hosted Brainy app if requested.

This is a test/debug APK. It loads the current secure hosted Brainy interface and exposes a native JavaScript bridge for exact Android reminder scheduling and external app intents. Payment and message actions must always require explicit confirmation.
