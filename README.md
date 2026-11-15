# VantaCode

AI-powered coding assistant for Android. Built on AiCode with extended capabilities.

## Features

- **AI Chat & Code Editing** — Full-featured AI assistant with code editing, file management, and terminal
- **DSH Plugin Market** — Browse and install 8000+ DeepSeek Harness plugins directly from the app
- **DSH Go Plugin Support** — Native `.dshgo.json` format for themes, pets, backgrounds, and custom instructions
- **Team Collaboration** — Multi-agent team chat with task dispatch and executor delegation
- **Skill System** — Extensible skill framework with online skill installation
- **Phone Control** — AI-driven phone automation (tap, swipe, type, screenshot)
- **Dynamic Backgrounds** — GIF/MP4 animated wallpapers with parallax
- **Desktop Pet** — Interactive desktop companion
- **App Picker** — Upload and manage Android apps from the chat
- **Multi-Model Support** — Configure multiple AI providers and models

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM + Hilt DI
- **Min SDK**: 26 (Android 8.0)
- **Build**: Gradle (armsolo variant)

## Building

```bash
# Set environment
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=/root/android/sdk

# Build
./gradlew assembleArmsoloRelease

# Output
# app/build/outputs/apk/armsolo/release/app-armsolo-release.apk
```

## Project Structure

```
app/src/main/java/com/vantacode/
├── core/                    # Core utilities, theme, network
├── feature/
│   ├── agent/               # AI agent, tools, skills
│   ├── chat/                # Chat UI, message rendering
│   ├── code/                # Code editor, file tree
│   ├── settings/            # Settings, plugin market
│   ├── terminal/            # Terminal emulator
│   ├── teams/               # Team collaboration
│   └── phone/               # Phone control tools
└── MainActivity.kt
```

## Plugin Ecosystem

VantaCode integrates with the DeepSeek Harness (DSH) plugin ecosystem:

- **Standard DSH Plugins** — npm/TypeScript format, install via `dsh plugin add`
- **DSH Go Plugins** — `.dshgo.json` format for mobile UI extensions
- **Market Source** — [dsh-plugin-catalog](https://www.npmjs.com/package/dsh-plugin-catalog) (8000+ entries)

## License

Based on [AiCode](https://github.com/) (GPL-3.0). See LICENSE for details.

## Credits

- Built on AiCode open-source project
- DSH plugin catalog by the DeepSeek Harness community
- DSH Go plugin format by [ccch1mneyyy/dsh-go](https://github.com/ccch1mneyyy/dsh-go)
