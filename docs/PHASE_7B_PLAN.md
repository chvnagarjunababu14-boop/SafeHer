# SafeHer Phase 7B: Secure Remote Guardian Alerts — Implementation Plan

## 1. Audit Findings & Current State

### Existing Architecture (Preserved):
- **Language & UI:** Kotlin, Jetpack Compose, Material 3, MVVM architecture.
- **Local Persistence:** Room database (version 3) storing trusted guardians, journeys, and journey events. Migrations `1->2` and `2->3` are verified and active.
- **SOS Workflow (Phase 7A):** 2-second hold activation, recipient selection, location sharing toggle, message editing, transparent pre-handoff disclaimer, SMS composer (`ACTION_SENDTO`), and clipboard copy fallback.
- **Location:** Foreground-only on-demand location retrieval via `FusedLocationRepository`.
- **Navigation:** Bottom navigation bar supporting `HOME`, `MAP`, `JOURNEYS`, `CONTACTS`, and `MORE`.

### Firebase Configuration Audit:
- `google-services.json`: **Not present** in `app/`.
- `firebase.json`: **Not present**.
- `firestore.rules`: **Not present**.
- `functions/`: **Not present**.
- Firebase SDK / Google Services Gradle plugins: **Not yet configured**.
- **Assessment:** Firebase has not yet been initialized in this project. As instructed, we will not fabricate credentials, project IDs, or pretend a live backend is active. Instead, we will:
  1. Add optional plugin application (`if (file("google-services.json").exists())`) so Gradle builds and unit tests succeed locally right away.
  2. Implement complete, production-grade Android client code with Firebase Auth, Firestore, Functions, and FCM.
  3. Create the complete second-generation TypeScript Cloud Functions backend with Admin SDK, Firestore rules, and backend unit/emulator tests.
  4. Provide exact Firebase Console prerequisites and deployment commands.

---

## 2. Prerequisites Requiring User Action in Firebase Console

To connect a live Firebase environment, the user must perform the following actions:

1. **Create / Select Firebase Project:**
   - Navigate to [Firebase Console](https://console.firebase.google.com/).
   - Click **Add project** and enter a project name (e.g., `safeher-production` or `safeher-dev`).
2. **Register Android Application:**
   - Package name: `com.safeher.app` (must match `applicationId` in `app/build.gradle.kts`).
   - App nickname: `SafeHer`.
   - **SHA-1 and SHA-256 Fingerprints:** Generate via `./gradlew signingReport` and register both in Project Settings. (Required for Google Sign-In and App Check Play Integrity).
3. **Download Configuration File:**
   - Download `google-services.json` from Project Settings.
   - Place it directly into `app/google-services.json`.
4. **Enable Authentication Providers:**
   - In Firebase Console > **Authentication** > **Sign-in method**:
     - Enable **Email/Password**.
     - Enable **Google**. Configure support email and note the generated Web Client ID.
5. **Enable Cloud Firestore:**
   - In Firebase Console > **Firestore Database**:
     - Click **Create database** in Production Mode (our `firestore.rules` will enforce security).
     - Select a location (e.g., `asia-south1` or `us-central1`).
6. **Enable Blaze (Pay-as-you-go) Plan:**
   - Required by Google Cloud to deploy 2nd Generation Cloud Functions (uses Cloud Run infrastructure; generous free tier applies).
7. **Install Firebase CLI locally:**
   - `npm install -g firebase-tools`
   - Run `firebase login`.

---

## 3. Architecture & Component Blueprint

```
                      +---------------------------------------+
                      |          Android Client (SafeHer)     |
                      | - Credential Manager & Firebase Auth  |
                      | - RemoteGuardianRepository            |
                      | - RemoteSosRepository                 |
                      | - SafeHerMessagingService (FCM)       |
                      +-------------------+-------------------+
                                          | Authenticated Calls (HTTPS)
                                          v
+-----------------------------------------------------------------------------------+
|                           Firebase Backend (2nd Gen)                              |
|                                                                                   |
|  [Callable Functions]                                                             |
|   - createGuardianInvite        -> Generates expiring, single-use invite code     |
|   - acceptGuardianInvite        -> Verifies invite, creates relationship          |
|   - revokeGuardianRelationship  -> Revokes relationship, severs access            |
|   - dispatchRemoteSos           -> Validates auth & relationship, writes alert,   |
|                                    stores sensitive location, sends FCM           |
|   - reportAlertReceived         -> Records DEVICE_RECEIVED from recipient device  |
|   - acknowledgeAlert            -> Records ACKNOWLEDGED from recipient guardian   |
|   - cleanupExpiredLocations     -> Purges location subdocuments after retention   |
|                                                                                   |
|  [Firestore Database]                                                             |
|   - /users/{uid}                                                                  |
|   - /invitations/{inviteId}                                                       |
|   - /relationships/{relId}                                                        |
|   - /alerts/{alertId}                                                             |
|       └─ sensitive/location    (Strictly restricted, short retention)            |
|       └─ delivery_status/{uid} (CREATED, QUEUED, FCM_ACCEPTED, etc.)              |
|                                                                                   |
|  [Firebase Cloud Messaging]                                                       |
|   - Dispatches minimal, opaque alert pushes via Admin SDK                         |
+-----------------------------------------------------------------------------------+
```

---

## 4. Phased Implementation Roadmap

1. **Gradle & Dependencies:**
   - Update `libs.versions.toml` with Firebase BOM, Auth, Firestore, Messaging, Functions, App Check, and Credential Manager.
   - Update `build.gradle.kts` and `app/build.gradle.kts` with graceful `google-services.json` detection.
2. **Backend Infrastructure (`functions/` & rules):**
   - Initialize TypeScript Cloud Functions (2nd Gen) with Admin SDK.
   - Implement callable functions (`createGuardianInvite`, `acceptGuardianInvite`, `revokeGuardianRelationship`, `dispatchRemoteSos`, `reportAlertReceived`, `acknowledgeAlert`).
   - Write `firestore.rules` with strict access controls.
   - Implement backend unit tests.
3. **Android Authentication & Account Screen:**
   - Create `AuthRepository` with email/password and Google Sign-In via Credential Manager.
   - Implement `AccountScreen` in the `MORE` tab with sign-in, sign-up, password reset, and profile management.
4. **Remote Guardian Invitations & Consent:**
   - Create `RemoteGuardianRepository`.
   - Implement invite creation dialog and invite code redemption dialog with clear consent disclosures.
5. **FCM Push Handling & Device Registration:**
   - Implement `SafeHerMessagingService` with notification channel and token registration.
   - Add notification tap navigation to guardian alert details.
6. **Remote SOS Dispatch & Real-Time Delivery Tracking:**
   - Update `EmergencySosDialog` to support Remote Alerts alongside Phase 7A SMS fallback.
   - Track delivery states: `CREATED`, `QUEUED`, `FCM_ACCEPTED`, `DEVICE_RECEIVED`, `ACKNOWLEDGED`, `FAILED`, `UNKNOWN`.
7. **Guardian Alert Detail Screen:**
   - Screen displaying alert time, sender, map link (authorized only), and acknowledgement button.
8. **Automated Verification:**
   - Run Android unit tests (`testDebugUnitTest`).
   - Run backend tests.
   - Build debug APK (`assembleDebug`).
   - Generate documentation and user verification guide.
