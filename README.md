# ANON AI ULTRA

Kotlin + Jetpack Compose Android AI workspace.

## First foundation release

- Black and red interface with green status accents
- Chat, ANON CODER, Models, and Settings navigation
- Model family catalogue and local model selection
- TikTok link in Settings: https://www.tiktok.com/@anonymous.1797
- GitHub Actions workflow that builds a debug APK

## Current status

This first commit provides the Android interface and build pipeline. AI providers are **not connected yet**. Chat and coding actions clearly report that no request is sent until a secure backend and provider credentials are configured.

Free model availability, rate limits, and terms depend on each provider. API keys must be stored on the backend, never inside the APK.

## Build

GitHub Actions builds `:app:assembleDebug` on pushes to `main` and on manual runs. Download the `ANON-AI-ULTRA-debug` artifact from a successful workflow run.
