# Orbit Money Technical Reference

This document covers architecture, database migrations, engine rules, API routes, and implementation notes for modifying or debugging Orbit Money.

## Stack

- **Shared app (`:composeApp`):** Kotlin Multiplatform (Kotlin 2.1.20) + Compose Multiplatform (1.7.3) + Material 3, compiling to Android (`androidTarget`, JVM 17) and WebAssembly (`wasmJs`, bundled as `orbitApp.js`).
- **Android wrapper (`:androidApp`):** `compileSdk 35`, `minSdk 24`, `targetSdk 35`. Handles splash screen, edge-to-edge display, adaptive 120Hz refresh rate, biometric app lock, foldable posture detection, Storage Access Framework (SAF) file import/export, Custom Tabs OIDC login, and launcher shortcuts.
- **Client libraries:** `kotlinx-coroutines`, `kotlinx-serialization-json`, `kotlinx-datetime`, `okio`, `ktor` 3.1.1 (`ktor-client-okhttp` on Android, `ktor-client-js` on Web), and `androidx.lifecycle` ViewModel Compose.
- **Backend (`backend/`):** Node.js 20.19+ + Express 4.21 + SQLite (`better-sqlite3`), `express-session` backed by `BetterSqliteSessionStore`, `multer` 2.x (CSV/backup uploads), `papaparse` (CSV parsing), and `web-push`.
- **Auth:** Local username/password login, OIDC login, or both; optional `X-API-Key` header for programmatic access.

## Dockerfile and Container Images

`Dockerfile` uses a two-stage build:

- **Stage 1 (`--platform=$BUILDPLATFORM eclipse-temurin:21-jdk` as `build-wasm`):** Runs once on the host builder architecture and reuses a pre-built host bundle at `composeApp/build/dist/wasmJs/productionExecutable/index.html` when present. Otherwise downloads Android command-line tools (`platforms;android-35`, `build-tools;35.0.0`) inside the build container and runs `./gradlew :composeApp:wasmJsBrowserDistribution -x compileProductionExecutableKotlinWasmJsOptimize --no-daemon`.
- **Stage 2 (`node:20-alpine` as `prod`):** Installs `sqlite` and `wget`, installs temporary build dependencies (`python3 make g++`) to compile `better-sqlite3`, runs `npm install --omit=dev --no-audit --no-fund`, removes build dependencies, copies `backend/src` to `/app/src` and the built `wasmJs` PWA bundle to `/app/public`, sets `org.opencontainers.image.version` from `APP_VERSION`, creates `/app/data`, and starts `node src/server.js` on port `5008`.

Container image targets and workflows:

- Public self-host installs use the standalone root `docker-compose.yml` pulling `ghcr.io/waspo98/orbit-money:latest` (`linux/amd64` and `linux/arm64`, host port `5008`, volume `orbit-money-data`, default bridge network).
- Maintainer live uses `deploy/live/docker-compose.yml` (`ghcr.io/waspo98/orbit-money:main`, host port `5008`, volume `orbit-money-data`, external `web_proxy` network).
- Maintainer beta uses `deploy/beta/docker-compose.yml` (`ghcr.io/waspo98/orbit-money:beta`, host port `5019`, volume `orbitmoney-beta_orbit-money-beta-data`, external `web_proxy` network).
- `.github/workflows/deploy-live.yml` and `.github/workflows/deploy-beta.yml` trigger only on manual `workflow_dispatch` (never automatically on push). Both build the `wasmJs` PWA bundle, build and publish branch image tags, recreate the local container, and verify the running `backend/package.json` version.
- `.github/workflows/publish-release-image.yml` runs on `release: [published]` (non-prerelease `vX.Y.Z` tags) and manual `workflow_dispatch`, using `docker buildx` to publish multi-arch (`linux/amd64`, `linux/arm64`) release tags (`vX.Y.Z`, `X.Y.Z`, `X.Y`, and `:latest`) without external GitHub Actions.

## Data Storage and Sync Modes

### Server Storage

All server state lives in Docker named volume `orbit-money-data` mounted at `/app/data`:

| File | Purpose |
|---|---|
| `budget.db` | SQLite database (accounts, transactions, categories, rules, budgets, goals, household, credit cards, sync config, logs) |
| `sessions.db` | Express session store (separate `better-sqlite3` WAL connection via `backend/src/services/sessionStore.js`) |
| `.session_secret` | Auto-generated 64-character hex cookie signing secret persisted when `SESSION_SECRET` is omitted from env |

### Client Storage Modes

`OrbitRepository` switches between two `OrbitStorageEngine` implementations:

