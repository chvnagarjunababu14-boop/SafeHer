# SafeHer — Testing & Quality Assurance Handbook

This handbook provides instructions for running automated unit tests, adding new test cases, safely verifying database migrations, evaluating the self-contained Demo Mode, testing production cloud integrations safely, and executing manual regression verification.

---

## 1. Automated Test Suite Overview

SafeHer enforces strict unit testing across data models, database migrations, state machines, and view models.

| Test Suite File | Tests | Focus Area |
|---|---|---|
| [`AuthRepositoryTest.kt`](file:///app/src/test/java/com/safeher/app/AuthRepositoryTest.kt) | 12 | User state, validation, email/password, auth results |
| [`ContactsViewModelTest.kt`](file:///app/src/test/java/com/safeher/app/ContactsViewModelTest.kt) | 15 | Local guardian list, primary status, delete, error handling |
| [`DatabaseMigrationTest.kt`](file:///app/src/test/java/com/safeher/app/DatabaseMigrationTest.kt) | 8 | Room migration 1&rarr;2, 2&rarr;3, schema integrity, cascade delete |
| [`DemoModeTest.kt`](file:///app/src/test/java/com/safeher/app/DemoModeTest.kt) | 5 | Demo profile switching, invite linking, multi-stage SOS dispatch, acknowledgement simulation, reset isolation |
| [`EmergencyMessageBuilderTest.kt`](file:///app/src/test/java/com/safeher/app/EmergencyMessageBuilderTest.kt) | 10 | SMS formatting, coordinate truncation, maps URL construction |
| [`GuardianRepositoryTest.kt`](file:///app/src/test/java/com/safeher/app/GuardianRepositoryTest.kt) | 14 | Room DAO interactions, primary guardian enforcement |
| [`HomeViewModelTest.kt`](file:///app/src/test/java/com/safeher/app/HomeViewModelTest.kt) | 18 | Tab selection, SOS 2s hold timer, dialogs, quick actions |
| [`JourneyRepositoryTest.kt`](file:///app/src/test/java/com/safeher/app/JourneyRepositoryTest.kt) | 16 | Journey lifecycle, ETA calculation, status transitions |
| [`JourneysViewModelTest.kt`](file:///app/src/test/java/com/safeher/app/JourneysViewModelTest.kt) | 15 | Journey creation, check-in pings, event history logging |
| [`MapViewModelTest.kt`](file:///app/src/test/java/com/safeher/app/MapViewModelTest.kt) | 10 | GPS tracking toggle, permission state, location flow |
| [`PhoneNumberHelperTest.kt`](file:///app/src/test/java/com/safeher/app/PhoneNumberHelperTest.kt) | 12 | Google libphonenumber parsing, E.164 normalization |
| [`RemoteSosTest.kt`](file:///app/src/test/java/com/safeher/app/RemoteSosTest.kt) | 12 | Cloud SOS payload construction, idempotency, retry rules |
| [`WelcomeViewModelTest.kt`](file:///app/src/test/java/com/safeher/app/WelcomeViewModelTest.kt) | 6 | Welcome onboarding progression |
| **Total Automated Unit Tests** | **153** | **100% Pass Rate** |

---

## 2. Running Automated Tests

### A. Run via Command Line (PowerShell)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# Execute all Demo flavor unit tests
.\gradlew.bat testDemoDebugUnitTest

# Execute all Production flavor unit tests
.\gradlew.bat testProdDebugUnitTest

# Execute an individual test class
.\gradlew.bat testDemoDebugUnitTest --tests "com.safeher.app.DemoModeTest"

# Execute a single test method
.\gradlew.bat testDemoDebugUnitTest --tests "com.safeher.app.DemoModeTest.demoRemoteSosRepository_dispatchesAndProgressesDelivery"
```

### B. Run Cloud Functions Backend Tests
```powershell
cd functions
npm test
cd ..
```
Runs 12 backend unit tests verifying unauthenticated request rejection, validation, and error codes.

### C. Inspecting Test Reports
When tests complete, Gradle generates detailed HTML reports:
```text
app/build/reports/tests/testDemoDebugUnitTest/index.html
```
Open this report in your browser to inspect test durations, standard output, and individual assertions:
```powershell
Start-Process "app\build\reports\tests\testDemoDebugUnitTest\index.html"
```

---

## 3. Instrumentation Tests Status

- **Current State:** Gradle dependencies for Android instrumentation testing (`androidx.test.ext:junit`, `androidx.test.espresso:espresso-core`, and `androidx.compose.ui:ui-test-junit4`) are configured in `app/build.gradle.kts`.
- **Directory:** There are currently no `.kt` test files in `app/src/androidTest/`. All existing test coverage is implemented via JVM unit tests in `app/src/test/`, providing fast execution without device dependencies.
- **Adding Instrumentation Tests:** Place UI/Compose instrumentation tests in `app/src/androidTest/java/com/safeher/app/` and run via `.\gradlew.bat connectedDemoDebugAndroidTest`.

---

## 4. How to Add a New Unit Test

1. Navigate to `app/src/test/java/com/safeher/app/`.
2. Follow existing test conventions:
   - Use JUnit 4 (`org.junit.Test`, `org.junit.Assert.*`).
   - Use coroutine test builders (`kotlinx.coroutines.runBlocking`).
   - Avoid heavyweight mocking frameworks where simple fakes or standard proxies suffice.
3. Example template:
   ```kotlin
   package com.safeher.app

   import org.junit.Assert.assertEquals
   import org.junit.Assert.assertTrue
   import org.junit.Test
   import kotlinx.coroutines.runBlocking

   class ExampleFeatureTest {

       @Test
       fun testFeatureBehavior() = runBlocking {
           // 1. Arrange
           val initialValue = 10

           // 2. Act
           val result = initialValue * 2

           // 3. Assert
           assertEquals(20, result)
       }
   }
   ```
4. Re-run tests to confirm:
   ```powershell
   .\gradlew.bat testDemoDebugUnitTest --tests "com.safeher.app.ExampleFeatureTest"
   ```

---

## 5. How to Test Database Migrations Safely

SafeHer stores critical local safety data (trusted emergency contacts, journey records, and safety check-in timestamps) in Room database `safeher_guardians.db`.

To test migrations without risking real data loss:
1. Always create a dedicated migration test in [`DatabaseMigrationTest.kt`](file:///app/src/test/java/com/safeher/app/DatabaseMigrationTest.kt).
2. Test schema creation for Version $N-1$.
3. Insert sample rows into the legacy schema.
4. Execute the `Migration(N-1, N)` instance.
5. Query the migrated database to verify:
   - Existing rows survived unmodified.
   - New columns contain expected default values.
   - Indices and foreign key constraints exist.
6. Verify that no destructive migrations (`fallbackToDestructiveMigration`) are ever registered on `SafeHerDatabase`.

---

## 6. How to Test Production Cloud Integrations Safely

When testing the production Firebase build (`prodDebug`):

> [!CAUTION]
> Never send test emergency alerts to real people or personal emergency contacts without prior warning.

1. **Use Dedicated Test Accounts:**
   - Create two test Google/Email accounts:
     - `tester.sender@yourdomain.com`
     - `tester.guardian@yourdomain.com`
2. **Use Dedicated Physical Devices or Separate Profiles:**
   - Install `app-prod-debug.apk` on Device A (Sender) and Device B (Guardian).
3. **Verify Guardian Linking:**
   - On Device A: Generate a guardian invitation code.
   - On Device B: Enter and accept the invitation code.
   - Verify that the relationship appears in Firestore `/relationships/`.
4. **Test SOS Dispatch:**
   - Trigger an emergency alert on Device A.
   - Confirm Device B receives the push notification via FCM.
   - Device B opens the alert and taps **Acknowledge Alert**.
   - Verify Device A updates to `Acknowledged` in real time.
5. **Clean Up Test Alerts:**
   - Cloud Functions retention cron automatically purges location data after 24 hours.

---

## 7. Manual Regression Checklist

Perform this verification before publishing a new build or approving a pull request:

### A. App Startup & Navigation
- [ ] App launches cleanly to the Home screen without flickering or crashing.
- [ ] Purple **Demo Mode Active** banner appears when running `demoDebug`.
- [ ] Bottom navigation smoothly switches between all 5 tabs: **Home**, **Map**, **Journeys**, **Contacts**, and **More**.
- [ ] Back-press exits the app from the Home tab or returns to Home from other tabs.

### B. Local Trusted Guardians (Room)
- [ ] Navigate to **Contacts** tab.
- [ ] Add a new local guardian with name, relationship, and phone number (`+91 98765 43210`).
- [ ] Edit the guardian's details and toggle "Primary Guardian".
- [ ] Restart the application; confirm the guardian persists in Room database.
- [ ] Delete the guardian; confirm list updates immediately.

### C. Safety Journeys & Check-in Timeline (Room)
- [ ] Navigate to **Journeys** tab.
- [ ] Start a journey with a destination and 15-minute expected duration.
- [ ] Verify active journey card appears with running countdown timer.
- [ ] Tap **Check In**; verify check-in count increments and timestamp logs to event timeline.
- [ ] Complete the journey; verify it appears in past journeys history.

### D. Foreground Location & Map
- [ ] On first launch, grant "While using the app" location permission.
- [ ] Navigate to **Map** tab. Verify map renders OpenStreetMap tiles.
- [ ] Verify blue location marker appears at current coordinates with accuracy circle.
- [ ] Deny location in Android Settings; reopen app; verify app displays graceful permission request card without crashing.

### E. Emergency SOS & SMS Fallback (Phase 7A)
- [ ] On Home screen, press and hold the red SOS button.
- [ ] Release before 2 seconds; verify trigger cancels cleanly without side effects.
- [ ] Hold for full 2 seconds; verify device vibrates and **Emergency SOS** dialog opens.
- [ ] Under **SMS Alert** tab, verify selected local guardians are listed.
- [ ] Tap **Dispatch SOS via SMS**; verify Android system SMS composer opens with prefilled emergency text and Google Maps link.
- [ ] Cancel SMS and return to app; confirm SafeHer remains functional.

### F. Demo Mode Remote SOS Simulation (Phase 7B)
- [ ] In the Emergency SOS dialog, select the **Push Notification** tab.
- [ ] Verify **Demo Guardian (Priya)** is checked.
- [ ] Tap **Dispatch Remote SOS**.
- [ ] Confirm progressive simulated pipeline transitions: `Queued (Simulated)` &rarr; `FCM Transit (Simulated)` &rarr; `App Receipt Simulated`.
- [ ] Tap **Simulate Guardian Acknowledgement**.
- [ ] Confirm badge updates to `Acknowledgement Simulated`.
- [ ] Verify that no actual cellular SMS or real cloud notifications were transmitted.

### G. Offline Resilience
- [ ] Put device into **Airplane Mode** (no Wi-Fi, no Mobile Data).
- [ ] Launch SafeHer Demo Mode.
- [ ] Verify local database, journey history, map tile cache, and demo SOS simulation all function properly offline.
