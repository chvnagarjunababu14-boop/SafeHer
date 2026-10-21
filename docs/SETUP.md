# SafeHer — Complete Fresh-Machine Setup Guide (Windows 11)

This step-by-step guide walks a new engineer or evaluator through preparing a fresh Windows 11 workstation, cloning the repository, opening the project in Android Studio, running the self-contained Demo Mode without cloud credentials, and optionally configuring the Firebase production build.

---

## Step 1: Install Development Toolchain

### A. Install Git for Windows
1. Download Git from [git-scm.com](https://git-scm.com/download/win).
2. Run the installer with standard options. Ensure **Git Credential Manager** is checked.
3. Configure your identity in PowerShell:
   ```powershell
   git config --global user.name "Your Name"
   git config --global user.email "your.email@example.com"
   git config --global credential.helper manager
   ```

### B. Install Android Studio
1. Download **Android Studio** (Ladybug Feature Drop 2024.2+ or later) from [developer.android.com/studio](https://developer.android.com/studio).
2. During setup, select the **Standard** installation type.
3. In the SDK Component Setup screen, verify that the following components are selected:
   - Android SDK Platform (API 35+)
   - Android SDK Build-Tools
   - Android SDK Platform-Tools
   - Android Emulator
4. Finish installation and allow Android Studio to download the SDK components. By default, SDK is installed to:
   ```text
   C:\Users\<YourUsername>\AppData\Local\Android\Sdk
   ```

### C. Verify Java Development Kit (JDK)
SafeHer requires **JDK 17 or higher**. Android Studio bundles a tested JetBrains Runtime (JBR 21 or 17).
Verify in PowerShell:
```powershell
# Set JAVA_HOME to Android Studio's bundled runtime
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# Test Java version
java -version
```
Expected output:
```text
openjdk version "21.0.x" (or "17.0.x")
OpenJDK Runtime Environment (build ...)
OpenJDK 64-Bit Server VM (build ...)
```

---

## Step 2: Clone the Repository

Clone the project to your local workspace directory:
```powershell
# Create or navigate to your projects folder
New-Item -ItemType Directory -Path "$env:USERPROFILE\Projects" -Force | Out-Null
Set-Location -Path "$env:USERPROFILE\Projects"

# Clone the SafeHer repository
git clone https://github.com/chvnagarjunababu14-boop/SafeHer.git
Set-Location -Path SafeHer
```

---

## Step 3: Open Project in Android Studio

1. Launch Android Studio.
2. Select **Open** (or **File** > **Open**).
3. Navigate to `C:\Users\<YourUsername>\Projects\SafeHer` and click **OK**.
4. Android Studio will automatically invoke the Gradle wrapper and perform initial project synchronization.

### How to Inspect Gradle Sync
- Look at the bottom status bar for **"Gradle Sync in progress..."**.
- When completed, the **Build** window at the bottom will display **"BUILD SUCCESSFUL"**.
- If a sync error occurs:
  1. Open the **Build** tab (Alt + 4).
  2. Select **Toggle view** to read the complete Gradle stack trace.
  3. Ensure your Gradle JDK in Android Studio is configured properly:
     - Navigate to **File** > **Settings** > **Build, Execution, Deployment** > **Build Tools** > **Gradle**.
     - Under **Gradle JDK**, select **Embedded JDK / JBR** (version 17 or 21).

---

## Step 4: Configure Local Properties

Create `local.properties` in the project root if it does not already exist. This file is excluded from Git via `.gitignore` to prevent committing personal paths.

Run in PowerShell:
```powershell
$sdkPath = "$env:LOCALAPPDATA\Android\Sdk".Replace('\', '\\')
@"
## Local configuration for Gradle
sdk.dir=$sdkPath
PLACES_API_KEY=
"@ | Out-File -FilePath local.properties -Encoding utf8
```

> [!NOTE]
> `PLACES_API_KEY` is optional. If left blank, the app allows full manual destination text entry for Safety Journeys without crashing.

---

## Step 5: Select and Run the Demo Flavor (Zero Setup)

SafeHer's **Demo Mode** is 100% self-contained and requires **no Firebase account, no `google-services.json`, and no network connection**.

### A. Select the Build Variant in Android Studio
1. Open the **Build Variants** tool window (left sidebar or **View** > **Tool Windows** > **Build Variants**).
2. For module `:app`, set **Active Build Variant** to:
   - **`demoDebug`** (Recommended for immediate testing and evaluation)

### B. Run Unit Tests via CLI
Verify that all 153 unit tests pass on your machine:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

.\gradlew.bat testDemoDebugUnitTest
```
Expected output:
```text
BUILD SUCCESSFUL in 3s
26 actionable tasks: 26 up-to-date
```

### C. Build the Demo APK
```powershell
.\gradlew.bat assembleDemoDebug
```
The resulting APK will be at:
```text
app\build\outputs\apk\demo\debug\app-demo-debug.apk
```

---

## Step 6: Connect a Device or Launch an Emulator

### Option 1: Physical Handset
1. Enable **Developer options** and **USB debugging** on your phone (see [REQUIREMENTS.md](file:///docs/REQUIREMENTS.md#3-android-test-device-requirements)).
2. Connect your phone via USB cable.
3. Accept the authorization prompt on the phone screen: *"Allow USB debugging from this computer?"* &rarr; Check *"Always allow"*, tap **Allow**.
4. Confirm device is detected in PowerShell:
   ```powershell
   $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
   & $adb devices -l
   ```

### Option 2: Android Emulator
1. In Android Studio, open the **Device Manager** (**Tools** > **Device Manager**).
2. Click **Create device**.
3. Select **Pixel 8**, then click **Next**.
4. Select system image **VanillaIceCream (API 35)** or **UpsideDownCake (API 34)** with Google Play.
5. Click **Finish**, then click the **Play** button to launch the virtual device.

---

## Step 7: Install and Launch the App

### Via Android Studio
Select your device/emulator in the target toolbar dropdown and click the green **Run 'app'** button (Shift + F10).

### Via Command Line
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# Install the demo APK
& $adb install -r app\build\outputs\apk\demo\debug\app-demo-debug.apk

# Launch MainActivity
& $adb shell am start -n com.safeher.app/.MainActivity
```

---

## Step 8: Verify App Functionality in Demo Mode

Once the app launches on your device, perform this 2-minute smoke verification:

1. **Observe Demo Banner:** Notice the purple banner at the top of the Home screen: **"Demo Mode Active • Local Simulation (No Firebase Required)"**.
2. **Test Emergency SOS Simulation:**
   - Press and hold the circular red SOS button for 2 seconds until the outer ring fills.
   - The **Emergency SOS** review dialog appears.
   - Select the **Push Notification** tab. Notice the pre-linked guardian **Demo Guardian (Priya)** is checked.
   - Tap **Dispatch Remote SOS**.
   - Observe the live simulated delivery state pipeline: `Queued (Simulated)` &rarr; `FCM Transit (Simulated)` &rarr; `App Receipt Simulated`.
   - Tap the evaluator button: **"Simulate Guardian Acknowledgement"**.
   - Notice the status immediately updates to `Acknowledgement Simulated`.
3. **Switch Demo Profiles:**
   - Tap the **More** tab (bottom navigation).
   - In the **Demo Mode Controller Card**, tap the role selector to switch to **Demo Guardian (Priya)**.
   - Notice the account profile updates instantly without passwords or network requests.
4. **Test Local Safety Journey:**
   - Tap the **Journeys** tab.
   - Enter destination "Central Library" and duration "15 min". Tap **Start Safety Journey**.
   - Tap **Check In** to record a safety ping on the Room-backed event timeline.

---

## Step 9: Configure Firebase for Production (Optional)

When you are ready to connect SafeHer to your personal Firebase cloud infrastructure:

1. In the [Firebase Console](https://console.firebase.google.com/):
   - Create a project (e.g., `safeher-prod`).
   - Register an Android App with package name: `com.safeher.app`.
   - Generate SHA-1 / SHA-256 fingerprints via PowerShell:
     ```powershell
     .\gradlew.bat signingReport
     ```
   - Register the SHA-1 and SHA-256 fingerprints under **Project Settings** > **Your apps**.
   - Download `google-services.json` and place it at:
     ```text
     app\google-services.json
     ```
2. Enable Firebase Services:
   - **Authentication:** Enable Email/Password and Google Sign-In.
   - **Firestore Database:** Create in production mode and deploy rules:
     ```powershell
     npm install -g firebase-tools
     firebase login
     firebase deploy --only firestore:rules,firestore:indexes
     ```
   - **Cloud Functions:**
     ```powershell
     cd functions
     npm install
     npm run build
     firebase deploy --only functions
     cd ..
     ```
3. In Android Studio, change the **Build Variant** to **`prodDebug`**.
4. Assemble the production APK:
   ```powershell
   .\gradlew.bat assembleProdDebug
   ```
