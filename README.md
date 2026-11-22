# ANON AI ULTRA

Kotlin + Jetpack Compose Android AI workspace.

## Features

- Black and red interface with green status accents
- Chat, ANON CODER, Models, and Settings navigation
- Gemini and Groq provider integration
- Model selection for Gemini 2.5 Flash, Gemini 2.5 Flash Lite, Groq Llama 3.1 8B Instant, and Groq GPT OSS 20B
- Separate Gemini and Groq API key fields in Settings
- API keys encrypted locally with Android Keystore (AES/GCM)
- ANON CODER uses Groq Llama 3.1 8B Instant
- TikTok link in Settings: https://www.tiktok.com/@anonymous.1797
- GitHub Actions workflow builds and uploads a debug APK

## API keys

Add your own Gemini and/or Groq API key in the app's Settings screen. Keys are stored encrypted on the device. Do not commit API keys to the repository.

Free model availability, rate limits, and provider terms can change. API requests are sent directly from the app to the selected provider.

## Build

GitHub Actions runs `:app:assembleDebug` on pushes to `main` and on manual runs. Download the `ANON-AI-ULTRA-debug` artifact from a successful workflow run.
