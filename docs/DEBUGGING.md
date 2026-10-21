# SafeHer — Practical Debugging & Troubleshooting Handbook

This handbook provides practical diagnosis and remediation workflows for errors encountered during development, builds, installation, runtime execution, database migrations, and cloud integrations.

Every issue follows the strict diagnostic format:
**Symptom &rarr; Likely Causes &rarr; Diagnostic Command &rarr; How to Interpret the Result &rarr; Safe Fix &rarr; Verification Step.**

---

## 1. Repeatable 11-Step Debugging Workflow

When encountering any unexpected failure, follow this systematic workflow before modifying code:

1. **Reproduce & Record:** Record the exact sequence of user actions leading to the failure.
2. **Capture Full Stack Trace:** Capture the untruncated stack trace using Logcat or Gradle's `--stacktrace` flag.
3. **Classify Failure Domain:** Categorize into: *Build/Gradle*, *Installation*, *Runtime/UI*, *Permission*, *Database/Room*, *Location/GPS*, *Network/Firebase*, or *Cloud Function*.
4. **Check Build Variant:** Confirm whether the active variant is `demoDebug` or `prodDebug` in Android Studio's *Build Variants* window.
5. **Inspect Logcat with Package Filter:**
   ```powershell
   $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
   & $adb logcat -v time | Select-String "com.safeher.app"
   ```
   > [!NOTE]
   > Package filtering is convenient, but crashes originating inside Android system frameworks (e.g., ActivityManager, WindowManager) may be filtered out. In crash scenarios, always run `& $adb logcat -b crash`.
6. **Redact Sensitive Information:** Before sharing logs in bug reports or GitHub issues, redact phone numbers, coordinates, FCM tokens, and user IDs.
7. **Run the Smallest Isolated Test:** Execute the specific unit test targeting the failing component:
   ```powershell
   .\gradlew.bat testDemoDebugUnitTest --tests "com.safeher.app.DatabaseMigrationTest"
   ```
8. **Identify Root Cause First:** Do not guess or apply trial-and-error edits to unrelated files.
9. **Apply the Minimal Safe Fix:** Implement the least invasive change that directly corrects the root cause.
10. **Execute Regression Test Suite:** Re-run all 153 tests to verify no regressions were introduced:
    ```powershell
    .\gradlew.bat testDemoDebugUnitTest
    ```
11. **Verify on Target Runtime:** Rebuild and test on a physical device or emulator.

---

## 2. Android Studio & Gradle Troubleshooting

