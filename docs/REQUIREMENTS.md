# SafeHer — System & Environment Requirements

This document details the verified hardware, software, SDK, device, and service requirements for building, running, testing, and deploying the **SafeHer** Android application and its Cloud Functions backend.

---

## 1. Requirements Matrix: Offline Demo vs. Cloud Production

SafeHer features an explicit architectural boundary separating self-contained local evaluation from cloud-connected production deployment:

| Requirement Area | Offline Demo Flavor (`demoDebug`) | Cloud Production Flavor (`prodDebug`) |
|---|---|---|
| **Android Studio & SDK** | Required | Required |
| **JDK 17+ / JBR** | Required | Required |
| **Google Services JSON (`google-services.json`)** | **Not Required** (Build ignores missing file) | **Required** (Downloaded from Firebase Console) |
| **Firebase Project** | **None** (Simulated locally via in-memory mock repositories) | **Required** (Blaze pay-as-you-go plan for Functions v2) |
| **Firebase Authentication** | **Simulated** (Fictional Demo User & Demo Guardian profiles) | **Required** (Email/Password & Google Sign-In) |
| **Cloud Firestore** | **Simulated** (In-memory reactive StateFlow stores) | **Required** (Asia-South1 or selected region) |
| **Firebase Cloud Messaging** | **Simulated** (Progressive pipeline with UI status badges) | **Required** (FCM v1 HTTP API) |
| **Cloud Functions Backend** | **Not Required** | **Required** (Node.js 18+ deployment) |
| **Google Places API Key** | **Optional** (Destination field falls back to text entry) | **Optional** (Configured via `local.properties`) |
| **Active Internet Connection** | **No** (100% offline capable after Gradle dependency cache) | **Yes** (For cloud synchronization and notifications) |

---

## 2. Development Computer Requirements

### Operating System
- **Supported:**
  - Windows 11 (64-bit) — *Verified development environment*
  - Windows 10 (64-bit, version 21H2 or later)
  - macOS 13 (Ventura) or later (Apple Silicon or Intel)
  - Linux (Ubuntu 22.04 LTS / Debian 12 or equivalent 64-bit distribution)

### Hardware Specifications
| Component | Minimum | Recommended |
|---|---|---|
| **RAM** | 8 GB | 16 GB or 32 GB (permits concurrent Android Studio, Gradle daemon, and Android Emulator) |
| **Free Storage** | 10 GB | 25 GB+ (SSD recommended for Android SDK platforms, system images, and Gradle build cache) |
| **Processor** | 64-bit quad-core Intel / AMD or Apple Silicon | 8-core modern processor with hardware virtualization (VT-x / AMD-V) enabled |

### Java Development Kit (JDK)
- **Version:** **JDK 17 or higher** (Required by Android Gradle Plugin 9.4+)
- **Verified Runtime:** Android Studio Bundled JetBrains Runtime (`jbr-21` / `jbr-17`)
- **Verified Path:** `C:\Program Files\Android\Android Studio\jbr`
- **Gradle Source/Target Compatibility:** `JavaVersion.VERSION_17` (configured in `app/build.gradle.kts`)

### Android Studio
- **Version:** Android Studio Ladybug Feature Drop (2024.2+) or Android Studio Meerkat / 2026+
- **Installed Components:**
  - Android Studio core IDE
  - Android SDK Command-line Tools (latest)
  - Android SDK Build-Tools
  - Android SDK Platform-Tools (provides `adb`)
  - Android Emulator (if testing without a physical handset)

### Android SDK & Tooling Versions
- **`compileSdk`:** `37` (Android SDK 35/37 platform APIs)
- **`targetSdk`:** `35` (Android 15)
- **`minSdk`:** `26` (Android 8.0 Oreo — guarantees support for background job constraints, notification channels, and modern cryptographic primitives)
- **Build Tools Version:** `35.0.0` (or `34.0.0` minimum)
- **Android Gradle Plugin (AGP):** `9.4.1` (configured via version catalog `gradle/libs.versions.toml`)
- **Gradle Wrapper:** `9.6.0-bin` (configured via `gradle/wrapper/gradle-wrapper.properties`)
- **Kotlin:** `2.3.20` with Kotlin Compose compiler plugin

### Command-Line Tools
- **Git:** Git 2.40+ (Verified on Windows with Git 2.56.0.windows.2)
- **Shell:** PowerShell 7+ or Windows PowerShell 5.1 (Windows) / Bash or Zsh (macOS/Linux)
- **Node.js (Backend Cloud Functions only):** Node.js 18.x or 20.x with npm 9+ (Node engine `>=18` defined in `functions/package.json`)

