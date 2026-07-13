# Test Brainy on Android

1. Open the latest **Build Brainy Android APK** workflow run.
2. Download the **brainy-debug-apk** artifact and unzip it.
3. Uninstall the previous Brainy test APK, then install the new `app-debug.apk`.
4. Open Brainy and allow notifications.
5. Tap **Test notification**. A native notification should appear after five seconds.
6. Add: “Remind me in 10 minutes to email ravi@example.com about the report.”

Brainy’s interface is packaged inside the APK, so no ChatGPT login is required. Responsibilities are kept locally on the phone. Internet is required only for OpenAI understanding through the protected Supabase Edge Function; local capture still works if AI is unavailable.