### Problem 2.1: Incompatible Java / JDK Version
- **Symptom:** Gradle sync or build fails with `Unsupported class file major version` or `Android Gradle plugin requires Java 17 to run`.
- **Likely Causes:** Gradle is pointing to an older Java runtime (e.g., JDK 8 or JDK 11) or system `JAVA_HOME` points to a legacy installation.
- **Diagnostic Command:**
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; & "$env:JAVA_HOME\bin\java.exe" -version
  ```
- **How to Interpret:** If the version reported is less than `17.0.0`, AGP 9.4+ will reject the build.
- **Safe Fix:**
  1. In Android Studio: Navigate to **File** > **Settings** > **Build, Execution, Deployment** > **Build Tools** > **Gradle**.
  2. Under **Gradle JDK**, select **Embedded JDK** or browse to `C:\Program Files\Android\Android Studio\jbr`.
  3. In PowerShell, prefix commands with:
     ```powershell
     $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
     ```
- **Verification:** Run `.\gradlew.bat --version`. Verify JVM version shows `17.x` or `21.x`.

---

### Problem 2.2: Duplicate Class / Namespace Conflicts
- **Symptom:** Build fails with `Duplicate class androidx.vectordrawable.graphics.drawable.Animatable2Compat found in modules...`.
- **Likely Causes:** Transitive dependencies pulling mismatched versions of legacy support libraries alongside modern AndroidX libraries.
- **Diagnostic Command:**
  ```powershell
  .\gradlew.bat :app:dependencies --configuration demoDebugRuntimeClasspath | Select-String "vectordrawable"
  ```
- **How to Interpret:** Check if two different versions of `androidx.vectordrawable` (e.g., `1.1.0` and `1.2.0`) are requested.
- **Safe Fix:** In `app/build.gradle.kts`, SafeHer explicitly includes a resolution strategy constraint:
  ```kotlin
  configurations.all {
      resolutionStrategy.eachDependency {
          if (requested.group == "androidx.vectordrawable") {
              useVersion("1.2.0")
              because("Prevent duplicate androidx.vectordrawable namespace conflicts")
          }
      }
  }
  ```
- **Verification:** Run `.\gradlew.bat assembleDemoDebug`. Build succeeds.

---

### Problem 2.3: Gradle Daemon Corruption or Stale Cache
- **Symptom:** Inexplicable compilation errors after branch switching, or Gradle hanging during configuration.
- **Likely Causes:** Stale daemon processes in memory or corrupted `.gradle/caches`.
- **Diagnostic Command:**
  ```powershell
  Get-Process -Name java, "gradle*" -ErrorAction SilentlyContinue | Select-Object Id, ProcessName, WorkingSet64
  ```
- **Safe Fix:**
  ```powershell
  # Stop all running Gradle daemons cleanly
  .\gradlew.bat --stop

  # Re-run build with configuration cache refresh
  .\gradlew.bat assembleDemoDebug --no-build-cache
  ```
- **Verification:** Run `.\gradlew.bat assembleDemoDebug`.

---

## 3. Installation & Device Debugging

### Problem 3.1: ADB Does Not Detect Physical Phone
- **Symptom:** `adb devices` shows an empty list or the device appears as `unauthorized`.
- **Likely Causes:** USB debugging disabled, missing USB driver on Windows, or USB mode set to "Charging only".
- **Diagnostic Command:**
  ```powershell
  $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
  & $adb devices -l
  ```
- **How to Interpret:**
  - `List of devices attached` (empty) &rarr; Hardware/driver issue or cable problem.
  - `XXXXXXXX device unauthorized` &rarr; Phone screen has an unaccepted authorization prompt.
- **Safe Fix:**
  1. Unlock the phone and look for **"Allow USB debugging from this computer?"**. Check **Always allow** and tap **Allow**.
  2. If empty, toggle **USB debugging** OFF and back ON in Developer options.
  3. Switch USB connection mode on your phone to **File Transfer (MTP)**.
  4. Restart the ADB server:
     ```powershell
     & $adb kill-server
     & $adb start-server
     & $adb devices -l
     ```
- **Verification:** Output displays `device product:... model:...`.

---

### Problem 3.2: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
- **Symptom:** `adb install` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.safeher.app signatures do not match newer version`.
- **Likely Causes:** A release build or a build signed by another developer's debug keystore is already installed on the handset.
- **Diagnostic Command:**
  ```powershell
  & $adb shell pm list packages | Select-String "com.safeher.app"
  ```
- **Safe Fix:**
  > [!WARNING]
  > Uninstalling deletes the local Room database (`safeher_guardians.db`). If local guardian or journey history must be preserved, back up the database first:
  ```powershell
  # Backup local database before uninstalling
  & $adb exec-out run-as com.safeher.app cat databases/safeher_guardians.db > backup_safeher.db

  # Uninstall existing conflicting APK
  & $adb uninstall com.safeher.app

  # Install clean demo debug build
  & $adb install -r app\build\outputs\apk\demo\debug\app-demo-debug.apk
  ```
- **Verification:** APK installs with `Success`.

---

## 4. Database & Room Migration Troubleshooting

### Problem 4.1: `IllegalStateException: Room cannot verify the data integrity`
- **Symptom:** App crashes immediately upon launch with:
  `java.lang.IllegalStateException: Room cannot verify the data integrity. Expected: TableInfo{...} , Found: TableInfo{...}`.
- **Likely Causes:** Discrepancy between the SQL DDL in a migration (`MIGRATION_X_Y`) and the Room entity class annotations (e.g., nullability mismatches, missing indices, or mismatched column types).
- **Diagnostic Command:**
  Run the automated database migration unit test:
  ```powershell
  .\gradlew.bat testDemoDebugUnitTest --tests "com.safeher.app.DatabaseMigrationTest"
  ```
- **How to Interpret:** Look at the specific field highlighted in the test diff. For example, in Java entities, String fields default to nullable unless annotated with `@androidx.annotation.NonNull`. If the migration specifies `TEXT NOT NULL`, an unannotated String will trigger a schema mismatch.
- **Safe Fix (DO NOT DELETE DATA):**
  1. Do NOT use `fallbackToDestructiveMigration()`. Local guardians and journey logs must never be deleted.
  2. Align the entity annotation with the migration SQL:
     - If the column is `TEXT NOT NULL`, annotate the Java entity field and constructor parameter with `@NonNull`.
     - If the column is nullable, omit `@NonNull` in both entity and migration SQL.
  3. Recompile and run `DatabaseMigrationTest`.
- **Verification:** `DatabaseMigrationTest` passes with 0 failures.

---

## 5. Location, OpenStreetMap & SOS Troubleshooting

