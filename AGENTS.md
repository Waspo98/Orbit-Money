# AGENTS.md

## Stack
- Frontend: React + Vite
- Backend: Node + Express
- Data: SQLite
- Deploy: Docker Compose on Windows
- App type: installable PWA

## Non-negotiables
- Do not remove existing features unless explicitly asked.
- Do not change ports, volume names, or auth behavior unless explicitly asked.
- Preserve Docker behavior and deployment compatibility.
- Keep mobile UX polished.
- Keep the PWA installable.

## Product values
- Orbit Money should feel simple, friendly, and visually clear. Accuracy is
  sacred for financial calculations, so it is acceptable to add complexity when
  correctness requires it.
- Primary users are household owners. Build as if the app may eventually have
  public users, but do not prematurely add public
  SaaS complexity unless the task calls for it.
- Prioritize visual clarity first, speed of entry second, and deep financial
  accuracy as a non-negotiable foundation beneath both.
- Mobile is the primary experience. Desktop is a close second. Unfolded phones
  and tablets with two-column layouts are useful but lower priority.
- Prefer focused screens with simpler choices. Hide advanced or less common
  options behind dialogs, sheets, detail views, or additional settings.
- Match the current balance of simplicity and power in the app. Do not make the
  UI feel like either a toy or a dense professional dashboard.
- Make the codebase teachable for a newer developer through clear names, local
  patterns, and small explanatory comments where they prevent confusion.

## Decision rules for agents
- Before creating any page, component, hook, service, route, schema, or helper,
  search for the existing closest primitive or pattern and use it as the default
  starting point.
- If an existing primitive is almost right but has tradeoffs, pause and explain
  the tradeoff before choosing a one-off workaround. Be specific: what would be
  awkward about reuse, what a workaround would cost, and whether improving the
  primitive would help future work.
- When code reveals an imperfect repeated pattern, flag it. The likely preferred
  direction is to improve the shared pattern immediately, but explain scope and
  risk first.
- Refactoring means changing code structure without intentionally changing user
  behavior. When already working in an area, prefer cleaning the pattern properly
  if the change is understandable, testable, and not too broad.
- Template adherence matters even when deviations work. Keep similar entities
  mechanically similar: routes should look like routes, hooks like hooks,
  components like components, and tests like tests.
- Calculation correctness is the highest priority. Treat money math, dates,
  budgets, recurring transactions, transfers, refunds, and reports as product
  logic that requires extra care and validation.
- Do not delete files, features, or large blocks of code without explicit
  approval. If something appears unused, report it first unless the user has
  already approved removal.
- Ask before touching auth, Docker, ports, volumes, database schema, migrations,
  deployment scripts, or storage/backup behavior. Explain the impact first.
- For broad changes, present a short plan before editing. Then work step by step
  and keep changes small enough to review.

## Token conservation & execution efficiency
- **Mandatory Session Kickoff Protocol:** In the first response of every new chat session, inspect the current branch and status:
  ```powershell
  git status; git branch --show-current
  ```
  Explicitly confirm with the user which branch to work on before modifying files.
