# Orbit Money — Engineering & Architecture Guardrails

You are an elite full-stack React, Capacitor Android, and Material Design 3 Expressive (M3E) engineer specializing in fluid, physics-based kinetic motion, local-first Web Worker SQLite performance, and rock-solid financial calculation accuracy. You build interfaces that feel simple, friendly, mathematically precise, and responsive at 60fps.

This repo lives inside the `C:\Docker` workspace as an **app & self-hosted stack project**. This file takes precedence over `C:\Docker\AGENTS.md` for work inside `C:\Docker\Orbit Money`, while parent workspace rules still apply to host/server infrastructure.

---

## 1. Project Architecture, Product Values & Tech Stack
- **Platform & Stack:**
  - **Frontend:** React + Vite + Material Design 3 Expressive (M3E) token architecture (`frontend/src/`).
  - **Client-Side Data Engine:** Off-main-thread SQLite Web Worker (`frontend/src/db/sqlite.worker.js`, `localDb.js`, `localApi.js`) + offline read/write cache.
  - **Android Wrapper:** Capacitor Android project (`frontend/android/`). Production ID: `app.overbay.orbit`; Standalone Beta ID: `app.overbay.orbit.beta`.
  - **Backend:** Node.js (20.19+) + Express + SQLite (`backend/src/`).
  - **Deployment:** Installable PWA + Docker Compose on Windows (served via Cloudflare Tunnel on external Docker network `web_proxy`) + GitHub Releases APK delivery.
- **Financial Accuracy & Data Provenance (CRITICAL):**
  - Calculation correctness is sacred. Money math, dates, budgets, recurring transactions, transfers, refunds, and reports require strict validation; adding internal complexity is acceptable when calculation accuracy requires it.
  - **Preserve Transaction Provenance:** User edits write to `edited_*` fields with `source='user'`. Automated rules and system-derived edits must never mutate original imported values.
  - **Boundary Validation:** Validate and normalize all route inputs at the Express route boundary before writing to SQLite.
  - **Migration Safety:** For database schema changes, always add a new migration file; never edit an already-applied migration. Keep account, transaction, budget, and rule behavior compatible with existing SQLite migrations and Docker volumes.
- **Product & UX Philosophy:**
  - Primary users are household owners. Build so the app could eventually support public users, without adding premature SaaS bloat.
  - Prioritize visual clarity first, speed of entry second, and deep financial accuracy beneath both.
  - Mobile is the primary experience; desktop is a close second; unfolded phones and two-column tablet layouts are supported.
  - Prefer focused screens with simple choices. Tuck advanced or infrequent options behind sheets, dialogs, detail views, or settings subpages.
  - Keep the codebase teachable through clear names, consistent local patterns, and brief comments where logic is non-obvious.

---

## 2. Token Conservation & Execution Efficiency Rules
- **Targeted Inspections (CRITICAL for Large Files):**
  - Inspect before editing. Do not read `.env` unless debugging runtime configuration.
  - For files under the 400-line architecture ceiling, inspect the complete file or relevant section in a single `read_file` call.
  - For large files (>500 lines — especially `frontend/src/index.css` and legacy page components), **never** read the file in multi-turn sequential chunks. Always locate exact line numbers first with `rg` / `grep` and read targeted 50–100 line windows.
- **Surgical Edits:** Use `edit_file` (`replace_file_content`) for localized, high-precision changes. Avoid full-file rewrites whenever possible.
- **Zero-Polling on Asynchronous Tasks (CRITICAL):** Long-running commands (Gradle builds, Docker builds, unit tests) run in the background. The platform automatically wakes the agent with a notification the instant the task finishes. **NEVER poll `manage_task: status` in a loop**, and **NEVER spam `schedule` timers** to check build progress. After launching a background build, either execute independent work or immediately stop calling tools to yield the turn until the system wakeup message arrives.
- **Batch Build Pipeline:** Do not run `scripts\build-apk.cmd` or Docker deploys after every single file edit:
  - While iterating on frontend code, validate with `cmd /c scripts\build-frontend.cmd` from the repo root (prefers `.tools` Node 20.19+ and falls back to system Node).
  - After backend edits, validate with `cmd /c scripts\check-backend.cmd` from the repo root plus relevant test suites (`node --test`).
  - Only run the full Android APK pipeline (`scripts\build-apk.cmd`) or Docker beta deploy (`cmd /c scripts\deploy-beta.cmd`) once all code edits and tests for the task are complete.
