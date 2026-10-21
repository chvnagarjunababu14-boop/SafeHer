# SafeHer — Security & Privacy Architecture

This document details the security architecture, threat model, trust boundaries, privacy controls, and verified versus pending mitigations for **SafeHer**.

---

## 1. Security Architecture & Trust Boundaries

SafeHer is a personal safety application handling sensitive data, including real-time physical location coordinates, trusted guardian phone numbers, and emergency alerts. 

```text
[ Android Handset (Untrusted Client) ]
       │
       │ (1) User confirms SOS with location consent toggle
       │
       ▼
[ Cloud Functions v2 Backend (Trusted Boundary) ]
       │  • Validates request.auth (JWT verification)
       │  • Enforces rate limits & idempotency
       │  • Verifies active relationship between Sender & Guardian
       │  • Enforces App Check (Play Integrity)
       │
  ┌────┴─────────────────────────────┐
  ▼                                  ▼
[ Cloud Firestore ]            [ FCM v1 Push Service ]
  • Private location in          • High-priority data push
    isolated subcollection       • Silent delivery & wake-up
  • Client writes BLOCKED        • Ephemeral notification
  • Read-only for participants   • Zero third-party analytics
```

### Trust Boundary Rules
1. **Client is Untrusted:** The Android client application cannot directly create or alter invitations, establish guardian relationships, write alerts, or update delivery statuses in Firestore. All mutations are performed through authenticated, server-side Cloud Functions.
2. **Explicit Consent for Location:** Location transmission is strictly opt-in. The user can toggle off location sharing in the SOS review dialog, in which case zero coordinates or location URLs are sent.
3. **Foreground-Only Location:** SafeHer does not request `ACCESS_BACKGROUND_LOCATION`. It accesses GPS only while in the foreground, during an active journey, or when the user initiates an SOS broadcast.

---

## 2. Threat Model & Implemented Controls

| Threat Vector | Severity | Implemented Mitigation | Status |
|---|---|---|---|
| **Repository Secret Leakage** | Critical | Comprehensive `.gitignore` excluding `local.properties`, `google-services.json`, keystores (`*.jks`), service account keys, and `.env`. Verified with automated regex scans. | **VERIFIED** |
| **Direct Database Tampering** | Critical | `firestore.rules` enforces `allow write: if false;` on `/invitations/`, `/relationships/`, `/alerts/`, `/alerts/{id}/sensitive/location`, and `/alerts/{id}/delivery_status/`. | **VERIFIED** |
| **Unauthorized Location Access** | Critical | Location coordinates stored in `/alerts/{alertId}/sensitive/location`. Firestore rules verify that `request.auth.uid` is either `senderUid` or in `recipientUids`. | **VERIFIED** |
| **Stale Location Exposure** | High | Cloud Functions scheduled cron job (`scheduledLocationCleanup`) automatically purges sensitive location subdocuments older than 24 hours. | **VERIFIED** |
| **Invite Code Brute-Forcing** | High | Single-use 6-character alphanumeric tokens (`DEMO-XXXXXX` or cloud-generated) stored in non-readable collection `/invitations/`. Codes expire after 24 hours. | **VERIFIED** |
| **Component Hijacking (IPC)** | Medium | `SafeHerMessagingService` declared with `android:exported="false"`. `MainActivity` is the sole exported component for launcher intents. | **VERIFIED** |
| **Accidental Emergency Trigger** | Medium | Press-and-hold 2.0-second timer with continuous visual progress animation and cancel-on-release mechanics. | **VERIFIED** |
| **Unintended Cloud Dispatch** | Medium | Dedicated `demoDebug` build flavor uses local mock repositories. Zero network requests, zero FCM calls, zero Firebase accounts required. | **VERIFIED** |

---

## 3. Verified Controls vs. Pending Roadmap

### Verified & Implemented Controls
- [x] **Zero Cloud Credentials in Git:** The repository contains no `google-services.json`, API keys, or private signing keys.
- [x] **Server-Side Authorization:** Cloud Functions verify `request.auth != null` and check relationship validity before dispatching FCM messages.
- [x] **Idempotent Dispatch:** `dispatchRemoteSos` requires an `idempotencyKey` to prevent duplicate alerts caused by mobile network retries.
- [x] **E.164 Phone Sanitization:** Guardian phone numbers are strictly validated and normalized using Google's `libphonenumber` library.
- [x] **Clean Demo Isolation:** Fictional profiles (`Demo User` & `Demo Guardian`) and simulated delivery pipelines operate purely in-memory and in SharedPreferences without modifying Room user data.

### Pending Controls & Production Hardening
- [ ] **Local Room Database Encryption (SQLCipher):**
  - *Current State:* `safeher_guardians.db` is stored unencrypted in the app's private sandbox (`/data/data/com.safeher.app/databases/`).
  - *Roadmap:* To prevent physical memory extraction on rooted devices without causing data loss for existing users, migrate Room to **SQLCipher for Android** using an in-place migration rather than a destructive drop-table.
- [ ] **Android Backup Protection (`android:allowBackup`):**
  - *Current State:* `AndroidManifest.xml` has `android:allowBackup="true"`.
  - *Recommendation:* Prior to Google Play release, set `android:allowBackup="false"` or configure `android:fullBackupContent` / `dataExtractionRules` to exclude database and SharedPreferences files from Google Drive cloud backups.
- [ ] **App Check Enforcement in Cloud Functions:**
  - *Current State:* `enforceAppCheck: false` in `functions/src/index.ts` to allow local emulator testing.
  - *Production Setting:* Flip `enforceAppCheck: true` in production once Play Integrity SHA fingerprints are registered in Firebase Console.
- [ ] **Lock Screen Notification Privacy:**
  - *Recommendation:* Configure notification channels with `NotificationCompat.VISIBILITY_PRIVATE` to conceal sender coordinates and emergency text from the lock screen when the phone is locked.

---

## 4. Secret & Credential Management

1. **Local Developer Secrets:**
   - Placed in `local.properties` (never staged).
   - `PLACES_API_KEY`: Handled gracefully if absent (defaults to empty string).
2. **Firebase Configuration:**
   - `app/google-services.json` is ignored by `.gitignore`.
   - The demo build variant (`demoDebug`) is specifically designed to assemble and run without this file.
3. **Signing Keystores:**
   - Production keystores (`*.jks`, `*.keystore`) must be maintained outside version control and injected via CI/CD secrets or environment variables.

---

## 5. Incident Response & Credential Rotation

If a personal API key or Firebase credential is ever accidentally exposed:

1. **Immediate Revocation:**
   - In Google Cloud Console: Immediately delete or regenerate the exposed API key under **APIs & Services** > **Credentials**.
   - In Firebase Console: Rotate the Web API key or service account private key under **Project Settings**.
2. **Git Remediation (Clean History):**
   - If committed locally before pushing: `git reset --soft HEAD~1`, remove the file, add to `.gitignore`, and re-commit.
   - If pushed to GitHub: Immediately revoke the secret at the provider level first. Then rewrite Git history using `git-filter-repo` or BFG Repo-Cleaner, and force-push with branch protection temporarily disabled.
3. **Notify Affected Parties:**
   - Report the exposure timeline, rotated key IDs, and verified audit logs in Cloud Logging.
