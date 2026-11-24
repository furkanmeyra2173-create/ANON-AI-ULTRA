# ANON AI ULTRA

Kotlin + Jetpack Compose Android AI workspace.

## Features

- Black and red interface with green status accents
- Chat, ANON CODER, Models, and Settings navigation
- Firebase AI Logic with Gemini Developer API
- Gemini model choices
- No user-entered API key required
- Anonymous Firebase Authentication for a stable app user ID
- Shared daily AI quota for Chat and ANON CODER: 30 requests per user per rolling 24-hour window
- Realtime Database transactions plus server-side security rules enforce the quota, a 10-requests-per-5-minute burst cap, and a 2-second minimum interval
- App Check debug provider for development builds
- Play Integrity provider for release builds
- GitHub Actions workflow builds and uploads a debug APK

## Daily usage limit setup

The app uses Firebase Authentication anonymous accounts and Firebase Realtime Database. The quota is keyed by the authenticated Firebase UID. Chat and ANON CODER share the same counter. The 24-hour window starts with the user's first request and resets 24 hours later.

1. In Firebase Console, open **Authentication > Sign-in method** and enable **Anonymous**.
2. Open **Realtime Database** and create a database for project `ultra-ai-8e44d`.
3. In the Realtime Database **Rules** tab, publish the rules from `database.rules.json`.
4. Download a fresh `google-services.json` after creating the database, then replace `app/google-services.json` in this repository so the database URL is included.
5. Build the app again with GitHub Actions.

The rules allow each authenticated user to read only their own counter and validate every increment on the database server. They enforce a maximum of 30 requests per rolling 24 hours, at most 10 requests in each 5-minute usage window (the window starts with its first accepted request), and at least 2 seconds between accepted requests. The app reserves one request before calling Gemini, so a failed Gemini attempt also uses one request.

Anonymous UIDs persist with the app's local Firebase Auth state, but clearing app data or reinstalling may create a new anonymous UID. App Check with Play Integrity should be configured for the Android release app, then enforcement should be enabled for Realtime Database and Firebase AI Logic before public release. Register the debug token before testing an enforced debug build. App Check helps reject requests that do not come from a genuine app/device. App Check is not a complete identity system, and anonymous-account rotation cannot be fully prevented without linking users to a verified sign-in provider. Do not claim these controls eliminate all abuse.

## Pricing

Firebase Realtime Database includes a no-cost Spark allowance (1 GB stored and 10 GB/month downloaded at the time this README was written). If the project exceeds Firebase's current no-cost limits, service availability may be restricted on Spark. Check the current Firebase pricing page before launch.

## App Check

- Debug builds use the Firebase App Check debug provider. Obtain the debug secret from device logs (Logcat) and register it under Firebase Console > App Check. Never publish this secret.
- Release builds use Play Integrity. Configure the Android app's Play Integrity provider in Firebase Console before enforcing App Check.
- Do not enable enforcement until the debug and release configurations have been verified.

## Build

GitHub Actions runs `:app:assembleDebug` on pushes to `main` and on manual runs. Download the `ANON-AI-ULTRA-debug` artifact from a successful workflow run.

## Experimental ANON model training

The separate training/ directory contains an experimental Turkish base-model pipeline initialized from random weights, a Kaggle notebook, and setup instructions. This is the start of the ANON-owned model effort; it is not yet a production conversational assistant. The Android app remains on Firebase AI Logic/Gemini until an ANON model passes evaluation and Android integration tests. Read training/README.md before starting a GPU session.