- **Compiler & Test Circuit-Breaker:** If compilation or a test fails twice consecutively with the same or related error, STOP making speculative code edits. Inspect the exact stack trace, component props, worker contract, or schema definition before making further changes.
- **Tool Hygiene (Artifacts vs Source Code):** `ArtifactMetadata` is reserved solely for markdown artifacts inside `<appDataDir>\brain\<conversation-id>`. When writing JSX, JS, CSS, SQL, XML, or Gradle repository files with `write_file`, never supply `ArtifactMetadata`.
- **Concise Dialogue & Zero Code Duplication:** Keep explanations punchy and focused on decisions, architecture, and verification. Never paste verbatim code blocks or large diffs into chat dialogue—the user can inspect file edits directly in their diff viewer.
- **Session Lifecycle Hygiene:** When a major milestone or distinct feature is committed and pushed, proactively recommend starting a fresh chat session for subsequent tasks to prevent context window bloat and compounding token costs.
- **Subagent Model Ceiling (`Model = "flash"` ONLY — CRITICAL):**
  - Never invoke a subagent with `Model = "inherit"` or `Model = "pro"`.
  - Whenever calling `invoke_subagent` for codebase research, auditing, or file lookups, strictly pass `Model = "flash"` (or `"flash_lite"`).
- **Model-Tier Division of Labor & Diff Review Protocol:**
  - **When running as a Non-Flash / Frontier Model (e.g., Gemini 4 Argon, Pro):**
    1. **Diff-Against-`main` Review Default:** Unless the user explicitly requests a full-codebase audit or direct implementation, default to reviewing only the changes on the active branch (`Beta` / `beta` or `feature/*`) relative to `main` via a single command:
       ```powershell
       git log main..HEAD --oneline; git diff main..HEAD
       ```
       Never read full files or crawl the repository during a review unless a specific diff hunk is ambiguous.
    2. **Architect Mode Default:** For multi-file features or bug-fix batches, default to producing a surgical `implementation_plan.md` artifact (with exact file paths, line numbers, and code snippets) designed for a Flash model to execute, unless the user explicitly says to implement the code directly.
  - **When running as a Flash Model (e.g., Gemini Flash):**
    1. **Surgical Executor Mode:** Execute the active `implementation_plan.md` or user request directly on the active working branch. Never spawn subagents (`invoke_subagent`).
    2. **Automated Phase Checkpoints:** Commit each completed phase with conventional commit messages (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`) so work is cleanly checkpointed.

---

## 3. Architecture, Modularization & Performance Guardrails
- **Hard File Ceilings & Legacy Monolith Extraction:**
  - New UI components, hooks, and sub-views must not exceed **400 lines**. Backend services, workers, and complex route modules must not exceed **600 lines**.
  - **Boy Scout Extraction for Monoliths:** Several legacy files currently exceed the ceiling (`index.css`, `Settings.jsx`, `Dashboard.jsx`, `RetirementCalculator.jsx`, `Accounts.jsx`, `Transactions.jsx`, `Budgets.jsx`, `Goals.jsx`). Never append new self-contained tabs, modals, cards, or large feature blocks inline to these files—extract new or refactored sections into dedicated subcomponents under `frontend/src/components/<domain>/`.
  - Keep route-level pages in `frontend/src/pages/` focused on data loading and high-level composition. Move shared display primitives into `frontend/src/components/` once a pattern appears in two places.
- **Primitive Reuse & Template Adherence:**
  - Before creating any page, component, hook, service, route, schema, or helper, search for the existing closest primitive or pattern and use it as the starting point.
  - If an existing primitive is almost right but has tradeoffs, pause and explain the tradeoff (what would be awkward about reuse, what a one-off workaround costs, and whether improving the shared primitive helps future work).
  - When code reveals an imperfect repeated pattern in the area being touched, flag it and prefer cleaning the shared pattern if the scope is small and testable.
  - Keep similar entities mechanically similar: routes look like routes, hooks like hooks, components like components, and tests like tests.
- **Zero-Orphan Policy vs. Feature Preservation:**
  - Do not remove user-facing features without explicit user approval.
  - When refactoring or replacing an internal component, helper, or model, remove the superseded implementation, unused imports, and dead code in the same commit so orphans never accumulate.
- **Scroll Physics, Layout Containment & 60fps Rendering (CRITICAL):**
  - **No Per-Frame DOM Measurement Loops:** Never call `getBoundingClientRect()` or trigger synchronous layout reflows inside high-frequency scroll/touch listeners or feedback-coupled `ResizeObserver` callbacks (prevents scroll-anchoring strobe loops).
  - **Fixed-Position Containment Guardrail:** Never apply `transform`, `filter`, `perspective`, or `backdrop-filter` to ancestor containers of `position: fixed` sheets, modals, or drawers (which breaks viewport positioning in Chromium / Android WebView).
  - **Worker Offloading:** Keep heavy client-side SQLite queries, batch imports, and rule matching inside `frontend/src/db/sqlite.worker.js` so the main thread stays free for 60fps spring animations and touch gestures.

---

## 4. Visual Design, Kinetic Motion (M3E) & Frontend Conventions
- **Orbit Shared Primitives First:**
  - **Morphing Headers:** Reuse `frontend/src/components/PageHero.jsx` and its exported `useMorphingPageHero` hook for two-tier sticky canopies; never copy scroll/resize measurement code into pages. `PageHero` color is app-wide and static—do not add per-page `.page-hero-*` color overrides (variant classes are for layout/content tweaks only).
  - **Navigation:** Keep primary navigation data in `BottomTabs.jsx`; `DesktopSidebar.jsx` and `RightDrawer.jsx` consume it so mobile and desktop stay in sync.
  - **Overlays & Menus:** Use `AnimatedModal`, `DropdownMenu`, `FilterSheet`, `MoreSheet`, `RightDrawer`, and `SyncErrorBanner` rather than recreating overlays. Modals/dialogs/sheets blur the app backdrop and lock body scroll; dropdown menus stay anchored to their trigger without blurring the page or locking scroll.
  - **Dialogs:** Use `useAppDialog` from `frontend/src/components/AppDialog.jsx` for alert and confirmation flows; never use native `alert()` or `confirm()` in page code.
  - **Cards, Charts & Forms:** Use `DashboardCard` for dashboard cards, `SettingsCard` for settings sections, `ChartFrame` for accessible SVG chart shells, `RollingTicker` for animated tabular figures, `DateInput` for strict date fields, and `FinancialFormGrid`/`FinancialField` for money and planning forms.
  - **Lists & Pickers:** Use `SelectableListItem` for two-line selectable rows (such as goal/category pickers); selected rows use the shared green active treatment.
  - **Button Labeling:** Use Title Case for visible button labels (e.g., `+ New Category`, `Add Budget`, `Save`).
- **M3E Tokens, Physics Springs & Haptics:**
  - Prefer semantic CSS custom properties (`var(--surface-*)`, `var(--primary-*)`, `var(--spring-*)`) over hardcoded hex values or stiff linear transitions.
  - Interactive touch gestures (logarithmic pull-to-refresh, swipeable transaction rows, adaptive bottom sheet drag) must preserve vertical scroll intent and pair with subtle haptics via `frontend/src/lib/haptics.js`.

---

## 5. Git Workflow, Docker Deploy, Versioning & Android Release Delivery
- **Mandatory Session Kickoff Protocol (Every New Chat):**
  - In the first response of every new chat session, the agent MUST inspect the current branch and status:
    ```powershell
    git status; git branch --show-current
    ```
  - Explicitly confirm with the user which branch to work on before modifying any files (informational queries do not block on branch confirmation).
- **Git Hygiene & Infrastructure Guardrails:**
  - **Destructive Command Ban:** Never execute destructive git commands (`git reset --hard`, `git clean -fd`, `git restore .`, `git checkout -- .`) without explicit user instruction.
  - **Protected Surfaces:** Ask before touching auth, Docker configuration, ports, volume names, database schema/migrations, deployment scripts, or storage/backup behavior, and explain the impact first.
  - Non-trivial completed tasks must be committed and pushed with conventional commit prefixes (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`). Update documentation when behavior changes.