- **Local mode (`StorageMode.LOCAL` / `LocalSnapshotEngine`):** Stores the full `OrbitWorkspaceSnapshot` JSON via `PlatformStorage` (atomic Okio file writes in Android internal `filesDir`, or `localStorage` with an in-memory fallback on Web). Supports direct SimpleFIN Bridge sync (`SimpleFinDirectClient`), client-side Rocket Money CSV import (`CsvImportEngine`), JSON export/restore, and optional Google Drive `appDataFolder` backups (`CloudSyncManager`).
- **Self-Hosted mode (`StorageMode.SELF_HOSTED` / `SelfHostedApiEngine`):** Communicates with the Express backend via `OrbitApiClient` and caches `OrbitWorkspaceSnapshot` locally in `PlatformStorage`. Cold starts load immediately from cache and fall back to read-only offline viewing when the server is unreachable. Owner households can read cached data offline indefinitely; shared/member households require an online validation within the last 7 days. Signing out clears cached household data.

### Web PWA Service Worker and Caching

`composeApp/src/wasmJsMain/resources/sw.js` (`CACHE_NAME = 'orbit-money-kmp-v3'`):

- Pre-caches `/`, `/index.html`, `/manifest.webmanifest`, `/orbitApp.js`, and app icons on install.
- Uses network-first for page navigations and cache-first for static assets, including Webpack's content-hashed `.wasm` chunks.
- Never intercepts `/api/*` or cross-origin requests.
- Handles `push` and `notificationclick` events for Web Push notifications (with `/notification-badge-96.png` badge icon).

`backend/src/server.js` sets `Cache-Control: no-store` (plus CDN/Cloudflare no-store headers) on `sw.js`, `Cache-Control: no-cache` on `index.html` and `manifest.webmanifest`, `public, max-age=31536000, immutable` on `/assets/*`, returns `404` for missing static asset URLs instead of falling back to `index.html`, and serves `index.html` for client-side routes.

## Database Schema (38 Migrations)

Versioned migrations in `backend/src/db/migrations/` run automatically on server startup. Never edit an applied migration; add a new numbered file for schema changes.

| Migration | Purpose |
|---|---|
| `001_initial_schema.sql` | Core tables (`accounts`, `transactions`, `categories`, `rules`, `simplefin_config`, `budgets`, `goals`) and 32 seeded Rocket Money categories |
| `002_seed_categories.sql` | Additional category seeds |
| `003_account_uniqueness.sql` | Composite unique index on `(institution, account_number_last4)` |
| `004_simplefin_sync_support.sql` | `sync_log` table and `cutover_date` on `simplefin_config` |
| `005_add_mortgage_type.sql` | Adds `mortgage` to `accounts.type` CHECK constraint (full table rebuild) |
| `006_account_sort_order.sql` | Adds `sort_order` to `accounts` |
| `007_transactions_updated_at.sql` | Adds `updated_at` to `transactions` (constant default + backfill from `imported_at`) |
| `008_original_edited_split.sql` | Renames `transactions.merchant` to `original_merchant`, adds `edited_*` and `edited_*_source` provenance columns |
| `009_budget_monthly.sql` | Per-month budgets `(category_id, month)` |
| `010_budget_global_amount.sql` | Reverts `budgets` to one global cap per `category_id`, keeping each category's most recent row |
| `011_account_estimated_value.sql` | Adds `estimated_value` to `accounts` for home-equity calculations |
| `012_mha_tracker.sql` | Adds MHA setting, account defaults, and transaction-level MHA overrides |
| `013_mha_category_defaults.sql` | Adds category-level MHA default eligibility |
| `014_mha_category_ignore_defaults.sql` | Adds category-level MHA default ignore behavior |
| `015_goal_allocations.sql` | Extends goals metadata and adds `goal_account_allocations` |
| `016_merchant_logo_cache.sql` | Adds cached merchant logo metadata |
| `017_goal_sort_order.sql` | Adds custom goal ordering |
| `018_account_balance_records.sql` | Adds dated account balance snapshots (`account_balance_records`) |
| `019_household_income.sql` | Adds `household_members` and dated `household_income_records` snapshots |
| `020_household_retirement_accounts.sql` | Links household members to existing retirement/HSA accounts |
| `021_integer_cents.sql` | Converts SQLite money columns to integer cents and separates percent vs fixed goal allocations |
| `022_upcoming_items.sql` | Adds `upcoming_items` and dismissed recurring suggestion keys |
| `023_multi_user_households.sql` | Adds users, households, memberships, OIDC ownership, household-scoped defaults, and migrates existing data to household `1` |
| `024_household_sharing.sql` | Adds pending email-based household shares |
| `025_rename_authentik_sub_to_oidc_sub.sql` | Renames OIDC subject column to `oidc_sub` |
| `026_neutral_default_household_name.sql` | Sets uncustomized default household name to `My Household` |
| `027_upcoming_recurrence_projection.sql` | Adds Upcoming recurrence-rule metadata and history-average amount projection settings |
| `028_upcoming_occurrence_reconciliation.sql` | Adds persisted Upcoming occurrence matching and missed-occurrence tracking |
| `029_permissions_import_batches.sql` | Adds household read/write permissions plus import preview and undo batch tracking |
| `030_user_preferences.sql` | Adds user-scoped UI preferences synced across devices |
| `031_push_notifications.sql` | Adds `push_subscriptions` and `notification_delivery_log` |
| `032_account_balance_record_source.sql` | Adds source metadata to balance records with household-scoped uniqueness |
| `033_credit_card_profiles.sql` | Adds `credit_card_profiles` for tracked credit cards |
| `034_clean_credit_card_profiles.sql` | Removes external catalog-specific card columns, keeping local card metadata |
| `035_credit_card_strategy.sql` | Adds `statement_closing_day`, `payment_due_day`, and `strategy_json` |
| `036_credit_card_billing.sql` | Adds `apr_bps`, `promo_apr_bps`, and `promo_end_date` |
| `037_credit_card_catalog.sql` | Adds household-scoped and shared credit card template catalog (`credit_card_catalog`) |
| `038_real_estate_and_sync_closure.sql` | Adds standalone properties (`is_property`, `property_name`), linked property loans (`linked_property_account_id`), and SimpleFIN missing/closed account tracking (`simplefin_last_seen_at`, `simplefin_missing_since`, `closure_prompt_dismissed_at`) |