---

## 3. Android Test Device Requirements

### Physical Handset (Recommended)
- **Android Version:** Android 8.0 (API level 26) through Android 15 (API level 35). Recommended for realistic testing: **Android 12 to 14 (API 31–34)**.
- **Developer Options:**
  1. Open **Settings** > **About phone**.
  2. Tap **Build number** 7 times until *"You are now a developer!"* appears.
  3. Navigate to **Settings** > **System** > **Developer options**.
  4. Enable **USB debugging**.
- **USB Connection & Drivers:**
  - Windows: Install Google USB Driver via Android Studio SDK Manager (*SDK Tools* > *Google USB Driver*) for Pixel devices, or OEM driver (Samsung Smart Switch / Xiaomi USB driver) as applicable.
  - Set USB connection mode to **File Transfer (MTP)** or **PTP** if debugging is not automatically detected.
- **Location Hardware:** Device must have integrated GPS hardware and Google Play Services for `play-services-location` FusedLocationProvider.
- **Runtime Permissions Required:**
  - `android.permission.ACCESS_FINE_LOCATION` (Foreground precise GPS)
  - `android.permission.ACCESS_COARSE_LOCATION` (Foreground network-based location)
  - `android.permission.POST_NOTIFICATIONS` (Mandatory on Android 13+ / API 33+ for emergency notifications)
  - `android.permission.READ_CONTACTS` (Optional; only requested when importing contacts from the system address book)

### Android Emulator (Alternative)
- **Device Profile:** Pixel 7, Pixel 8, or Pixel 9
- **System Image:** Android 14.0 ("UpsideDownCake" / API 34) or Android 15.0 ("VanillaIceCream" / API 35) with **Google Play** (required for Play Services Location and Google Sign-In)
- **Virtualization:** Hyper-V / Windows Hypervisor Platform enabled in Windows Features.

### ADB Detection Verification
To verify your workstation recognizes the connected device or running emulator:
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
```
Expected output:
```text
List of devices attached
9A121FFAZ001GH         device product:cheetah model:Pixel_7 device:cheetah transport_id:1
```

---

## 4. Accounts & External Services

### GitHub
- **Account:** Required for pushing code or creating Pull Requests to [`https://github.com/chvnagarjunababu14-boop/SafeHer`](https://github.com/chvnagarjunababu14-boop/SafeHer).
- **Authentication:** Git Credential Manager (GCM) using browser OAuth or Personal Access Token (fine-grained with `contents:write` scope).

### Firebase & Google Cloud Services (Production Variant Only)
*Not required for evaluating or running `demoDebug`.*

When setting up the production build flavor (`prodDebug`), the following cloud resources are required:

1. **Firebase Project:**
   - Free Spark plan supports Authentication and Firestore in development.
   - **Blaze (Pay-as-you-go) Plan:** Required to deploy 2nd Generation Cloud Functions and permit outbound HTTPS requests from Cloud Functions to FCM.
2. **Firebase Authentication:**
   - Provider 1: **Email/Password** enabled in Firebase Console.
   - Provider 2: **Google Sign-In** enabled in Firebase Console. Requires registering workstation and release keystore SHA-1 / SHA-256 certificate fingerprints in Firebase Project Settings.
3. **Cloud Firestore:**
   - Database created in production mode.
   - Rules: Deploy [`firestore.rules`](file:///firestore.rules) (blocks direct client writes; restricts reads to authenticated participants).
   - Indexes: Deploy [`firestore.indexes.json`](file:///firestore.indexes.json).
4. **Firebase Cloud Messaging (FCM):**
   - FCM v1 enabled by default in modern Firebase projects.
   - Client requires `google-services.json` placed in `app/google-services.json`.
5. **Firebase App Check (Production Hardening):**
   - Play Integrity provider registered for Android release builds.
   - Debug provider token registered in Firebase Console for local development testing.
6. **Google Places API (Optional Feature):**
   - Enables live address autocomplete suggestions on the Journeys screen.
   - Google Cloud Console: Enable **Places API (New)** on your project.
   - Create an API key restricted to your Android package (`com.safeher.app`) and SHA-1 fingerprint.
   - Billing account required on Google Cloud Platform (includes $200 monthly free credit tier).
   - Placed in `local.properties` as `PLACES_API_KEY=AIzaSy...`.
   - If omitted: App gracefully degrades to manual destination text entry without crashing.