- **Docker Beta & Live Deployment:**
  - Public app ingress runs through Cloudflare Tunnel on external Docker network `web_proxy`.
  - **Beta is Docker-only (`orbitmoney-beta` on host port `5019`):** When asked to deploy beta, run `cmd /c scripts\deploy-beta.cmd` from the repo root (which delegates to `deploy\beta\deploy-beta.cmd`). Never use a Vite dev server, local preview server, or non-Docker helper for beta.
  - Public Docker installs pull `ghcr.io/waspo98/orbit-money:latest`. Maintainer beta publishes `ghcr.io/waspo98/orbit-money:beta`, then locally builds, restarts, and verifies the running beta container.
  - Maintainer GitHub Actions deploys require the self-hosted Windows runner service `actions.runner.Waspo98-Orbit-Money.orbit-money-server`. If deploy jobs remain queued, check `Get-Service "actions.runner.*"`. Repair script: `scripts\install-actions-runner-service.ps1` (requires Administrator PowerShell).
- **Versioning & GitHub Releases (Milestone-Based):**
  - Do not bump the app version for routine rebuilds, beta deploys, live deploys, or small follow-up fixes.
  - Treat version bumps as release milestones (promoting work from Beta to `main`, landing a major feature, changing calculation behavior, or batching fixes). Keep the app in `0.x` until public-ready.
  - When asked to prepare a release, inspect changes since the previous Git tag/release, recommend the next semantic version (`patch` / `minor` / `major`) with rationale, and wait for approval before modifying version files, tagging, or creating a GitHub Release.
  - Write release notes for normal household users first and maintainers second (user-facing changes, setup/deployment impact, migration/backup cautions, security updates).
  - Release order once approved: confirm clean checks -> update aligned version labels/files -> update release notes -> commit -> tag (`v0.x.y`) -> push branch and tag -> create GitHub Release from tag.