## Key Data Model Rules

- **Sign convention:** Expenses are negative and income is positive (flipped from Rocket Money's positive-expense format on CSV import).
- **Money units:** SQLite and all Kotlin domain models/engines store currency as integer cents (`Long` / `typealias Cents = Long`). Express API request and response payloads convert at the route boundary via `backend/src/lib/money.js`. Percent fields remain decimal percent values; credit card APRs are stored in integer basis points (`0..10000`, where `2499` = `24.99%`).
- **Route boundary conventions:** Express handlers parse and validate parameters through `backend/src/lib/routeParams.js` and `backend/src/lib/localDate.js`, returning standardized envelopes via `backend/src/lib/http.js`.
- **Transaction provenance (migration 008):** Imported fields live in `original_*` columns and are never mutated by edits or rules. User and rule changes write to `edited_*` columns with `edited_*_source` (`'user'`, `'rule:{id}'`, `'system:*'`, or `NULL`). Display values resolve via `COALESCE(edited_X, original_X)`. User edits are sticky and are never overwritten by rules.
- **Multi-user households and sharing:** Tenant rows carry `household_id`. Owners invite partners by email; first OIDC login claims the pending invite, creates the `household_memberships` row, and retires the invite. Subsequent read/write access changes happen on `household_memberships`.
- **Backup restore:** Owner-only in Self-Hosted mode. Replaces the active household's data, remaps all foreign keys across accounts, categories, rules, transactions, goals, household members, upcoming items, import batches, and preferences, and keeps the current user as owner. SimpleFIN credentials are excluded and must be reconnected after restore.
- **Rules:** Conditions (`merchant`, `original_description`, `amount`, `account_id`, `category_id`) evaluate exclusively against original imported values so match counts stay stable and rules do not chain on prior renames. Actions (`rename`, `categorize`, `mark_transfer`, `mark_ignored`) apply in `priority DESC, id ASC` order; the first rule claiming a field wins.
- **Account types and real estate:** Account types are `checking`, `savings`, `credit`, `investment`, `loan`, `mortgage`, `cash`, and `other`. Migration 038 adds standalone property accounts (`is_property = 1`) and allows mortgages/loans to link to a property via `linked_property_account_id`. Net worth includes home equity (estimated value minus linked mortgage debt).
- **SimpleFIN closed-account detection:** When a previously synced unarchived SimpleFIN account disappears from a clean sync payload, `markMissingSimpleFinAccounts` stamps `simplefin_missing_since`. Users resolve the prompt via `POST /api/accounts/:id/resolve-closure` with `paid_off_keep_property`, `refinanced_keep_property`, `sold_property`, `sold_or_closed`, or `dismiss`.
- **Account balance records:** `account_balance_records` upserts by `(account_id, record_date)` and updates `accounts.current_balance` when the new record is the latest snapshot. Net worth history uses the latest snapshot on or before each month before falling back to transaction deltas.

## Features and Engines

### Dashboard

Customizable card grid (`DashboardScreen`, 15 default widgets persisted via preferences): Accounts, Budget Pulse, Top Spending, This Month, Biggest Monthly Transactions (with dashboard-only hide that does not set transaction `is_ignored`), Subscriptions / Recurring, Categorize Recent Transactions (swipe-style review queue unifying uncategorized items, noisy merchant text, and category changes), Month vs Last Month, Goals Progress, Goal Focus, Retirement Snapshot, MHA Tracker Summary, Upcoming: Bills and Income, Mortgage Snapshot, and Recent Transactions.

### Transactions and Rules

- Rocket Money CSV import (with preview, commit, and batch undo on the backend, plus direct local import in Local mode) auto-creates accounts when `(institution, last4)` does not match an existing account, flips signs, and generates rename rules from Custom Name columns.
- Full search, multi-account/multi-category filtering (including `uncategorized`), date presets, absolute-amount range, type filters, ignored/transfer filters, and sort options.
- Inline editing for merchant, category, notes, transfer, and ignored flags, transaction splitting, per-field reset to original, and "Mark as recurring" / "Create rule from transaction" shortcuts.
- Customizable transaction quick actions configured in Settings.
- Creating, editing, toggling, or deleting rules synchronously reapplies enabled rules while preserving manual user edits.

### SimpleFIN Sync

- Exchanges a one-shot base64 setup token for an access URL. Server mode stores the URL encrypted with AES-256-GCM using `SIMPLEFIN_ENCRYPTION_KEY`; Local mode syncs directly via `SimpleFinDirectClient`.
- Cutover-date overlap handling lets Rocket Money own pre-cutover history and SimpleFIN own post-cutover transactions.
- Transfer auto-matcher pairs opposite-sign same-day transactions in transfer categories using resettable `system:transfer_matcher` edits.
- Daily scheduler (default 6:00 AM) plus manual Sync Now, sync log tracking (`success`, `partial`, `error`), and missing/zero posted-date guards.

### Budgets and Spending Trends

- **Budgets (`BudgetsScreen`):** Global monthly cap per spending category (transfer and income categories excluded), month navigation, total spent / pace / remaining hero, interactive Spending Mix donut with **Categories** and **Merchants** modes, budgeted/unbudgeted ("Everything Else") rows, and persisted sort order (`budgetSortOption`).
- **Spending Trends (`SpendingTrendsScreen`):** 3-, 6-, or 12-month Income vs Expenses bar chart, per-category monthly history with budget reference line and summary stats (average, median, high month, over-budget count), and drill-down into filtered Transactions.

### Credit Card Tracker and Rewards Strategy

Powered by `CreditCardCycleEngine`, `CreditCardBenefitEngine`, `CreditCardStrategyEngine`, and `CardTransactionClassifier`:

- **Billing and cycle math (`CreditCardCycleEngine`):** Clamps `statement_closing_day` and `payment_due_day` (`1..31`) to month length via `LocalDateUtils.daysInMonth`. Derives `lastClose`, `nextClose`, `dueDateAfter`, rolling annual fee anniversaries (`nextAnnualFeeDate`, clamping Feb 29 to Feb 28 in non-leap years), utilization in basis points (green <10%, amber 10-30%, red >30%), upcoming payments due within 35 days, and estimated statement balance:
  $$\text{balanceAtClose} = \text{currentBalance} - \sum_{\text{date} > \text{lastClose}} \text{effectiveAmountCents}$$
  $$\text{estimatedStatementBalance} = \max(0, -\text{balanceAtClose})$$
- **Benefits v2 and value ledger (`CreditCardBenefitEngine`):** Up to 24 benefits per card with immutable IDs (`b_<id>`), cadences (`monthly`, `quarterly`, `semi_annual`, `annual` calendar or cardmember year, `every_4_years`), and usage histories capped at 120 entries. Calculates captured credits, annualized potential value, and effective annual fee (`annualFee - creditsCaptured`). Applying a card template merges new benefits and reward categories while keeping existing usage logs and custom entries.
- **Household 5/24 and welcome bonuses (`CreditCardStrategyEngine`):** Partitions personal cards opened in the last 24 months by household player (`cardholder_member_id`, fallback label, or unassigned), flags authorized user cards (`authorized_user_member_ids`) with an AU badge, excludes business cards by default unless overridden (`counts_toward_524_override`), surfaces missing-open-date warnings, and computes `nextSlotFreeDate` and `under524DropDate`. Welcome bonus tracking uses `CardTransactionClassifier.bonusSpendDeltaCents` to exclude payments, refunds, balance transfers, fees, interest, and cash advances, supports manual prior-spend offsets, and tracks `ON_TRACK`, `AT_RISK`, `SPEND_MET`, `RECEIVED`, and `EXPIRED` states.
- **Card editor and wallet art:** Configuration-proof `CardEditorDraft` in `AccountsViewModel` renders in a modal sheet on compact screens and inline in `AccountDetailInspectorPane` on wide screens. `CardArtworkThumbnail` resolves configured issuer card artwork URLs when present and falls back to `CardArtPalette` gradient presets (`sapphire-blue`, `amex-gold`, `venture-silver`, `apple-card`, `discover-plum`).

### Goals and Upcoming

- **Goals (`GoalsScreen`):** Two-tab layout (**Goals** and **Funding**). Links multiple active asset accounts (`checking`, `savings`, `cash`, `investment`, `other`) by percent or fixed dollar amount, supports stealing allocation from other goals when confirmed, derives historical progress charts with touch scrubbing, and provides an "Imagine" extra-monthly-savings ETA slider.
- **Upcoming (`UpcomingScreen`):** Tracks manual and accepted recurring bills, subscriptions, and income across weekly, biweekly, twice-monthly, monthly (day-of-month or nth-weekday), bimonthly, yearly, and custom cadences, with fixed or history-averaged amounts. Projects occurrences over the current month and next 62 days, reconciles synced transactions (marking unmatched items missed after 4 days), and surfaces recurring suggestions from transaction history.

### Household, Calculators, and MHA Tracker

- **Household (`HouseholdScreen`):** Stores member profiles, employment, gross/net pay, pay cadence, retirement contributions, employer match, benefits, linked retirement/HSA accounts, and dated income history snapshots.
- **Retirement Calculator (`RetirementCalculatorScreen`):** Three-tab workspace (**What-If**, **Runway**, **Accounts**) fed by Household profiles and user-selected linked accounts (`retirementIncludedAccountIds`). Compares Conservative, Balanced, and Aggressive scenarios in today's dollars with touch scrubbing, models retirement spending drawdown and depletion age, and separates HSA vs non-HSA bridge assets before age 65.
- **Housing Calculator (`HousingCalculatorScreen`):** Models monthly mortgage payment breakdowns and home-selling net proceeds.
- **MHA Tracker (`MhaTrackerScreen`):** Optional ministerial housing allowance tracker (**Tracker** and **Defaults** tabs) enforcing the IRS 3-limit exclusion test (actual eligible expenses, church designation cap, fair market rental value). Filters out ignored and transfer rows, supports per-year tax rate storage, Simple manual percentage mode, and Household estimate mode using bundled 2026 federal tax tables (`taxData2026.js` / `MhaTaxEngine.kt`) plus no-tax and single-rate state auto-estimates with freshness metadata.

### Layout, Navigation, Themes, and Android Integrations

- **Postures (`DevicePosture`):** `COMPACT_PHONE` (<600dp), `UNFOLDED_FOLDABLE` (>=600dp or unfolded hinge), `TABLET_DESKTOP` (>=1100dp or >=840dp landscape). Wide postures enable two-pane master-detail inspectors across Accounts, Budgets, Goals, Credit Card Tracker, and Settings.
- **Navigation (`AdaptiveNavigationShell` / `OrbitNavKey`):** 4 locked primary bottom tabs (Dashboard, Transactions, Budgets, Accounts) plus a More sheet on phones, foldables, and tablets; desktop web browsers (>=1200dp) render a persistent left sidebar. Horizontal slide transitions move between primary tabs; vertical push/pop transitions move to and from More destinations.
- **Settings (`SettingsScreen`):** Split-pane master-detail layout with live keyword search across 7 sections grouped into Personalize (**Look and Feel**, **Notifications**, **Turn On/Off App Features**), Connections & Data (**Storage, Sync & Sharing**, **SimpleFIN Sync**, **Backups & Maintenance**), and App (**About Orbit Money**).
- **Themes:** 5 presets (`Orbit Emerald`, `Ocean Slate`, `Warm Terracotta`, `Royal Indigo`, `Monochrome Ink`), System/Light/Dark modes, optional AMOLED true-black dark mode, Android Material You dynamic color support, and haptic feedback toggle.
- **Android specifics:** Standalone beta package identity (`app.overbay.orbit.beta`, app title `Orbit Beta`, scheme `orbitmoneybeta://`, gold-ring beta icon) installs side-by-side with production (`app.overbay.orbit`, scheme `orbitmoney://`). Includes `BiometricPrompt` app lock, launcher shortcuts (`Add Transaction`, `Budgets`, `Accounts`), SAF file import/export, Custom Tabs OIDC exchange (`POST /api/auth/oidc/native-exchange`), and adaptive 120Hz display refresh rate.

### Push Notifications

Requires `WEB_PUSH_PUBLIC_KEY`, `WEB_PUSH_PRIVATE_KEY`, and `WEB_PUSH_SUBJECT`. Subscriptions live in `push_subscriptions` and delivery deduplication lives in `notification_delivery_log`. Defaults to privacy-first text without dollar amounts. Supports weekly snapshots, paycheck/income alerts, SimpleFIN sync warnings, and selected-account manual balance snapshot reminders.

## API Endpoints

All `/api/*` endpoints except `/api/auth/*` and `/api/health` require authentication (`requireAuth`). Non-GET requests on data routes also enforce household write permission (`requireWriteForUnsafeMethods`).

### Auth and Health

| Method | Path | Description |
|---|---|---|
| GET | `/api/health` | Health check (`status`, version) |
| GET | `/api/auth/config` | Active auth provider, OIDC button label/URL, sample-data flag |
| POST | `/api/auth/login` | Local session login |
| POST | `/api/auth/sample` | Create and sign into a temporary sample household (`ENABLE_SAMPLE_DATA=1`) |
| GET | `/api/auth/oidc/login` | Start OIDC login flow |
| GET | `/api/auth/oidc/callback` | OIDC redirect callback |
| POST | `/api/auth/oidc/native-exchange` | Exchange a one-time native Android OIDC code for a session |
| POST | `/api/auth/logout` | End session |
| GET | `/api/auth/me` | Current authenticated user, household, and permissions |

### Preferences and Notifications

| Method | Path | Description |
|---|---|---|
| GET | `/api/preferences` | Fetch account-scoped UI preferences |
| PUT | `/api/preferences` | Bulk-update preferences |
| PUT | `/api/preferences/:key` | Update a single preference key |
| GET | `/api/notifications/config` | Web Push config, VAPID public key, and subscription count |
| POST | `/api/notifications/subscriptions` | Register or refresh a browser push subscription |
| POST | `/api/notifications/test` | Send a test push notification to the user's active devices |

### Transactions and Import

| Method | Path | Description |
|---|---|---|
| GET | `/api/transactions` | Paginated filtered/sorted list with display values, originals, and provenance |
| GET | `/api/transactions/review-queue` | Candidate transactions for the Dashboard review flow |
| POST | `/api/transactions` | Create a manual transaction |
| GET | `/api/transactions/:id` | Single transaction detail |
| PATCH | `/api/transactions/:id` | Partial edit writing to `edited_*` with `source = 'user'` |
| POST | `/api/transactions/:id/reset` | Clear edits on specified fields and reapply rules |
| DELETE | `/api/transactions/:id` | Delete transaction |
| POST | `/api/import/rocket-money` | Upload and import Rocket Money CSV |
| POST | `/api/import/rocket-money/preview` | Preview CSV import before committing |
| POST | `/api/import/rocket-money/:id/commit` | Commit a previewed import batch |
| POST | `/api/import/batches/:id/undo` | Undo an import batch |

### Accounts, Balance Records, and Credit Cards

| Method | Path | Description |
|---|---|---|
| GET | `/api/accounts?includeArchived=` | List accounts ordered by `sort_order` |
| POST | `/api/accounts` | Create an account or standalone property |
| POST | `/api/accounts/reorder` | Persist account sort order |
| GET | `/api/accounts/records` | List balance records across accounts |
| PUT | `/api/accounts/:id` | Update account metadata, estimated value, or linked property |
| POST | `/api/accounts/:id/archive` | Archive account |
| POST | `/api/accounts/:id/unarchive` | Unarchive account |
| GET | `/api/accounts/:id/records` | List dated balance snapshots for one account |
| POST | `/api/accounts/:id/records` | Upsert a dated balance snapshot |
| DELETE | `/api/accounts/:id/records/:recordId` | Delete a balance snapshot |
| POST | `/api/accounts/:id/resolve-closure` | Resolve a missing SimpleFIN account (`paid_off_keep_property`, `refinanced_keep_property`, `sold_property`, `sold_or_closed`, `dismiss`) |
| POST | `/api/accounts/:id/merge` | Merge source account and all dependent records into target account |
| DELETE | `/api/accounts/:id` | Delete account (requires 0 transactions) |
| PUT | `/api/accounts/:id/credit-card-profile` | Upsert credit card profile, billing days, APR BPS, benefits v2, and strategy JSON |
| PUT | `/api/accounts/:id/credit-card-profile/benefits/:benefitId/usage` | Upsert or clear benefit usage for a period key (120-entry cap) |
| DELETE | `/api/accounts/:id/credit-card-profile` | Delete credit card profile metadata while keeping the account |
| GET | `/api/card-catalog` | List credit card templates |
| GET | `/api/card-catalog/:id/artwork` | Fetch card template artwork |
| POST | `/api/card-catalog/sync` | Sync credit card catalog templates |

### Categories, Rules, Budgets, Trends, and Net Worth

| Method | Path | Description |
|---|---|---|
| GET / POST / PUT / DELETE | `/api/categories`, `/api/categories/:id` | CRUD for household categories |
| GET / POST / PUT / DELETE | `/api/rules`, `/api/rules/:id` | CRUD for rules (synchronous reapply on write) |
| GET | `/api/rules/:id/match-count` | Condition-only match count for one rule |
| POST | `/api/rules/preview` | Preview match count for unsaved conditions |
| PATCH | `/api/rules/:id/enabled` | Toggle enabled state and reapply |
| POST | `/api/rules/reapply-all` | Reapply all enabled rules across transactions |
| DELETE | `/api/rules/all` | Delete all rules and revert `rule:*` edits |
| GET | `/api/budgets?month=YYYY-MM` | Monthly budgeted/unbudgeted/inactive breakdown and totals |
| GET | `/api/budgets/months` | Months with transaction activity |
| PUT / DELETE | `/api/budgets`, `/api/budgets/:id` | Upsert or delete global monthly category cap |
| GET | `/api/spending-trends?months=3\|6\|12` | Monthly cash flow and per-category history |
| GET | `/api/net-worth?months=` | Current net worth, monthly history, and breakdown (up to 240 months or `all`) |

### Goals, Upcoming, Household, MHA, Logos, SimpleFIN, and Backups

| Method | Path | Description |
|---|---|---|
| GET / POST / PUT / DELETE | `/api/goals`, `/api/goals/:id`, `/api/goals/reorder` | Savings goals, multi-account allocations, progress history, and reorder |
| GET / POST / PUT / DELETE | `/api/upcoming`, `/api/upcoming/:id` | Recurring bills, subscriptions, income, and 62-day projections |
| POST | `/api/upcoming/estimate` | Estimate recurring amount from transaction history |
| POST | `/api/upcoming/from-transaction` | Create a recurring item from a transaction |
| POST | `/api/upcoming/suggestions/accept`, `/dismiss` | Accept or dismiss detected recurring suggestions |
| GET / POST / PUT / DELETE | `/api/household`, `/api/household/members`, `/api/household/members/:id` | Household summary, member profiles, and linked retirement accounts |
| GET | `/api/household/retirement-history?months=` | Monthly balance history and YTD growth for retirement accounts |
| POST | `/api/household/members/:id/income-records` | Upsert a dated income/benefit snapshot |
| GET / POST / PATCH / DELETE | `/api/household-sharing`, `/shares`, `/users` | Owner-managed partner invites and read/write permissions |
| GET / PUT | `/api/mha`, `/api/mha/settings`, `/api/mha/tax-profile` | MHA summary, settings, and tax profile |
| PUT | `/api/mha/accounts/:id/default`, `/categories/:id/default`, `/categories/:id/ignore-default` | MHA default eligibility and ignore rules |
| POST | `/api/merchant-logos/report`, `/ensure`, `/search`, `/override` | Merchant logo load tracking, Logo.dev brand search, and manual overrides |
| GET / POST | `/api/simplefin/status`, `/setup`, `/sync`, `/disconnect`, `/sync-log` | SimpleFIN connection, manual sync, and sync history |
| GET | `/api/data/budgeting-export` | Export budgeting data |
| GET | `/api/data/orbit-backup` | Download full household JSON backup |
| POST | `/api/data/orbit-restore/preview`, `/api/data/orbit-restore` | Owner-only JSON backup preview and restore |

## File Inventory

### Shared App (`composeApp/src/`)

- `commonMain/kotlin/app/overbay/orbit/`: `App.kt`, `Navigation.kt`, `NavigationBackStack.kt`, `NavigationKeys.kt`, `NavigationStateCoordinator.kt`
- `data/`: `OrbitRepository*.kt`, `SnapshotFlushCoordinator.kt`, `engine/` (`LocalSnapshotEngine`, `SelfHostedApiEngine`, `OrbitStorageEngine`), `model/`, `network/` (`OrbitApi*Client`, `SimpleFinDirectClient`), `sync/` (`CloudSyncManager`, `RemoteDriveClient`)
- `domain/`: `AccountClosureDetector`, `BalanceHistoryEngine`, `BudgetEngine`, `CardTransactionClassifier`, `CreditCardBenefitEngine`, `CreditCardCatalog*`, `CreditCardCycleEngine`, `CreditCardStrategyEngine`, `CreditCardTemplates`, `CsvImportEngine`, `DemoSeedGenerator`, `GoalAllocationEngine`, `HousingCalculatorEngine`, `LocalDateUtils`, `MhaTaxEngine`, `MoneyUtils`, `NetWorthEngine`, `RealEstatePropertyEngine`, `RetirementCalculatorEngine`, `ReviewQueueEngine`, `RuleMatcherEngine`, `SimpleFinWarningParser`, `SpendingTrendsEngine`, `TransferMatcherEngine`, `UpcomingEngine`, `UpcomingScheduleHelper`
- `theme/`: `CardArtPalette`, `Color`, `CustomThemeColors`, `OrbitColorTokens`, `OrbitSprings`, `Theme`, `Type`
- `ui/`: `accounts`, `budgets`, `calculators` (`housing`, `retirement`), `cardperks`, `categories`, `components`, `dashboard`, `goals`, `household`, `login`, `mha`, `networth`, `rules`, `settings`, `transactions`, `trends`, `upcoming`
- `androidMain/`: `AndroidGoogleDriveClient`, `BiometricAuthHelper`, `DevicePosture`, `DisplayRefreshRateHelper`, `FileImportExportHelper`, `FoldableDetector`, `OidcAuthLauncher`, `OrbitHaptics`, `PlatformRuntime`, `PlatformStorage`, `Theme`
- `wasmJsMain/`: `main.kt`, `WasmGoogleDriveClient`, platform `util/` bindings, and `resources/` (`index.html`, `manifest.webmanifest`, `sw.js`, PNG/SVG icons, splash wordmarks)

### Android Wrapper (`androidApp/src/`)

- `main/`: `AndroidManifest.xml`, `MainActivity.kt`, adaptive/legacy launcher icons, splash screen theme, `res/xml/shortcuts.xml`, backup and file-provider configs
- `beta/`: Beta-only icon, string, and shortcut overlays (`app.overbay.orbit.beta`)

### Backend (`backend/src/`)

- Core: `server.js`, `config.js`, `auth.js`, `crypto.js`, `scheduler.js`
- Database: `db/index.js`, `db/migrations.js`, `db/migrations/001` through `038`
- Helpers (`lib/`): `editAssignments`, `effectiveSql`, `http`, `localDate`, `money`, `routeParams`, `upcomingProjection`, `upcomingSchedule`, `upcomingSuggestionConfidence`, `uploads`
- Routes (`routes/`): `accounts`, `auth`, `budgets`, `cardCatalog`, `categories`, `data`, `goals`, `health`, `household`, `householdSharing`, `import`, `merchantLogos`, `mha`, `netWorth`, `notifications`, `preferences`, `rules`, `simplefin`, `spendingTrends`, `transactions`, `upcoming`
- Services (`services/`): `accountBalanceRecordService`, `accountMerge`, `accountRealEstateService`, `budgetOverview`, `creditCardBenefitUsage`, `creditCardCatalog*`, `creditCardProfileService`, `csvImport`, `demoSeed`, `householdDefaults`, `householdSharing`, `institutionLogos`, `merchantLogos`, `mhaSummary`, `mhaTaxEstimate`, `monthlyBudgetOverview`, `notificationPreferences`, `notifications`, `oidc`, `orbitBackupRestore`, `orbitBackupSnapshotBridge`, `retirementHistoryService`, `ruleMatcher`, `sampleHouseholds`, `sessionStore`, `simplefinClient`, `simplefinSync`, `spendingTrends`, `taxData2026`, `transferMatcher`, `upcomingItemValidation`, `upcomingReconciliation`
- Tests (`test/`): Node built-in test runner suites (`node --test`)

## Known Gotchas

- **`better-sqlite3` `.iterate()` + write transaction:** Fails with "database connection is busy". Use `.all()` instead.
- **Node `fetch` and inline URL credentials:** `fetch` rejects URLs containing `user:pass@`. Strip credentials from SimpleFIN URLs and pass an `Authorization: Basic` header.
- **SQLite `ALTER TABLE ADD COLUMN` defaults:** SQLite rejects non-constant defaults like `datetime('now')` in `ALTER TABLE`. Use a constant default and backfill in a follow-up `UPDATE`.
- **Rules do not chain:** Conditions match original imported values only so match counts stay deterministic. If a rule relied on an earlier rule's rename, condition on `original_description` or `original_merchant` instead.
- **Rules with match counts:** `GET /api/rules?withCounts=1` evaluates every rule against all transactions; large datasets (300+ rules x 8K+ transactions) can take 10-20 seconds.
- **Service worker fetch listener:** `sw.js` must keep a `fetch` event listener to satisfy PWA installability checks across Chromium browsers.
- **Docker `wasmJs` build speed:** `Dockerfile` uses a pre-built `composeApp/build/dist/wasmJs/productionExecutable` directory if you run `./gradlew :composeApp:wasmJsBrowserDistribution` (or `scripts\build-frontend.cmd`) on the host first; otherwise Stage 1 downloads the Android SDK and compiles inside Docker, which is significantly slower.
- **Gradle 9 + Skiko wasm unpack:** `composeApp/build.gradle.kts` marks `unpackSkikoWasmRuntime` with `doNotTrackState` for Gradle 9 compatibility.
- **Beta Android builds:** `androidApp/build.gradle.kts` detects a `beta` branch from `.git/HEAD` (or `-PbetaBuild=true`) and automatically switches to `app.overbay.orbit.beta` and `OrbitBeta-debug.apk`.
- **Windows backend test script:** `cmd /c scripts\check-backend.cmd` prefers a local `.tools` Node runtime when present and falls back to `node` on `PATH`.
