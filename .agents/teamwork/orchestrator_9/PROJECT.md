# Project: Orbit Money (KMP Rebuild) — Milestone 5

## Architecture
- Modules:
  - `:composeApp`: Shared Compose Multiplatform UI, ViewModels, StateFlow state holders, M3E theme system, pure-Kotlin financial calculation engines, `OrbitRepository`, and Ktor API / Google Drive sync clients.
    - `commonMain`: 95%+ of codebase — all 14 screens, shared M3E components, Canvas charts, domain engines, repository logic.
    - `androidMain`: Native Android integrations (`BiometricPrompt`, `WindowInfoTracker` foldable posture, 120Hz display refresh, Android haptics, SAF launchers, `AndroidGoogleDriveClient`).
    - `wasmJsMain`: Installable Web PWA target (browser entry, IndexedDB/localStorage snapshot persistence, Web GIS Google Drive, Service Worker `sw.js`, `manifest.webmanifest`).
  - `:androidApp`: Thin Android app module (`MainActivity`, `AndroidManifest.xml`, splash screen, shortcuts, signing config, branch-aware `copyDebugApkToRoot` task producing `OrbitBeta-debug.apk` on `Beta`).
  - `backend/`: Preserved untouched Node.js + Express + SQLite backend.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Gradle & Multiplatform Scaffold | Root KMP Gradle build, libs.versions.toml, :composeApp, :androidApp, Android + wasmJs targets | M1 | R1 |
| 2 | Pure Kotlin Financial Domain Engines | 8 engines (Budget, Upcoming, Rules, Transfers, Net Worth, Trends, MHA, Calculators) with integer cents | M2 | R2 |
| 3 | Dual Storage OrbitRepository | LocalSnapshotEngine (Okio/IndexedDB/Drive) and SelfHostedApiEngine (Ktor client to Express) | M2 | R2 |
| 4 | M3E Theme & Primitives | Orbit Emerald & presets, physics springs, MorphingPageHero, RollingTickerText, AdaptiveNavigationShell | M3 | R3 |
| 5 | Interactive Canvas Charts | NetWorthAreaChart, SpendingTrendsBarChart, BudgetRingGauge, RetirementProjectionChart with touch scrubbing | M3 | R3 |
| 6 | Onboarding & Dashboard | Onboarding walkthrough, StorageMode selector, customizable M3E Dashboard with reorderable cards | M4 | R3 |
| 7 | Transactions & Batch Actions | 60fps scrolling, swipeable rows, batch actions, provenance editing (original vs edited), CSV import | M5 | R3 |
| 8 | Categories & Rules Engine UI | Category hierarchy management and Transaction Rules builder with preview | M5 | R2/R3 |
| 9 | Budgets, Upcoming & Goals | Monthly budgets with ring gauge, recurring bill projections with auto-candidates, savings goals | M6 | R2/R3 |
| 10 | Accounts, Credit Perks & Net Worth | Grouped accounts, credit card profile sheets (annual fee, perks), Net Worth analytics screen | M7 | R2/R3 |
| 11 | Spending Trends & MHA Tracker | Trends charts/breakdowns, MHA 3-limit housing allowance progress & 2026 tax savings estimator | M8 | R2/R3 |
| 12 | Housing & Retirement Calculators | Buy vs Rent amortization calculator and Monte Carlo / deterministic retirement projection | M9 | R2/R3 |
| 13 | Household & Modular Settings | Multi-user household permissions/invites, modular settings blocks (sync, drive, theme, lock) | M10 | R2/R3 |
| 14 | Native Android Polish & Drive Sync | BiometricPrompt, 120Hz, foldable posture, Android shortcuts, Google Drive appDataFolder sync | M11 | R4 |
| 15 | Wasm PWA Docker Cutover & APK Delivery | Remove legacy frontend, update Dockerfile for Wasm, update build scripts, compile OrbitBeta-debug.apk | M12 | R1/R4 |

## Milestones Status
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | KMP Gradle & Shell Scaffold | Root KMP setup, libs.versions.toml, settings.gradle.kts, composeApp, androidApp, compile verification | none | DONE |
| 2 | Domain Engines & Dual Repository | Models, 8 financial engines, MoneyUtils, unit tests, OrbitRepository, Local & Api engines | M1 | DONE |
| 3 | M3E Theme, Primitives & Canvas Charts | Color presets, typography, springs, AdaptiveNavigationShell, PageHero, Tickers, 4 Canvas charts | M2 | DONE |
| 4 | Onboarding & Dashboard | Onboarding flow, StorageMode picker, DashboardScreen, cards, customization sheet | M3 | DONE |
| 5 | Transactions, Categories & Rules | TransactionsScreen, swipeable rows, editor sheet, CSV import, Categories, Rules | M3 | IN PROGRESS |
