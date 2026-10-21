# SafeHer — Everyday Development Handbook

This document provides a verified reference of everyday developer commands, Gradle tasks, build configurations, and testing workflows for the **SafeHer** project.

---

## 1. Key Architectural Distinctions

Before executing commands, understand the three critical dimensions of the project's build architecture:

### A. Demo vs. Production Flavors
- **`demo` Flavor:**
  - **Purpose:** Self-contained evaluation, rapid UI development, offline testing, automated unit tests.
  - **Backend:** Mock repositories (`DemoAuthRepository`, `DemoRemoteGuardianRepository`, `DemoRemoteSosRepository`).
  - **Cloud Dependency:** **None.** Does not require Firebase initialization, internet connection, or `google-services.json`.
  - **Default Setting:** `IS_DEMO_DEFAULT = true`.
- **`prod` Flavor:**
  - **Purpose:** Cloud integration, real device-to-device alerts, production deployments.
  - **Backend:** Live Firebase infrastructure (`FirebaseAuthRepository`, `FirestoreGuardianRepository`, `RemoteSosRepositoryImpl`).
  - **Cloud Dependency:** Requires valid `app/google-services.json` and a deployed Firebase project.
  - **Default Setting:** `IS_DEMO_DEFAULT = false`.

### B. Debug vs. Release Build Types
- **`debug`:**
  - Signed with the default Android debug keystore (`~/.android/debug.keystore`).
  - `debuggable = true`, allowing full Android Studio debugger and Logcat inspection.
  - Includes Compose preview tooling (`ui-tooling`).
- **`release`:**
  - `isMinifyEnabled = false` (configured in `app/build.gradle.kts`; ready for R8 obfuscation rules in `app/proguard-rules.pro`).
  - Requires a private signing keystore and production passwords (never committed).

### C. Building an APK vs. Installing
- **`assemble<Variant>`:** Compiles source code, resources, and libraries into a standalone `.apk` package file on disk. Does not require a device to be connected.
- **`install<Variant>`:** Assembles the APK and deploys it directly to a connected Android handset or running emulator via `adb`.

### D. Unit Tests vs. Instrumentation Tests
- **Unit Tests (`src/test/`):** Run locally on the JVM using mock objects. Extremely fast (3–5 seconds for 153 tests) and require no device or emulator.
- **Instrumentation Tests (`src/androidTest/`):** Run on a physical Android device or emulator with a full Android OS runtime. Used for UI assertions and end-to-end flows.

---

## 2. Essential Gradle Commands (Verified)

Always run Gradle commands from the root repository directory (`C:\...\SafeHer`). Ensure `JAVA_HOME` points to JDK 17+ (e.g., Android Studio's bundled JBR).

### One-Time Environment Setup in PowerShell
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
```

### Running Automated Tests
```powershell
# Run all unit tests for the Demo flavor (153 tests)
.\gradlew.bat testDemoDebugUnitTest

# Run all unit tests for the Production flavor (153 tests)
.\gradlew.bat testProdDebugUnitTest

# Run test alias (points to testDemoDebugUnitTest)
.\gradlew.bat testDebugUnitTest
```

### Building APKs
```powershell
# Assemble the self-contained Demo Debug APK (Output: app/build/outputs/apk/demo/debug/app-demo-debug.apk)
.\gradlew.bat assembleDemoDebug

# Assemble the Production Debug APK (Output: app/build/outputs/apk/prod/debug/app-prod-debug.apk)
.\gradlew.bat assembleProdDebug

# Assemble Demo Release APK
.\gradlew.bat assembleDemoRelease
```

### Installing Directly to Device or Running Emulator
Ensure your device is connected (`$adb devices -l`):
```powershell
# Install Demo Debug APK
.\gradlew.bat installDemoDebug

# Install Production Debug APK
.\gradlew.bat installProdDebug

# Uninstall Demo Debug from device
.\gradlew.bat uninstallDemoDebug

# Uninstall all variants from device
.\gradlew.bat uninstallAll
```

### Inspecting Tasks & Project Configuration
```powershell
# View all available tasks categorized by group
.\gradlew.bat tasks

# View all install-related tasks
.\gradlew.bat tasks --group="Install"

# View all verification/test tasks
.\gradlew.bat tasks --group="Verification"

# View all build tasks
.\gradlew.bat tasks --group="Build"

# Display dependency tree for the app module
.\gradlew.bat :app:dependencies --configuration demoDebugRuntimeClasspath

# Generate signing report (retrieves SHA-1 and SHA-256 fingerprints for Firebase)
.\gradlew.bat signingReport
```

---

## 3. Cloud Functions Backend Commands (TypeScript)

The Cloud Functions backend is located in the `functions/` subdirectory.

```powershell
# Navigate to functions directory
Set-Location -Path functions

# Install dependencies
npm install

# Compile TypeScript to JavaScript (into functions/lib/)
npm run build

# Run TypeScript compilation in watch mode (auto-recompiles on edit)
npm run build:watch

# Run Cloud Functions backend unit tests (12 tests)
npm test

# Run local Firebase Emulator Suite (Functions & Firestore)
npm run serve

# Deploy functions to Firebase (requires Firebase CLI login and Blaze plan)
firebase deploy --only functions

# Return to root directory
Set-Location -Path ..
```

---

## 4. ADB Command Quick-Reference

ADB (`Android Debug Bridge`) is located in `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# List connected physical devices and running emulators
& $adb devices -l

# Install an APK with reinstall/replace flag (-r)
& $adb install -r app\build\outputs\apk\demo\debug\app-demo-debug.apk

# Launch MainActivity
& $adb shell am start -n com.safeher.app/.MainActivity

# Force stop the app
& $adb shell am force-stop com.safeher.app

# Clear application data (CAUTION: Deletes local Room database)
& $adb shell pm clear com.safeher.app

# Capture real-time Logcat stream filtered by tag or package
& $adb logcat -v time | Select-String "SafeHer"

# Dump Android crash backtraces
& $adb logcat -b crash
```

---

## 5. Working Tree & Git Hygiene

Always ensure the working tree is clean before committing:
```powershell
# Check short status
git status --short

# Verify tracked branch
git branch -vv

# View commit history
git log --oneline -n 10
```

> [!CAUTION]
> Never commit `local.properties`, `app/google-services.json`, keystores (`*.jks`), or `.env` files. Verify with `git status` that `.gitignore` prevents them from staging.