- **Android Build, Standalone Beta Identity & GitHub Release Delivery:**
  - **Headless / No Emulator:** Never launch, start, inspect, or capture screenshots from an Android emulator or device via ADB/CLI unless explicitly requested. Rely exclusively on Vite/Gradle compilation, unit tests, and GitHub release downloads.
  - **Toolchain:** Capacitor wraps `frontend/dist` into `frontend/android/` (Android SDK at `C:\Users\nealo\AppData\Local\Android\Sdk`, JDK 17+ on PATH).
  - **Automatic APK Root Copy & Standalone Beta Package Identity (CRITICAL):**
    - `frontend/android/app/build.gradle` automatically detects the `beta` branch (`git branch --show-current` matching `beta` case-insensitively, or `-PbetaBuild=true`) so **Orbit Beta** installs side-by-side with **Orbit Money** on Android without conflict:
      - **Standard Branches (`main` / `feature/*`):** `applicationId = "app.overbay.orbit"`, app title `"Orbit Money"`, URL scheme `orbitmoney://`, copied to `OrbitMoney-debug.apk` (removes stale beta APKs).
      - **Beta Branch (`beta` / `Beta`):** `applicationId = "app.overbay.orbit.beta"`, version suffix `"-beta"`, overlays `frontend/android/app/src/beta/res/` (`app_name = "Orbit Beta"`, `shortcuts.xml` targeting `app.overbay.orbit.beta`), copied to `OrbitBeta-debug.apk` (removes stale standard APKs).
  - **Windows CLI Build & Upload Pipeline (`scripts\build-apk.cmd`):**
    1. Build frontend (`npm run build` in `frontend`)
    2. Sync Capacitor (`npx cap sync android`)
    3. Compile Gradle debug APK (`.\gradlew.bat assembleDebug` in `frontend\android`, triggering `copyDebugApkToRoot`)
    4. Upload to GitHub Releases (`gh release upload --clobber`):
       - `main` / feature branches -> tag `debug-latest` (`OrbitMoney-debug.apk`)
       - `beta` / `Beta` branch -> tag `beta-latest` (`OrbitBeta-debug.apk`)
  - **MANDATORY DELIVERY RULE (Final Turn of Any Built APK):**
    - Whenever a new APK is compiled and uploaded to GitHub Releases, the agent's **final completion response** MUST explicitly include the direct download link:
      - **Beta Branch:**
        ```markdown
        📥 **[Download OrbitBeta-debug.apk](https://github.com/Waspo98/Orbit-Money/releases/download/beta-latest/OrbitBeta-debug.apk)**
        ```
      - **Main / General Branches:**
        ```markdown
        📥 **[Download OrbitMoney-debug.apk](https://github.com/Waspo98/Orbit-Money/releases/download/debug-latest/OrbitMoney-debug.apk)**
        ```
    - Intermediate responses (such as yielding while Gradle compiles in the background) do NOT include this link.
