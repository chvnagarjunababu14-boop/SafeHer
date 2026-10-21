# Contributing to SafeHer

Thank you for your interest in contributing to **SafeHer**. Because SafeHer is a personal safety and emergency application, code quality, stability, test coverage, and user privacy are of paramount importance.

Please review this guide before submitting code or opening a pull request.

---

## 1. Development Principles

1. **Safety First:** Emergency features (SOS triggers, guardian alerts, location broadcasting) must operate reliably during high-stress scenarios. Avoid complex multi-step interactions during emergency workflows.
2. **Explicit Consent & Privacy:** Never track or share user location without explicit user action. Respect the user's choice to opt out of location sharing.
3. **No Destructive Migrations:** Local guardian contacts and safety journey history must survive app updates. Never use `fallbackToDestructiveMigration()` on `SafeHerDatabase`.
4. **Offline Resilience:** The application core (local Room database, map caching, and SMS fallback) must function without an internet connection.
5. **Zero Secret Leakage:** Never commit credentials, private keys, `google-services.json`, or personal phone numbers.

---

## 2. Setting Up Your Development Environment

1. Follow the step-by-step instructions in [docs/SETUP.md](file:///docs/SETUP.md).
2. Use **JDK 17 or higher** (Android Studio bundled JBR recommended).
3. Ensure the project builds and all unit tests pass locally before making changes:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   $env:Path = "$env:JAVA_HOME\bin;" + $env:Path

   .\gradlew.bat testDemoDebugUnitTest
   .\gradlew.bat assembleDemoDebug
   ```

---

## 3. Git Workflow & Branching

1. Fork or clone the repository from [`https://github.com/chvnagarjunababu14-boop/SafeHer`](https://github.com/chvnagarjunababu14-boop/SafeHer).
2. Branch from `main`:
   - Feature branches: `feature/your-feature-name`
   - Bugfix branches: `fix/issue-description`
   - Documentation: `docs/topic-name`
3. Keep commits atomic and write descriptive commit messages:
   ```text
   feat(sos): add confirmation vibration on emergency dispatch
   fix(database): enforce non-null constraint on journey event types
   docs(setup): update Windows 11 Java environment variables
   ```

---

## 4. Coding Standards & Conventions

### Kotlin & Jetpack Compose
- Follow official Kotlin coding style (`kotlin.code.style=official` in `gradle.properties`).
- Follow Unidirectional Data Flow (UDF) in Compose screens:
  - ViewModels expose `StateFlow<UiState>`.
  - Composables accept state values and emit event callbacks (e.g., `onTabSelected = { ... }`).
  - Keep composables focused and reusable; avoid business logic inside Composable functions.
- Use Material 3 design tokens defined in [`com.safeher.app.ui.theme`](file:///app/src/main/java/com/safeher/app/ui/theme):
  - Primary Purple: `#7C3AED`
  - Lavender Background: `#F8F6FD`
  - Pink Accent: `#EC4899`
  - Crimson SOS: `#EF4444`

### Java Room Entities & DAOs
- In Java entities, always annotate non-nullable fields and constructor parameters with `@androidx.annotation.NonNull`. Room generates nullable schema expectations for unannotated Java Strings, causing schema mismatches against `TEXT NOT NULL` migration DDL.

### TypeScript / Cloud Functions Backend
- Follow strict TypeScript typing in `functions/src/`.
- Ensure all callable functions validate `request.auth != null` and sanitize input parameters.
- Run `npm test` inside `functions/` to verify backend tests pass.

---

## 5. Testing Requirements

Every pull request must pass all existing tests and add new tests for new functionality:

1. **Run Full Test Suite:**
   ```powershell
   .\gradlew.bat testDemoDebugUnitTest
   ```
2. **Verify Both Flavors Build Cleanly:**
   ```powershell
   .\gradlew.bat assembleDemoDebug
   .\gradlew.bat assembleProdDebug
   ```
3. **Database Migrations:**
   - Any schema changes must be accompanied by a manual `Migration(N, N+1)` in `SafeHerDatabase.java` and a corresponding test in [`DatabaseMigrationTest.kt`](file:///app/src/test/java/com/safeher/app/DatabaseMigrationTest.kt).
4. **Demo Mode Parity:**
   - If introducing a new cloud-backed feature, add a corresponding mock implementation in `com.safeher.app.data.demo` so the offline Demo Mode continues to evaluate out-of-the-box.

---

## 6. Submitting a Pull Request

1. Push your branch to GitHub:
   ```powershell
   git push -u origin feature/your-feature-name
   ```
2. Open a Pull Request against `main`.
3. Provide a clear description containing:
   - Summary of changes and motivation.
   - Exact Gradle test commands executed and results.
   - Screen recording or screenshots for UI changes.
   - Confirmation that no secrets or personal data were staged.
