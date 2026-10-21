# SafeHer — Demo Mode Guide

This guide explains how to build, install, test, and evaluate **SafeHer Phase 7B** in self-contained **Demo Mode** without needing a personal Firebase project, Google Cloud account, or cloud service credentials.

---

## 1. Executive Summary

SafeHer includes an explicit, self-contained **Demo Mode** designed for developers, evaluators, and reviewers. In Demo Mode, all remote multi-party safety capabilities (authentication, remote guardian linking, push notifications, and live delivery tracking) are simulated entirely on-device via a clean repository boundary (`RepositoryProvider`).

No Firebase setup, `google-services.json`, or internet connectivity is required to evaluate the application.

---

## 2. What Is Real vs. What Is Simulated

| Feature | In Demo Mode | In Production Firebase Mode |
|---|---|---|
| **Local Room Database** | **REAL** (Persisted on device: trusted guardians, journey history, event timeline) | **REAL** (Persisted on device) |
| **Foreground Location** | **REAL** (Foreground GPS/fused location retrieval and accuracy reporting) | **REAL** (Foreground GPS/fused location retrieval) |
| **Interactive Map & Tile Cache** | **REAL** (OpenStreetMap rendering with offline caching) | **REAL** (OpenStreetMap rendering) |
| **Phase 7A SMS Fallback** | **REAL** (Hands off to Android system SMS composer with prefilled message) | **REAL** (Hands off to Android system SMS composer) |
| **Authentication** | **SIMULATED** (Local fictional profiles, no network requests, no passwords collected) | **REAL** (Firebase Auth: Email/Password & Google Sign-In) |
| **Remote Guardian Linking** | **SIMULATED** (Instant single-use invite codes `DEMOxxxx` and in-memory linking) | **REAL** (Cloud Firestore & 2nd Gen Cloud Functions) |
| **SOS Push Notifications** | **SIMULATED** (Progressive state simulation: Queued -> FCM Accepted -> App Receipt) | **REAL** (FCM push delivery to remote devices) |
| **Guardian Acknowledgement** | **SIMULATED** (Evaluator can tap "Simulate Guardian Acknowledgement" or switch roles) | **REAL** (Recipient taps notification, authenticates, and acknowledges) |
| **Sensitive Location Purge** | **SIMULATED** (In-memory retention) | **REAL** (Cloud Functions 24h cron retention job) |

---

## 3. Fictional Demo Profiles

Demo Mode provides two fictional demo profiles:

1. **Demo User (Sender Perspective)**
   - **Display Name:** `Demo User`
   - **Email:** `demo.user@safeher.local`
   - **Role:** You. Dispatches simulated emergency alerts, manages linked guardians, and tests location consent.
   - **Pre-linked Guardian:** `Demo Guardian (Priya)`

2. **Demo Guardian (Recipient Perspective)**
   - **Display Name:** `Demo Guardian (Priya)`
   - **Email:** `priya.guardian@safeher.local`
   - **Role:** Trusted guardian. Receives simulated alerts, views emergency location map, and simulates acknowledgement.
   - **Pre-linked Ward:** `Demo User`

### Role Switching
You can switch roles with a single tap in the **Account & Remote Circle** screen (accessible via the **More** tab or by tapping the purple **Demo Mode Active** banner on the Home screen).

---

## 4. How to Build & Install the Demo APK

### Prerequisites
- JDK 17+ (or Android Studio JBR)
- Android SDK Platform 35+
- An Android device or emulator running Android 8.0+ (API 26+)

### Build Commands

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# Assemble Demo Debug APK
.\gradlew.bat assembleDemoDebug

# Run Unit Tests
.\gradlew.bat testDemoDebugUnitTest
```

### APK Location
- **Path:** `app\build\outputs\apk\demo\debug\app-demo-debug.apk`

### Install on Device or Emulator
```bash
adb install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk
```

---

## 5. 5-Minute Evaluator Walkthrough

### Step 1: Open SafeHer & Observe Demo Mode Banner
1. Launch **SafeHer**.
2. Notice the purple banner at the top of the Home screen:
   *`🧪 SafeHer Demo Mode Active — Local simulation enabled. Tap to switch demo roles or exit.`*

### Step 2: Test Guardian Linking
1. Tap the **More** tab in bottom navigation (or tap the Demo Banner).
2. You will see the **SafeHer Demo Mode (Active)** controller card:
   - Notice the current active profile is **Demo User**.
   - Notice the linked guardian **Demo Guardian (Priya)** is already displayed under *Active Linked Guardians*.
3. Tap **Create Guardian Invite**:
   - Generates a simulated single-use code (e.g., `DEMO4821`).
4. Tap **Accept Guardian Invite**:
   - Type any code (e.g. `DEMO1234`) and tap **Accept Link**.
   - A new simulated contact (`Demo Guardian (Ananya)`) is immediately linked to your circle!

### Step 3: Trigger a Simulated Remote SOS
1. Return to the **Home** tab.
2. Press and hold the red **Emergency SOS** button for 2 seconds.
3. In the Emergency Dialog:
   - Notice the channel selector defaults to **Remote Push (FCM) [DEMO]**.
   - Check that `[DEMO] Demo Guardian (Priya)` is selected.
   - Toggle **Share Current Location** ON or OFF.
   - Review the emergency message body.
4. Tap **Dispatch Remote SOS**:
   - The dialog immediately shows `Remote Alert Dispatched [SIMULATED]`.
   - Watch the live delivery status progress in real-time:
     - `[SIMULATED] Created`
     - `[SIMULATED] Queued`
     - `[SIMULATED] FCM Accepted`
     - `[SIMULATED] App Receipt Simulated`

### Step 4: Simulate Guardian Acknowledgement
1. Notice the purple button: **Simulate Guardian Acknowledgement**.
2. Tap it:
   - The delivery badge immediately changes to `[SIMULATED] Acknowledgement Simulated` (green checkmark).
3. Alternatively, tap **Switch to SMS Composer** to test the Phase 7A local SMS handoff fallback!

### Step 5: Resetting Demo Data
1. Go to the **More** tab.
2. Tap **Reset Demo Data**.
3. All in-memory demo guardians and alerts are cleanly reset to defaults.
4. **Note:** Your local Room database (local guardians, journeys, and event history) remains 100% intact and untouched.

---

## 6. How to Switch to a Real Firebase Build Later

When you are ready to connect to a live Firebase backend:

1. Obtain your official `google-services.json` from the Firebase Console and place it at:
   `app/google-services.json`
2. Build the production variant:
   ```powershell
   .\gradlew.bat assembleProdDebug
   ```
3. In the app's **More** tab, tap **Exit Demo Mode** (or the production variant will have `IS_DEMO_DEFAULT = false`).
4. SafeHer will automatically use `FirebaseAuthRepository`, `FirebaseRemoteGuardianRepository`, and `FirebaseRemoteSosRepository`.

---

## 7. Safety, Privacy & Honesty Constraints

> [!IMPORTANT]
> **Safety Notice:**
> - Demo Mode **never** sends real SMS messages, automated phone calls, emails, or FCM push notifications.
> - Demo delivery statuses (`[SIMULATED] App Receipt Simulated`, `[SIMULATED] Acknowledgement Simulated`) are simulated locally on-device and do **not** represent real human receipt or official emergency response.
> - SafeHer does not guarantee police dispatch or emergency response.