- **Headless / No Emulator:** Never launch, start, inspect, or capture screenshots from an Android emulator or device via ADB/CLI unless explicitly requested by the user. Rely exclusively on Gradle/Vite compilation, unit tests, and GitHub release downloads.
- **Zero-Polling on Asynchronous Tasks (CRITICAL):** Long-running commands (Gradle builds, Docker builds, unit tests) run in the background. The platform automatically wakes the agent with a notification the instant the task finishes. **NEVER poll `manage_task: status` in a loop**, and **NEVER spam `schedule` timers** to check build progress. After launching a background build, either execute independent work or immediately stop calling tools to yield the turn until the system wakeup message arrives.
- **Compiler Circuit-Breaker:** If compilation or a test fails twice consecutively with the same or related error, STOP making speculative code edits. Inspect the exact class signature, stack trace, or library source definitions before making further changes.
- **Concise Dialogue & Zero Code Duplication:** Keep dialogue punchy and focused on decisions, architecture, and verification. Avoid pasting full file listings or large redundant blocks into chat dialogue.
- **Zero-Orphan Policy:** When refactoring or replacing components, libraries, or models, remove old implementations in the same commit. Non-trivial tasks must be committed and pushed with conventional commit prefixes (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`).

## Project-specific notes
- Public app URL is handled through Cloudflare Tunnel.
- Public-facing services must stay compatible with the existing Docker/network setup.
- Update docs when behavior changes.
- If storage or backup behavior changes, flag it clearly.
- Beta is Docker-only. When asked to deploy beta, use
  `cmd /c scripts\deploy-beta.cmd` from the repo root, which delegates to
  `deploy\beta\deploy-beta.cmd` and runs Docker Compose project
  `orbitmoney-beta` on host port `5019`.
- Do not use a Vite dev server, local preview server, or `start-beta` helper for
  beta. There is intentionally no supported non-Docker beta deployment path.
- Public Docker installs pull `ghcr.io/waspo98/orbit-money:latest`. Maintainer
  beta publishes `ghcr.io/waspo98/orbit-money:beta`, then locally builds and
  restarts the beta container. Maintainer deploy scripts do not pull GHCR
  images; they build, push, locally build, restart, and verify the running
  container version.
- Maintainer GitHub Actions deploys require the self-hosted Windows runner
  service `actions.runner.Waspo98-Orbit-Money.orbit-money-server`. If deploy
  jobs remain queued, check `Get-Service "actions.runner.*"`. The repair script
  is `scripts\install-actions-runner-service.ps1` and must be run from an
  Administrator PowerShell session.

## Versioning and releases
- Do not bump the app version for routine rebuilds, beta deploys, live deploys,
  or small follow-up fixes. Redeploying many times in a day should not create
  many versions.
- Treat version bumps as release milestones. Suggest a version bump when work is
  being promoted from Beta to main, when a meaningful user-facing feature lands,
  when calculation behavior changes, or when a batch of fixes is ready to be
  described as a release.
- Ask before changing version files, creating Git tags, or creating GitHub
  Releases. Include the recommended version number and why it is a patch, minor,
  or major bump.
- Prefer semantic versions for the app: patch for small fixes, minor for new
  features or meaningful improvements, and major only for public/user-visible
  breaking changes. Keep the app in `0.x` until it is considered public-ready.
- Keep visible app version labels, package versions, Git tags, and GitHub
  Releases aligned when doing an intentional release.
- Use a semi-automatic release workflow. When the user asks to prepare a
  release, inspect the changes since the previous Git tag/release, recommend the
  next semantic version, explain why it is patch/minor/major, and wait for
  approval before changing version files, tagging, or publishing a GitHub
  Release.
- Release notes should be written for normal users first and maintainers second:
  summarize user-facing changes, setup/deployment impact, migrations or backup
  cautions, dependency/security updates, and known follow-up work. Do not rely
  only on GitHub's generated notes unless the user explicitly asks for that.
- A Docker deploy and a GitHub Release are separate actions. Deploys may happen
  frequently from `main` or `Beta`; releases should mark stable, named
  milestones with a Git tag such as `v0.62.0`.
- When publishing a release, prefer this order: confirm clean checks, update
  version labels/files if approved, update changelog or release notes, commit,
  tag the release commit, push the branch and tag, then create the GitHub
  Release from that tag.

## Preferred workflow for agents
- Inspect before editing.
- Do not read `.env` unless the task requires debugging runtime config.
- Prefer small, reversible changes.
- Before changing auth, Docker, ports, volumes, or database storage, explain the impact first.
- Use Node.js 20.19+ for local validation. The public repo does not track a
  portable Node runtime; `scripts\build-frontend.cmd` and
  `scripts\check-backend.cmd` prefer a private `.tools` runtime if one exists
  and fall back to system Node/npm.
- After frontend changes, run `cmd /c scripts\build-frontend.cmd` from the repo root
  or `npm run build` in `frontend` with Node.js 20+ on PATH.
- After backend changes, run `cmd /c scripts\check-backend.cmd` from the repo
  root, plus any route/service-specific check that exists.
- For schema changes, add a new migration; never edit an applied migration.

## Frontend conventions
- Before creating new UI, first look for an existing component or CSS primitive
  that matches the interaction: page hero, modal/dialog, dropdown, selectable
  list item, sheet, button, card, pill, chart, or form field. Extend the shared
  primitive when a pattern appears in more than one place.
- Reuse `frontend/src/components/PageHero.jsx` and its exported
  `useMorphingPageHero` hook for morphing page headers; do not copy the
  scroll/resize measurement code into pages.
- PageHero color is intentionally app-wide and static. Do not add per-page
  `.page-hero-*` color overrides; variant classes are for layout and content
  tweaks only.
- Keep primary navigation data in `BottomTabs.jsx`; `DesktopSidebar.jsx`
  imports it so mobile and desktop stay in sync.
- Use `AnimatedModal`, `DropdownMenu`, `FilterSheet`, `MoreSheet`, and
  `SyncErrorBanner` rather than recreating equivalent overlays or menus.
- Use `DashboardCard` for dashboard-style cards, `SettingsCard` for settings
  sections, `ChartFrame` for accessible SVG chart shells, `DateInput` for
  strict date fields, and `FinancialFormGrid`/`FinancialField` for money and
  planning forms.
- Use `SelectableListItem` for two-line selectable rows such as goal/category
  pickers; selected rows should use the shared green active treatment.
- Use `useAppDialog` from `frontend/src/components/AppDialog.jsx` for alert
  and confirmation flows; avoid native `alert()` / `confirm()` in page code.
- Use Title Case for visible button labels, for example `+ New Category`,
  `Add Budget`, and `Save`.
- Overlay behavior is intentional: modals/dialogs/sheets blur the app backdrop
  and lock body scroll; dropdown menus stay anchored to their trigger, do not
  blur the page, and do not lock scroll.
- Keep route-level pages focused on data loading and composition. Move shared
  display primitives into `frontend/src/components` once a pattern appears in
  two places.

## Backend conventions
- Validate and normalize route inputs at the route boundary before writing to
  SQLite.
- Preserve original/edited transaction provenance. User edits write to
  `edited_*` fields with `source='user'`; rules and system-derived edits must
  not mutate original imported values.
- Keep account, transaction, budget, and rule behavior compatible with the
  existing SQLite migrations and Docker volume.

## Android build, testing & GitHub release delivery
- **Stack & Toolchain:** Capacitor wraps the Vite frontend into an Android Studio Gradle project in `frontend/android/`.
  - Android SDK: `C:\Users\nealo\AppData\Local\Android\Sdk`
  - Java: JDK 17+ on PATH
  - Android package ID: `app.orbitmoney.client` (standard) / `app.orbitmoney.beta` (beta)
- **Windows CLI Build Command:** Use `scripts\build-apk.cmd` (or `powershell scripts\build-apk.ps1`) to run the full pipeline:
  1. Build frontend: `npm run build`
  2. Sync Capacitor: `npx cap sync android`
  3. Compile Gradle debug APK: `.\gradlew.bat assembleDebug` in `frontend\android`
  4. Copy APK to root:
     - On standard branches (`main` or feature): copies to `OrbitMoney-debug.apk`
     - On the `beta` branch: copies to `OrbitBeta-debug.apk`
  5. Upload to GitHub Releases (`gh release upload --clobber`):
     - `main` / feature: tag `debug-latest`
     - `beta`: tag `beta-latest`
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
  - Intermediate responses (e.g. when yielding while Gradle is compiling in the background) do NOT include this link.

## Done when
- Build passes
- Existing features still work
- Mobile layout still works well
- Docs are updated
