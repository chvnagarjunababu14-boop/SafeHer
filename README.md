# SafeHer-Next

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Platform](https://img.shields.io/badge/Android-SDK%2026%20..%2035-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-FF6F00.svg)](https://developer.android.com/topic/architecture)

**SafeHer-Next** is a modern, privacy-first Android application designed to provide women with immediate safety assistance, emergency SOS signaling, automated check-ins, and trusted guardian alerts. 

Built from the ground up using **Kotlin**, **Jetpack Compose**, and **Material 3**, SafeHer-Next delivers an accessible, calm, and reliable user experience when it matters most.

---

## 🎨 Visual Design System

SafeHer's design prioritizes clarity, calm reassurance, and rapid accessibility during high-stress scenarios:

| Element | Color / Token | Hex | Purpose |
| :--- | :--- | :--- | :--- |
| **Primary** | `SafeHerPurplePrimary` | `#7C3AED` | Core action buttons, brand identity, navigation |
| **Primary Dark** | `SafeHerPurpleDark` | `#5B21B6` | Pressed states, high-contrast headers |
| **Accent Pink** | `SafeHerPinkAccent` | `#EC4899` | Highlights, safety badges, active indicators |
| **Background** | `SafeHerLavenderBackground` | `#F8F6FD` | Soothing light lavender canvas minimizing visual fatigue |
| **Card Surface** | `SafeHerSurfaceWhite` | `#FFFFFF` | Elevated cards with rounded corners (16–24dp) |
| **Border** | `SafeHerBorderLavender` | `#E5DEFF` | Subtle card dividers and boundary lines |
| **Text Primary** | `SafeHerTextPrimary` | `#1E1B4B` | High-contrast indigo for crystal-clear readability |
| **Status Safe** | `SafeHerStatusSafe` | `#10B981` | Positive guardian connection and safe status |
| **Emergency** | `SafeHerStatusEmergency` | `#EF4444` | High-visibility emergency actions |

---

## 🏗️ Architecture & Tech Stack

- **Language:** Kotlin 2.3.x
- **UI Framework:** 100% Jetpack Compose with Material 3 design tokens
- **Architecture:** Model-View-ViewModel (MVVM) with StateFlow and Unidirectional Data Flow (UDF)
- **Dependency Management:** Gradle Version Catalog (`gradle/libs.versions.toml`)
- **Build System:** Gradle 9.6+ with Gradle Kotlin DSL (`.gradle.kts`)
- **Target Specifications:**
  - `minSdk`: **26** (Android 8.0 Oreo — guarantees background job reliability and notification channels)
  - `targetSdk`: **35** (Android 15)
  - `compileSdk`: **35**
  - Package Name: `com.safeher.app`

---

## 📂 Project Structure

```text
SafeHer/
├── app/
│   ├── build.gradle.kts                   # Module build configuration
│   ├── proguard-rules.pro                 # Release obfuscation rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # Application declarations
│       │   ├── java/com/safeher/app/
│       │   │   ├── MainActivity.kt        # Single activity host
│       │   │   └── ui/
│       │   │       ├── screens/welcome/   # Welcome screen (Phase 1)
│       │   │       │   ├── WelcomeScreen.kt
│       │   │       │   └── WelcomeViewModel.kt
│       │   │       └── theme/             # Design system
│       │   │           ├── Color.kt
│       │   │           ├── Shape.kt
│       │   │           ├── Theme.kt
│       │   │           └── Type.kt
│       │   └── res/                       # Drawables, mipmaps, values
│       └── test/                          # Unit tests
├── docs/
│   ├── PROJECT_PLAN.md                    # Roadmap across development phases
│   └── SECURITY_AND_PRIVACY.md            # Privacy policies and threat mitigations
├── gradle/
│   ├── libs.versions.toml                 # Centralized dependency catalog
│   └── wrapper/                           # Gradle wrapper definitions
├── build.gradle.kts                       # Root project build script
├── settings.gradle.kts                    # Root project settings
├── gradle.properties                      # JVM and compilation flags
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

1. **Android Studio** Ladybug Feature Drop (2024.2+) or Android Studio 2026+.
2. **JDK 17 or higher** (Bundled JetBrains Runtime JBR 21/25 in Android Studio recommended).
3. **Android SDK Platform 35** and **Build Tools**.

### Build Flavors

SafeHer provides two dedicated build variants:

| Variant | Purpose | Firebase Credentials Needed? |
|---|---|---|
| `demoDebug` | **Self-Contained Demo Mode** (fictional profiles, simulated FCM & guardian dispatch, 100% offline-ready) | **No** (Zero setup required) |
| `prodDebug` | **Production Mode** (Firebase Auth, Cloud Firestore, Cloud Functions, real FCM notifications) | **Yes** (`google-services.json` required) |

For details on demo mode, architecture, and testing walkthrough, see [DEMO_MODE.md](file:///DEMO_MODE.md).

### Build and Run via Command Line

```powershell
# Check Gradle wrapper version
.\gradlew.bat --version

# Run Unit Tests (Demo Variant)
.\gradlew.bat testDemoDebugUnitTest

# Assemble Demo Debug APK (No Firebase setup needed)
.\gradlew.bat assembleDemoDebug

# Assemble Production Debug APK (Requires configured Firebase)
.\gradlew.bat assembleProdDebug
```

The generated APKs will be output to:
- Demo: `app/build/outputs/apk/demo/debug/app-demo-debug.apk`
- Prod: `app/build/outputs/apk/prod/debug/app-prod-debug.apk`

---

## 🛡️ Key Features & Capabilities

- **Emergency SOS & Location Handoff:** User-confirmed emergency messaging with Android system SMS composer handoff and clipboard fallback.
- **Remote Guardian Alerts:** Authenticated remote guardian circle with progressive delivery status tracking (`Queued`, `FCM Transit`, `App Receipt`, `Acknowledged`).
- **Safety Journeys & Check-ins:** Timed journeys with milestone check-ins, event timelines, and safety status monitoring.
- **Trusted Local Guardians:** Fast access to emergency contacts stored locally on-device in Room database.
- **Self-Contained Demo Mode:** Believable local mock architecture enabling immediate evaluation without personal cloud credentials or cost.
- **Privacy First:** Explicit consent, foreground-only location access, zero background tracking without user knowledge, and strict data minimization.

