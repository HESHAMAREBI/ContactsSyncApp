---
trigger: always_on
---

# ContactsSyncApp Architecture & Workspace Execution Rules

## 1. Project Identity & Core Tech Stack
- **Project Scope:** ContactsSyncApp (Enterprise Android Application designed for central synchronization of corporate contacts from Google Sheets into managed Android devices via Microsoft Intune MDM).
- **Core Tech Stack:** Native Kotlin, Android Jetpack (WorkManager, Room DB with KSP, ViewModel, Coroutines & Flow), Google Sheets API v4, Android Contacts Content Provider, Java 17, Target/Compile SDK 36.
- **No Unnecessary Third-Party Bloat:** Do NOT introduce redundant network or database libraries. Rely strictly on official AndroidX, Jetpack, Coroutines, and Google API Client libraries.

## 2. Execution & Authority Protocol
- **FULL AUTHORITY:** You are authorized to write complete, functional Kotlin/XML code without placeholders or `TODO` comments. Do not ask for permission to write or modify code.
- **SAFE CONTACT OPERATIONS:** NEVER perform destructive wipes on user personal contacts. All phone contacts operations MUST strictly isolate application contacts using a dedicated `ACCOUNT_TYPE` (e.g., `com.jumhoria.contacts`) and batch operations (`applyBatch`) limited to safe chunk sizes (max 100 per transaction) to prevent `TransactionTooLargeException`.
- **NON-DESTRUCTIVE UPDATES:** Ensure updates to database entities or UI components do not break existing Room DAOs or background WorkManager jobs.

## 3. Architectural & Clean Kotlin Standards (STRICT)
- **MVVM & Clean Architecture:** Strictly separate concerns into packages: `data` (Remote/Sheets, Local/Room, Repositories), `ui` (Activities, Adapters, ViewModels), and `worker` (WorkManager sync tasks).
- **Single Source of Truth:** NEVER maintain duplicate Repository classes or objects. All data access must go through unified repository classes located in the `com.example.contactssyncapp.data` package.
- **Modern Kotlin Concurrency:** Use Kotlin Coroutines (`suspend` functions, `Dispatchers.IO`) and Flow/StateFlow for asynchronous tasks and UI state. Avoid blocking the Main Thread.

## 4. Reliability, Security & Anti-Crash Guard
- **Defensive Android Programming:** Prevent ANR (Application Not Responding) and Fatal Crashes (`NullPointerException`, `TransactionTooLargeException`, `SecurityException`) at all costs. Implement comprehensive `try-catch` blocks and structured logging (`Log.e` / `Log.d`).
- **Zero Warnings & Build Cleanliness:** Generated code must compile cleanly with Kotlin Symbol Processing (KSP) and strict Gradle Kotlin DSL (`build.gradle.kts`).
- **Enterprise MDM Config:** Support dynamic retrieval of Spreadsheet IDs and Sync intervals via Android Enterprise `RestrictionsManager` (Microsoft Intune Managed Configurations) with local fallbacks.
- **Background & Battery Constraints:** All `WorkManager` workers (`SyncWorker`) must strictly enforce network constraints (`NetworkType.CONNECTED`) and use exponential backoff retry policies to prevent battery drain.

## 5. Workflow & Pre-Implementation Audit (ANTI-DUPLICATION)
- **MANDATORY SCAN:** You are strictly prohibited from writing new features from scratch without first performing a deep search across existing Kotlin files, Room DAOs, layouts, and Gradle scripts.
- **NO REINVENTING THE WHEEL:** Utilize and fix existing infrastructure (e.g., existing `ContactDao`, `AppDatabase`, `NotificationHelper`) instead of writing duplicate helper classes or parallel databases.
- **EXPLICIT CONFIRMATION:** Before applying any refactoring or generating complex code, you must state: `"System Audit Complete:"` and list the relevant files found and your execution plan.

## 6. Documentation, Strict Path Tracking & Output Style
- **Visual Architecture:** Use clear Markdown tables and structured summaries when explaining logic or data-flow changes.
- **DEPLOYMENT TRACKER (MANDATORY FULL PATHS):** At the end of every response where files are modified, created, or deleted, you MUST output a clean Deployment Tracker list using explicit relative paths (e.g., `[MODIFY] app/src/main/java/com/example/contactssyncapp/data/ContactsSyncRepository.kt` or `[DELETE] app/src/main/java/com/example/contactssyncapp/ContactsSyncRepository.kt`).
- **Arabic Technical Communication:** When communicating with the user, use professional, high-level engineering Arabic mixed with accurate Android/Kotlin technical terms.

## 7. End-of-Day Workflow, Daily Archiving & Git Push Protocol (STRICT)
- **Session Termination Triggers:** Whenever the user states phrases like: `"نكتفي بهذا القدر"`, `"نلتقي غداً"`, `"ختام الجلسة"`, `"أرشف اليوم"`, or `/archive`, you MUST automatically execute the daily closing protocol.
- **Structured Daily Summary Storage:**
  1. Navigate to the base documentation path: `C:\Users\HLadmin\AndroidStudioProjects\Doc\`.
  2. Create (if not existing) a sub-folder organized by the current Year and Month, e.g., `YYYY-MM` (e.g., `2026-08/`).
  3. Generate a Markdown summary file named strictly with the full timestamp format: `daily_summary_YYYY_MM_DD_HHmm.md` (e.g., `daily_summary_2026_08_19_1830.md`).
  4. The summary file MUST include:
     - Comprehensive table of all completed tasks and bug fixes.
     - List of all created, modified, or deleted files with full paths.
     - Build/testing status and unresolved edge cases.
     - Recommended actionable roadmap for the next working session.
- **Automated GitHub Synchronization (Git Push):**
  - Prompt and provide the exact Git commands to stage, commit, and push all modified and new files (including the newly generated daily summary) to the remote GitHub repository:
    ```bash
    git add .
    git commit -m "feat(daily-sync): summary of work on YYYY-MM-DD HH:mm"
    git push origin main
    ```