### Problem 5.1: Location Unavailable or Timeout on Emergency SOS
- **Symptom:** SOS review dialog shows "Acquiring location..." indefinitely, or location is omitted from the message.
- **Likely Causes:**
  1. Device location (GPS) is turned off in Android system quick settings.
  2. Location permissions (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`) were denied.
  3. Testing indoors on a device with GPS-only location mode.
- **Diagnostic Command:**
  ```powershell
  & $adb shell dumpsys location | Select-String -Pattern "Last Known Locations|Geofences" -Context 0,5
  ```
- **Safe Fix:**
  1. Open Android **Settings** > **Location** > Ensure **Use location** is ON.
  2. Set Location accuracy to **Google Location Accuracy** (Fused network + GPS).
  3. If testing on an emulator: Open the emulator's Extended Controls (**...** icon) > **Location** > Click **Send** to inject simulated coordinates.
- **Verification:** Launch SafeHer and navigate to the **Map** tab. The map centers on the blue pulsing user marker.

---

### Problem 5.2: SMS App Fails to Open During Emergency SOS Handoff
- **Symptom:** Tapping "Dispatch SOS via SMS" shows a brief toast or nothing happens.
- **Likely Causes:** The test device (e.g., Wi-Fi only tablet or default emulator) lacks a telephony application handling `vnd.android-dir/mms-sms` or `smsto:` intents.
- **Diagnostic Command:**
  ```powershell
  & $adb shell pm query-intent-activities -a android.intent.action.SENDTO -d "smsto:1234567890"
  ```
- **How to Interpret:** If output says `No activities found`, no SMS application exists on the device.
- **Safe Fix:** SafeHer has built-in resilience: When `ActivityNotFoundException` is caught, the app automatically copies the emergency message with coordinates to the Android system clipboard and displays a dialog instructing the user to paste it into any messaging app (WhatsApp, Telegram, email).
- **Verification:** Verify clipboard fallback notification appears and clipboard contains the formatted message.

---

## 6. Firebase & Cloud Production Troubleshooting

### Problem 6.1: Missing `google-services.json` in Production Build
- **Symptom:** `assembleProdDebug` fails or app crashes on startup with `IllegalStateException: Default FirebaseApp is not initialized`.
- **Likely Causes:** `google-services.json` is missing from the `app/` folder.
- **Diagnostic Command:**
  ```powershell
  Test-Path "app\google-services.json"
  ```
- **How to Interpret:** Returns `False`.
- **Safe Fix:**
  - If you are evaluating or testing the app: **Switch to the Demo flavor (`demoDebug`)**, which is specifically engineered to run 100% offline without `google-services.json`.
  - If you are preparing production: Download `google-services.json` from your Firebase Console and place it in `app/google-services.json`.
- **Verification:** Run `.\gradlew.bat assembleDemoDebug` (succeeds without the file) or `assembleProdDebug` (succeeds with the file).

---

### Problem 6.2: Google Sign-In Fails with `ApiException: 10` (Developer Error)
- **Symptom:** Tapping "Sign in with Google" closes immediately with code 10.
- **Likely Causes:** Workstation SHA-1 fingerprint is not registered in Firebase Console, or Web Client ID is mismatched.
- **Diagnostic Command:**
  ```powershell
  .\gradlew.bat signingReport
  ```
- **How to Interpret:** Locate the `SHA1` under `Variant: prodDebug` (e.g., `35:89:01:A2:...`).
- **Safe Fix:**
  1. Open Firebase Console > **Project Settings** > **Your apps** > **Android app (`com.safeher.app`)**.
  2. Click **Add fingerprint** and paste the SHA-1 and SHA-256 fingerprints.
  3. Download the updated `google-services.json` and replace `app/google-services.json`.
  4. Ensure Google provider is enabled in Firebase Authentication.
- **Verification:** Re-test Google Sign-In; account selection sheet displays successfully.

---

### Problem 6.3: Firestore `PERMISSION_DENIED` on Client Write
- **Symptom:** Client log shows `FirebaseFirestoreException: PERMISSION_DENIED: Missing or insufficient permissions`.
- **Likely Causes:** SafeHer's security rules intentionally block all direct client writes to `/invitations/`, `/relationships/`, and `/alerts/` to prevent token forging and spoofing. All writes must go through Cloud Functions.
- **Safe Fix:** Verify your client code calls the callable Cloud Function (e.g., `createGuardianInvite`, `dispatchRemoteSos`) rather than attempting a direct `FirebaseFirestore.collection("alerts").add(...)`.
- **Verification:** Cloud Function executes under admin authority and writes to Firestore.
