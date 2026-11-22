# ANON AI ULTRA

Kotlin + Jetpack Compose Android AI workspace.

## Features

- Black and red interface with green status accents
- Chat, ANON CODER, Models, and Settings navigation
- Firebase AI Logic with Gemini Developer API
- Gemini 3.8 Flash and Gemini 3.5 Flash-Lite model choices
- No user-entered API key required
- App Check debug provider for development builds
- Play Integrity provider for release builds
- GitHub Actions workflow builds and uploads a debug APK

## AI access and quotas

The app calls Firebase AI Logic directly using the Firebase project configuration. Users do not enter a Gemini or Groq API key.

Model availability, free-tier quotas, rate limits, and billing requirements are controlled by Google and can change. This app does not promise unlimited free usage. Protect the Firebase project with App Check and monitor usage in the Firebase console.

## App Check

- Debug builds use the Firebase App Check debug provider. Obtain the debug secret from the device logs (Logcat) and register it under Firebase Console > App Check. Never publish this secret.
- Release builds use Play Integrity. Configure the Android app's Play Integrity provider in Firebase Console before enforcing App Check.
- Do not enable enforcement until the debug and release configurations have been verified.

## Build

GitHub Actions runs `:app:assembleDebug` on pushes to `main` and on manual runs. Download the `ANON-AI-ULTRA-debug` artifact from a successful workflow run.
