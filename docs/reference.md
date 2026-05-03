# Orbit Money — Technical Reference

> This document covers implementation details needed when modifying or debugging the app.

## Stack
- **Frontend:** React 18 (Vite build, client-side routing via React Router v6)
- **Backend:** Node.js 20.19+ + Express
- **Auth:** Local session login, OIDC login, or both; `API_KEY` header for programmatic access
- **Data:** SQLite (better-sqlite3) + Docker named volume
- **Key dependencies:** `express`, `express-session`, `better-sqlite3`, `multer` (CSV upload), `papaparse` (CSV parsing), `@dnd-kit/core` + `@dnd-kit/sortable` (dashboard, account, navigation, and settings reorder UI)
- **Session storage:** local `better-sqlite3` session store in `backend/src/services/sessionStore.js`

## Dockerfile
Multi-stage build:
- **Stage 1 (build):** `node:20-slim` — not Alpine, because Rollup/musl hangs on Docker Desktop + WSL2
- **Stage 2 (prod):** `node:20-alpine` — requires `python3 make g++` for better-sqlite3 native compilation (removed after install)
- Uses `--no-audit --no-fund` flags to prevent npm hangs

## Docker Images
- Public installs use `ghcr.io/waspo98/orbit-money:latest` from `docker-compose.yml`.
- Maintainer beta uses `ghcr.io/waspo98/orbit-money:beta` from `deploy/beta/docker-compose.yml`.
- The `main` and `Beta` GitHub Actions workflows run the self-hosted deploy scripts with `ORBIT_PUBLISH_IMAGE=1`; those scripts publish their image tags, then pull and restart the matching Docker Compose service.
- Release tags like `v0.52.0` publish matching version image tags through `publish-release-image.yml`.
- The Compose files keep `build` definitions as a local source-build fallback, but normal update paths should use `docker compose pull` followed by `docker compose up -d`.

## Data Storage
All data lives in Docker named volume `orbit-money-data` mounted at `/app/data`:

| File | Purpose |
|---|---|
| `budget.db` | SQLite database — accounts, transactions, categories, rules, budgets, sync config, sync log |
| `sessions.db` | Session store (separate `better-sqlite3` connection) |

## Offline PWA Cache

Offline mode is intentionally read-only. The service worker caches the app shell
and static assets, while `frontend/src/api.js` keeps successful authenticated
GET responses in IndexedDB through `frontend/src/offlineCache.js`. Non-GET API
calls are blocked while offline with the read-only message shown in the app
banner.

Cached API responses are keyed by signed-in user identity, household id, and
request path. A successful `/api/auth/me` response is the access validation
source. Owner households can use cached data offline without a time limit;
shared/member households must have validated access within the last 7 days.
Logout clears the IndexedDB financial response cache.

Route components are lazy-loaded, so `App.jsx` warms those route chunks after a
successful online app load. Keep that preloading behavior when adding new
offline-readable pages, otherwise a never-visited page can fail to import while
offline.

`frontend/src/offlineWarmup.js` also warms core read-only API paths shortly
after login, including the default Transactions and Budgets requests. Keep those
paths mechanically aligned with each page's initial `api.get(...)` call; the
offline cache keys by exact request path.

### Database Schema (30 migrations)

| Migration | Purpose |
|---|---|
| `001_initial_schema.sql` | Core tables: accounts, transactions, categories, rules, simplefin_config, budgets, goals. 32 seeded Rocket Money categories. |
| `002_seed_categories.sql` | Additional category seeds |
| `003_account_uniqueness.sql` | Composite unique index on (institution, account_number_last4) |
| `004_simplefin_sync_support.sql` | `sync_log` table, `cutover_date` on `simplefin_config` |
| `005_add_mortgage_type.sql` | Adds `mortgage` to accounts.type CHECK constraint (full table rebuild) |
| `006_account_sort_order.sql` | Adds `sort_order` column to accounts for drag-and-drop reorder |
| `007_transactions_updated_at.sql` | Adds `updated_at` column to transactions (constant default + backfill from `imported_at`) |
| `008_original_edited_split.sql` | Renames `transactions.merchant` → `original_merchant`, adds `edited_merchant`, `edited_category_id`, `edited_is_transfer`, `edited_is_ignored` and matching `_source` columns |
| `009_budget_monthly.sql` | Per-month budgets: rebuilds `budgets` with composite unique `(category_id, month)` |
| `010_budget_global_amount.sql` | Reverted to global per-category budgets: rebuilds `budgets` back to `category_id UNIQUE`, collapses any multi-month rows to the most recent via `ROW_NUMBER() OVER (PARTITION BY category_id ORDER BY month DESC)` |
| `011_account_estimated_value.sql` | Adds `estimated_value` to accounts for mortgage/home-equity net-worth calculations |
| `012_mha_tracker.sql` | Adds MHA app setting, default account eligibility, and transaction-level MHA overrides |
| `013_mha_category_defaults.sql` | Adds category-level MHA default eligibility |
| `014_mha_category_ignore_defaults.sql` | Adds category-level MHA default ignore behavior |
| `015_goal_allocations.sql` | Extends goals metadata and adds multi-account goal allocations |
| `016_merchant_logo_cache.sql` | Adds cached merchant logo metadata for transaction display |
| `017_goal_sort_order.sql` | Adds custom goal ordering |
| `018_account_balance_records.sql` | Adds dated account balance snapshots for manual/disconnected account history |
| `019_household_income.sql` | Adds household member profiles and dated income/benefit history snapshots |
| `020_household_retirement_accounts.sql` | Links household members to existing retirement/HSA accounts for projections |
| `021_integer_cents.sql` | Converts money storage to integer cents and splits goal allocation percent vs fixed-amount storage |
| `022_upcoming_items.sql` | Adds saved Upcoming bills, subscriptions, income items, and dismissed recurring-suggestion keys |
| `023_multi_user_households.sql` | Adds users, households, memberships, OIDC-ready ownership, household-scoped defaults, and migrates existing data to household `1` |
| `024_household_sharing.sql` | Adds pending household shares by email for partner access |
| `025_rename_authentik_sub_to_oidc_sub.sql` | Renames the stored OIDC subject column from Authentik-specific naming to `oidc_sub` |
| `026_neutral_default_household_name.sql` | Renames the old default household label to `My Household` when it has not been customized |
| `027_upcoming_recurrence_projection.sql` | Adds Upcoming recurrence-rule metadata and history-average amount projection settings |
| `028_upcoming_occurrence_reconciliation.sql` | Adds persisted Upcoming occurrence tracking for matched and missed projected transactions |
| `029_permissions_import_batches.sql` | Adds household read/write permissions plus import preview and undo batch tracking |
| `030_user_preferences.sql` | Adds account-scoped UI preferences so customization follows the user across browsers |

### Key Data Model Notes

**Sign convention:** expenses are negative, income is positive (flipped from Rocket Money's positive-expense format during CSV import).

**Money representation:** SQLite stores money fields as integer cents. API responses and request bodies still use dollar values for the frontend. Convert at route/service boundaries with `backend/src/lib/money.js`; do not store floating-point dollars in SQLite. Percent fields remain percent values, and goal allocations store percent allocations in `allocation_percent` while fixed-dollar allocations use integer cents in `allocation_amount`.

**Route template:** Express handlers should validate and normalize route inputs at the boundary, use `backend/src/lib/routeParams.js` for common id/boolean/bounded-integer parsing, and send responses through `backend/src/lib/http.js` helpers (`sendOk`, `sendBadRequest`, `sendNotFound`, `sendServerError`, etc.) so API success and error shapes stay mechanical.

**Transaction sources:** `csv_import`, `simplefin`, `manual` — tracked in `source` column, deduplicated via `external_id`.

**Original / edited provenance (migration 008):** Rules and manual edits never mutate the raw imported values. Each editable field has an `original_*` column (populated at import, never touched afterward) and a nullable `edited_*` column with an `edited_*_source` tag (`'user'`, `'rule:{id}'`, `'system:*'`, or NULL). Display values are computed as `COALESCE(edited_X, original_X)` at the API layer so the UI sees one logical merchant/category/flag regardless of how it got there. User edits are sticky — rules never override `source='user'`. System edits are reserved for app-owned derivations like transfer matching and are preserved during rule reapply.

**Multi-user ownership:** Tenant-owned data carries `household_id`. Existing single-user data is assigned to household `1` during migration. Fresh installs end with the neutral default name `My Household`; existing installs are only renamed if the household still has the old default label. Uniqueness that used to be global, such as category name, SimpleFIN account id, transaction external id, and app settings, is now scoped by household. New OIDC users get a household seeded from `app_default_settings` and `app_default_categories`.

**Partner sharing:** Owners create pending email invites. OIDC login claims only unaccepted pending invites, creates the household membership once, and retires the invite; future access changes happen only on `household_memberships`.

**Backup restore:** Orbit backup restore is owner-only. It replaces the active household's data, clears existing pending shares/memberships and SimpleFIN sync history for that household, then restores matching memberships from the backup while keeping the current user as owner. Restores allocate fresh row ids in the target household and remap dependent references across accounts, categories, rules, transactions, goals, income members, upcoming items, import batch history, and user preferences. SimpleFIN connection information is intentionally excluded and must be reconnected after restore.

**Auth modes:** `AUTH_PROVIDER=local` shows only the username/password form. `AUTH_PROVIDER=oidc` shows only the OIDC button. `AUTH_PROVIDER=both` shows both options. `OIDC_LOGIN_LABEL` controls the OIDC button text and defaults to `Log in with OIDC`. `ENABLE_SAMPLE_DATA=1` exposes the sample-data login flow; it is disabled by default for normal installs.

**Public-safe env defaults:** `API_KEY` is optional and should remain blank unless programmatic access is intentionally enabled. `SIMPLEFIN_ENCRYPTION_KEY` may remain blank until the user connects SimpleFIN. Startup rejects known placeholder values for required secrets so copied example files do not become predictable public deployments.

**Rules:** JSON-serialized `conditions` (array of `{field, operator, value}`) and `actions` (array of `{type, value}`). Condition fields: `merchant`, `original_description`, `amount`, `account_id`, `category_id`. All condition evaluation runs against originals only, making match counts stable. Merchant text conditions check both `original_merchant` and `original_description` so SimpleFIN payee cleanup does not break rules when the raw description still contains the fuller merchant name. Action types: `rename`, `categorize`, `mark_transfer`, `mark_ignored`. First rule (priority DESC, id ASC) to claim a given field wins; lower-priority rules skip it.

**Account types:** `checking`, `savings`, `credit`, `investment`, `loan`, `mortgage`, `cash`, `other`.

**Account balance records:** `account_balance_records` stores dated balance snapshots for accounts that need manual history outside SimpleFIN. Adding a record upserts by `(account_id, record_date)` and updates `accounts.current_balance` when that record is the newest snapshot for the account. Net Worth history uses the latest snapshot on or before each month when one exists, then falls back to transaction-derived balances.

**Overlap strategy (SimpleFIN + Rocket Money):** cutover date approach — RM owns transactions before cutover, SimpleFIN owns after. Pre-cutover RM transactions are deleted during sync if SimpleFIN provides the same period.

**Budgets:** One row per spending `category_id` (globally applied). Transfer and income categories are excluded from budget rows and per-category spending lists; income is summarized separately in the monthly Income / Expenses / Net stat row. The `budgets` table retains the `rollover` column from migration 001 for future Phase 2 work but it's not consumed by the current UI.

**Goals:** Goal progress is computed from `goal_account_allocations` and active asset account balances. Allocations can be fixed dollar amounts or percentages of the account balance; fixed allocations are stored as integer cents and percentage allocations remain percent values. The old `goals.current_amount` column is retained for compatibility, but the Goals API derives live progress at read time.

**Household:** `household_members` stores the current profile, income, retirement, and benefit assumptions for each person. `household_income_records` stores dated snapshots so future projections can use compensation history without mutating old records. `household_retirement_accounts` links existing account rows to household members so retirement projections can compound present balances without moving or duplicating account data.

## Features

### Core Transactions
- Rocket Money CSV import: auto-creates accounts, sign-flips amounts, and generates rename rules from Custom Name columns. Existing account matching only reuses an account when both institution and last4 are present; missing identifiers create a fresh account to avoid merging unrelated cash/manual accounts.
- SimpleFIN bank sync with AES-256-GCM encrypted access URL storage
- Tap-to-expand transaction cards — grid-template-rows animated expand/contract (220ms), auto-scroll-into-view on expand (accounts for bottom tabs height)
- Inline edit modal: merchant, category, notes (date/amount read-only — bank ground truth)
- Transaction deletion and transfer/ignored toggles
- "Mark as recurring" is available from the transaction row menu and expanded transaction card; it opens the recurring-item editor seeded from that transaction before saving to Upcoming.
- Edit provenance UI: "edited manually" / "applied by rule" badges, per-field "reset to original" buttons, inline display of the original value alongside the edited one

### Search, Filter, and Sort
Transactions page toolbar has a debounced search input + filter sheet + sort dropdown. URL query params are the source of truth — back-button and link-sharing work. Active filters render as dismissible pills below the toolbar. Supported filters:

- **q** — free-text search across display merchant, original merchant, original description, and notes
- **accounts** — multi-select account IDs
- **categories** — multi-select category IDs (supports `uncategorized` literal for NULL matches)
- **date_from** / **date_to** — YYYY-MM-DD inclusive, with chip presets (This month, Last month, Last 30 / 90, YTD, All time)
- **amount_min** / **amount_max** — absolute-value match so `50` catches both ±$50
- **type** — all / income / expense / transfer
- **include_ignored** — default true; can hide
- **exclude_credit_card_payments** — `1` hides credit-card-payment style rows for dashboard/report views
- **has_edits** — any / yes / no
- **sort** — `date_desc | date_asc | amount_desc | amount_asc | abs_amount_desc | abs_amount_asc | merchant_asc`

All comparisons use COALESCE(edited, original) so filtering matches what's on screen.

### Rules Management
- Full CRUD: create, read, update, delete individual rules
- Bulk wipe with typed "DELETE" confirmation
- Condition builder: field + operator + value rows, AND'd together
- Action builder: rename, categorize, mark transfer, mark ignored
- Live preview: debounced (400ms) match count against existing transactions before saving
- Enable/disable toggle per rule with optimistic UI
- Match count displayed per rule in the list view — stable because counting is condition-only (no actions involved)
- "Create rule from transaction" flow: pre-fills condition with `merchant contains [original merchant]`, action with `rename to [current merchant]`. Synchronous reapply on save so the Transactions page reflects the change immediately on silent refresh
- Rule deletion reverts every edit whose `source = 'rule:{id}'`, then re-runs remaining rules to re-populate any fields they should claim
- Auto-apply: creating, updating, or toggling a rule triggers a synchronous reapply — by the time the response returns, all transactions reflect settled state

### SimpleFIN Sync
- Setup via base64 token exchange (SimpleFIN Bridge API)
- Access URL encrypted at rest (AES-256-GCM, key from `.env`)
- Auto-match accounts by (institution, last4)
- Transfer auto-matcher: pairs opposite-sign same-day transactions in displayed transfer-flagged categories. It respects edited category/ignored state and marks transfer status as a resettable `system:transfer_matcher` edit rather than mutating originals.
- Scheduler: daily at 6 AM (configurable) + manual "Sync Now"
- Sync log with status tracking (success / error / partial)
- Partial sync detection: if SimpleFIN reports account connection issues, status shows as yellow "Partial sync" rather than red "Error"
- Node `fetch` rejects inline URL credentials — implementation strips them and sends as Basic Auth header

- Transactions with missing/zero SimpleFIN `posted` timestamps are skipped instead of being stored as `1970-01-01`; if a later sync returns a valid date for a previously affected transaction ID, the sync repairs that row's raw date/details.

### MHA Tracker
- Eligible transaction totals exclude transactions whose displayed state is ignored or transfer. Eligibility can still come from account defaults, category defaults, or transaction overrides, but ignored/transfer rows are filtered out before summary totals and savings are computed.

### Accounts
- Full CRUD + merge (reassigns all transactions to target, deletes source) + archive/unarchive
- **Reorder mode:** dedicated toggle that replaces normal grouped account cards with drag handles; account rows can be reordered within their account-type group, and group headers can be dragged to reorder whole groups. In-mode there are no competing tap targets, activation distance is 0 (any movement), and tapping "Done" exits. Outside reorder mode, no `@dnd-kit` listeners are mounted at all — eliminating the press-and-hold activation problems that dogged earlier versions.
- Sort order persisted to `sort_order` column
- "See transactions" button filters the Transactions page via `?accounts=X` query param
- Three-dot dropdown menu per account (Add Record, Edit, Merge, Archive, Delete)
- Add Record modal stores a dated account total snapshot with an app-dialog confirmation that summarizes the percentage change from the previous balance.
- Edit modal: name, type, institution, plus estimated value for mortgage accounts. Balance and last-4 are intentionally not editable in the UI.

### Budgets
- **Monthly caps, global per category.** One amount per category that applies to every month. Editing `Groceries` updates the cap for every past and future month.
- **Month navigation** via `‹ / ›` arrows plus the shared `AppSelect` pill. The select menu is portaled and page-centered on mobile so month jumps use the same dropdown primitive as the rest of the app.
- **Snapshot card:** total monthly expenses, percentage of budgeted amount, remaining/over, unbudgeted spending, cash flow, and spend pace. The headline and progress math intentionally include unbudgeted expenses.
- **Spending mix:** expandable category-share rows with transaction previews. Rows use the shared `ExpandingSection` animation; the optional collapse chevron primitive is deliberately hidden on this page.
- **Tracking sections:** notable budget activity, unbudgeted spending quick-add CTAs, and budgeted categories with inactive categories collapsed at the bottom of the card. True uncategorized/null-category spending appears in the unbudgeted breakdown and links to `categories=uncategorized`, but it cannot be quick-added as a budget until it has a real category.
- **Category sort preference:** the Budgeted Categories sort menu persists through the existing `budgetedSort` user preference key.
- Progress bars transition green → yellow (≥85%) → red (>100%)
- Endpoints: GET `/api/budgets?month=YYYY-MM`, GET `/api/budgets/months`, PUT `/api/budgets` (upsert), DELETE `/api/budgets/:id`

### Goals
- Create and edit saving targets from the More menu with a three-step wizard: purpose, accounts, allocation
- Supported goal kinds: college, car, home, emergency, travel, custom
- Allocations can connect multiple active asset accounts to a goal: checking, savings, cash, investment, or other
- Each account allocation can apply either a percent of the account balance or a fixed dollar amount
- Wizard shows each account's existing goal allocations and can steal allocation from other goals when explicitly enabled
- Goal charts derive monthly history from existing transaction deltas and current account balances; ETA uses recent monthly progress
- Account allocation meters are split into consistent per-goal color chunks so the same goal is visually traceable across accounts.
- "Imagine" slider projects a hypothetical ETA with extra monthly savings
- Endpoints: GET `/api/goals?months=`, POST `/api/goals`, PUT `/api/goals/:id`, DELETE `/api/goals/:id`

### Retirement Calculator
- Standalone More-menu page at `/retirement-calculator`.
- Uses Household retirement inputs and linked retirement/HSA accounts as the automatic current-balance and contribution source.
- Question modes answer "What Will We Have?", "When Can We Retire?", and "How Much To Save?" from the same projection model.
- Assumption controls cover market return, inflation, and withdrawal rate presets; bridge checks separate HSA assets from non-HSA retirement balances before age 65.
- Preferred retirement age and default growth estimator (Conservative, Balanced, Aggressive) persist as account preferences and feed the Dashboard retirement snapshot.

### Household
- Create and edit household members from the More menu after Net Worth
- Tracks age source date, employment status, employer/title, gross income, pay cadence, annualized net pay, retirement account type, employee contribution, employer match assumptions, and benefit values
- Links active investment accounts to household members as 401(k), 403(b), Roth IRA, HSA, pension, or other retirement assets so current balances feed the retirement calculator
- Paycheck deduction fields are entered per paycheck in the UI and annualized from the selected pay frequency for projection math
- Saving a profile writes a dated income snapshot for future historical income reporting
- Summary cards show annualized take-home pay, gross income, employer retirement match, benefits value, and earners
- Endpoints: GET `/api/household`, POST `/api/household/members`, PUT `/api/household/members/:id`, POST `/api/household/members/:id/income-records`, DELETE `/api/household/members/:id`

### Upcoming
- Standalone More-menu page at `/upcoming` for saved bills, subscriptions, and income.
- Supports manual entries with type, amount, amount mode, next date, category, account, notes, and frequency: weekly, biweekly, twice monthly, monthly, bimonthly, yearly, or custom every X days/weeks/months.
- Month-based schedules can use exact month days or weekday patterns such as first and third Friday.
- Amount mode can stay fixed or estimate from matching transaction history over a selectable lookback window.
- `/api/upcoming` expands saved items into projected occurrences for the rest of the current month and the next 62 days, then returns rest-of-month cash-flow totals.
- The backend also stores hidden occurrence reconciliation rows. A scheduler and SimpleFIN sync pass generate expected occurrences, match them to synced transactions, and mark unmatched occurrences missed after a 4-day grace window.
- Transaction rows can seed a recurring item with "Mark as recurring"; the shared editor opens before anything is created.
- Suggestions are generated from recent transaction history, emphasizing income and Bills & Utilities style categories before other merchants. Suggestions can be reviewed in the recurring editor, accepted as a bill, subscription, or income, or dismissed.
- Dashboard Subscriptions and Upcoming cards read from `/api/upcoming` rather than frontend-only transaction heuristics.
- Monthly summary values normalize different frequencies to monthly equivalents.
- Endpoints: GET `/api/upcoming`, POST `/api/upcoming`, PUT `/api/upcoming/:id`, DELETE `/api/upcoming/:id`, POST `/api/upcoming/from-transaction`, POST `/api/upcoming/suggestions/accept`, POST `/api/upcoming/suggestions/dismiss`

### Dashboard
Customizable multi-card overview page at `/dashboard`. Stacked on narrow phones, 2-column grid on foldable/tablet widths (≥640px), with Recent Activity spanning full width. The `Customize My Dashboard` button opens one modal list where every dashboard card has a visibility toggle and drag handle. Layout, dashboard-only hidden biggest transactions, goal focus, retirement snapshot preferences, and review state persist through `/api/preferences` so they follow the signed-in account across browsers.

- **Accounts card:** net worth (active balances plus mortgage estimated-value equity), breakdown by group (Cash = checking+savings+cash, Investments, Credit cards, Loans, Real Estate, Other). Rows with zero balance are hidden.
- **This month card:** Day X of Y + compact Income / Expenses / Net trio
- **Budget pulse card:** overall progress bar + daily spend pace + up to 5 "attention" categories (over-budget first, then 85%+), or a success message when all are on track
- **Top spending card:** up to 7 categories with horizontal bars scaled to the biggest spender; bars use each category's color
- **Biggest Monthly Transactions card:** top 5 current-month expenses, excluding income, ignored rows, transfers, and credit-card-payment style rows. `Hide From Dashboard` is dashboard-only and does not set the transaction ignored flag used by budgets/reports.
- **Subscriptions / Recurring and Upcoming cards:** read saved bills, subscriptions, and income from `/api/upcoming`. The Upcoming page owns manual entries and accepted suggestions.
- **Categorize Recent Transactions card:** opens a swipe-style review modal that asks whether each category is correct, batches reviews in groups of 10, and lets the user change the category, create a rule, or skip the transaction. Candidates come from recent uncategorized transactions, noisy imported merchant text, and category pattern changes. Reviewed/skipped card IDs are stored as account preferences; category changes still use the normal transaction edit API.
- **Uncategorized, Month vs Last Month, Goals Progress, Goal Focus, Retirement Snapshot, MHA Tracker Summary, Mortgage Snapshot:** reuse existing route data from Transactions, Budgets, Goals, Household, MHA, and Accounts.
- **Recent activity card:** last 10 transactions with the shared `TransactionRow` actions
- Shimmer skeleton loading per card
- Empty state routes new users to Settings, where Rocket Money import and SimpleFIN setup live

### Responsive Layout
- **Phone (< 640px):** Single column, bottom tabs
- **Tablet/Foldable (640–1079px):** Centered content with foldable-aware 2-column card grids on Dashboard, Budgets, Accounts, Category Manager, Goals, Net Worth, Household, and MHA Tracker where the page content benefits from it; bottom tabs remain active
- **Desktop (≥ 1080px):** Left sidebar (260px) + content area, bottom tabs hidden

### Navigation
- **Bottom tabs (5):** Dashboard, Transactions, Budgets, Accounts, More. These four primary routes are locked to their original positions.
- **Desktop sidebar:** Lists every visible page directly. Primary routes remain first, and More-menu pages follow the user-controlled More order. Bottom tabs are hidden on desktop.
- **More tab:** Opens bottom sheet (mobile) with cards for Rules, Category Manager, Savings Goals, Upcoming, Retirement Calculator, Housing Calculator, Net Worth, Household, MHA Tracker when enabled, and Settings. Settings can hide optional frontend sections from navigation and reorder More-menu cards through account preferences; locked pages stay visible.
- **Removed hamburger menu:** do not reintroduce a separate hamburger drawer; use Bottom Tabs, Desktop Sidebar, and More Sheet.
- **Settings page:** Collapsible/reorderable cards for Appearance, Turn App Features On/Off, SimpleFIN, Rules maintenance, Import/Export, Backup/Restore, Account, and Orbit Money build details. The Data Management section includes a Reapply Rules action that reruns every enabled rule against every transaction while preserving manual edits. The feature toggle card owns frontend-only navigation visibility, More-card order, and the existing MHA visibility control.
- **React Router v6:** Client-side routing with browser back/forward support. Page modules are lazy-loaded through `React.lazy`/`Suspense` so the authenticated app shell stays small. All routes are served via the Express catch-all for deep-link support.

### UI Details
- `AnimatedModal` component: render-prop pattern (`{({ close }) => ...}`), 180ms slide/zoom animations via `.closing` CSS classes. Saved closes animate; canceled closes should feel immediate unless a specific flow says otherwise.
- `PageHero` component and `useMorphingPageHero(initialHeight)` hook own the morphing sticky hero measurement logic. Reuse `PageHero` for page headers; pass `chrome` and `toolbar` slots when a page needs custom header controls.
- `AppDialog.jsx` exposes `useAppDialog()` for modal alert/confirmation flows. Prefer it over native `alert()` / `confirm()` so mobile UX and destructive-action styling stay consistent.
- `AppSelect.jsx` is the shared custom select primitive. Pass an `options` array (`{ value, label }`) and do not nest native `<option>` children; use `menuPlacement="page-center"` for compact month/year picker pills.
- `ExpandingSection.jsx` owns expand/collapse animation. Pair it with `CollapseIndicator` only when the local pattern wants a visible affordance; `CollapseIndicator` supports `visible={false}` so pages can keep shared structure without showing a chevron.
- `frontend/src/navigation.js` owns route metadata. `BottomTabs.jsx`, `DesktopSidebar.jsx`, `MoreSheet.jsx`, `App.jsx`, and route visibility should read from it so labels, icons, route rendering, and feature gating stay aligned.
- `SelectableListItem.jsx` is the shared two-line selectable card/row primitive. Use it for lists where one item is selected, such as goal/category pickers; selected rows use the shared green active treatment.
- `CurrencyInput.jsx` is the shared primitive for editable dollar amounts. Use it for money text fields so values format with `$` and comma grouping while typing; pair saved values with `parseCurrencyInput`.
- `SearchField.jsx` is the shared rounded search primitive for page toolbars and collapsed page-hero search slots.
- `PercentInput.jsx` is the shared editable percent primitive; pair ad hoc percent formatting with `formatPercentInput` from `frontend/src/lib/formatters.js`.
- Overlay behavior: modals, app dialogs, sheets, and full-screen popovers blur the app backdrop and lock body scroll. `DropdownMenu` stays anchored to its trigger, does not blur the page, and does not lock scroll.
- Three-way theme toggle (☀️ / 💻 / 🌙): localStorage persistence with pre-paint script in `index.html` to avoid flash
- Night Style (`Soft Dark` / `AMOLED Black`) also persists in `localStorage`; the selected night style applies to explicit Night mode and to System mode when the device prefers dark.
- 40+ CSS custom properties for light/dark themes, emerald-600/500 accent
- Inter Tight (Google Fonts) for body/UI text and bundled DM Sans for titles/brand headings
- Bottom tabs: 80px height (Material Design baseline), 5 columns
- Dismissable sync-error banner keyed by error message text in localStorage
- Mobile zoom suppression: `text-size-adjust: 100%` on `html` + 16px minimum font-size on editable form controls ≤767px (prevents both Android Chrome text boosting and iOS Safari focus zoom)

### PWA
- `manifest.webmanifest` with PNG icons (regular + maskable)
- Service worker (`sw.js`): pass-through fetch, no offline caching (satisfies install criteria only)
- Installable on Android Chrome, desktop Chrome/Edge
- `theme-color` meta tags for light and dark schemes

## API Endpoints

### Auth
| Method | Path | Description |
|---|---|---|
| GET | `/api/auth/config` | Active auth provider, enabled login methods, OIDC login URL/button label, and sample-data availability |
| POST | `/api/auth/login` | Local session login |
| POST | `/api/auth/sample` | Create/login to a temporary sample household when `ENABLE_SAMPLE_DATA=1` |
| GET | `/api/auth/oidc/login` | Start OIDC login |
| GET | `/api/auth/oidc/callback` | OIDC callback |
| POST | `/api/auth/logout` | Session logout |
| GET | `/api/auth/me` | Check auth status |

### Transactions
| Method | Path | Description |
|---|---|---|
| GET | `/api/transactions?...` | Paginated list with full filter/search/sort. Returns display values (COALESCE'd) + originals + edit metadata + `has_edits` flag, plus `grandTotal` for "X of Y shown" UI and `monthlyTotal` for the current-month transaction count |
| GET | `/api/transactions/:id` | Single transaction detail |
| PATCH | `/api/transactions/:id` | Partial update (merchant, category_id, notes, is_transfer, is_ignored). Writes to `edited_*` with source='user'. Setting a value equal to the original clears the edit and re-runs rules for that one row. |
| POST | `/api/transactions/:id/reset` | Body `{ fields: [...] }`. Clears edits on listed fields and reapplies rules |
| DELETE | `/api/transactions/:id` | Hard delete |

### Accounts
| Method | Path | Description |
|---|---|---|
| GET | `/api/accounts?includeArchived=` | List, ordered by sort_order |
| PUT | `/api/accounts/:id` | Update (name, type, institution) |
| POST | `/api/accounts/:id/archive` | Archive |
| POST | `/api/accounts/:id/unarchive` | Unarchive |
| POST | `/api/accounts/:id/records` | Add or update a dated manual balance snapshot |
| POST | `/api/accounts/:id/merge` | Merge into target account |
| DELETE | `/api/accounts/:id` | Delete (only if 0 transactions) |
| POST | `/api/accounts/reorder` | Save drag-and-drop order |

### Rules
| Method | Path | Description |
|---|---|---|
| GET | `/api/rules?withCounts=1` | List all rules, optionally with match counts (condition-only counting) |
| POST | `/api/rules` | Create rule. Synchronous full reapply before response returns. |
| PUT | `/api/rules/:id` | Update rule. Synchronous full reapply. |
| PATCH | `/api/rules/:id/enabled` | Toggle enabled. Synchronous full reapply. |
| DELETE | `/api/rules/:id` | Delete single rule. Synchronously clears all `edited_*` with `source = 'rule:{id}'`, then reapplies remaining rules. |
| DELETE | `/api/rules/all` | Wipe all rules. Clears all `rule:*` sourced edits. |
| POST | `/api/rules/preview` | Preview match count for unsaved conditions |
| POST | `/api/rules/reapply-all` | Manual full reapply |
| GET | `/api/rules/:id/match-count` | Match count for one rule |

### Categories
| Method | Path | Description |
|---|---|---|
| GET | `/api/categories` | List all categories for the household |
| POST | `/api/categories` | Create a category |
| PUT | `/api/categories/:id` | Update a category |
| DELETE | `/api/categories/:id` | Delete a category when it is safe to remove |

### Budgets
| Method | Path | Description |
|---|---|---|
| GET | `/api/budgets?month=YYYY-MM` | Monthly overview: `budgeted` / `unbudgeted` / `inactive` arrays + summary with `total_budgeted`, `total_spent_in_budgets`, `total_spent_unbudgeted`, `total_spent`, `total_income`, `total_expenses`, `total_net`. Month defaults to current month. Null-category expense rows are returned as an unbudgeted `Uncategorized` item and summary money is summed in integer cents before serialization. |
| GET | `/api/budgets/months` | Distinct months with transaction activity (for month picker) |
| PUT | `/api/budgets` | Upsert `{ category_id, amount, rollover? }` — global per-category |
| DELETE | `/api/budgets/:id` | Delete budget |

### Net Worth
| Method | Path | Description |
|---|---|---|
| GET | `/api/net-worth?months=` | Current summary, monthly history, and account breakdown. `months` accepts numeric ranges up to 240 or `all`. Mortgage accounts contribute estimated value minus debt balance. |

### Goals
| Method | Path | Description |
|---|---|---|
| GET | `/api/goals?months=` | Goals, account allocation availability, history, progress, and ETA projections. |
| POST | `/api/goals` | Create a goal with `{ name, target_amount, target_date?, kind?, allocations, stealFromOthers? }`. |
| PUT | `/api/goals/reorder` | Persist custom goal order |
| PUT | `/api/goals/:id` | Replace goal metadata and allocations. Can rebalance other goals when stealing is enabled. |
| DELETE | `/api/goals/:id` | Delete a goal and its allocation rows. |

### Upcoming
| Method | Path | Description |
|---|---|---|
| GET | `/api/upcoming` | Active saved items, projected occurrences, rest-of-month cash flow, recurring suggestions, and monthly-equivalent summary totals |
| POST | `/api/upcoming` | Create a manual item |
| PUT | `/api/upcoming/:id` | Replace an upcoming item |
| DELETE | `/api/upcoming/:id` | Delete an upcoming item |
| POST | `/api/upcoming/from-transaction` | Legacy endpoint that creates a monthly recurring item from `{ transaction_id }`; current UI uses the shared editor before POST `/api/upcoming` |
| POST | `/api/upcoming/suggestions/accept` | Accept a suggestion and create an item |
| POST | `/api/upcoming/suggestions/dismiss` | Dismiss a suggestion key |

### Household
| Method | Path | Description |
|---|---|---|
| GET | `/api/household` | Household summary, member profiles, linked retirement accounts, and recent income history snapshots. |
| POST | `/api/household/members` | Create a household member, save linked retirement accounts, and write the first dated income snapshot. |
| PUT | `/api/household/members/:id` | Update a household member, replace linked retirement accounts, and upsert a dated income snapshot. |
| POST | `/api/household/members/:id/income-records` | Upsert a manual income/benefit snapshot for a member. |
| DELETE | `/api/household/members/:id` | Delete a member and their income history. |

### Household Sharing
Partner sharing is owner-managed. Pending invites are one-time OIDC login claims:
when the invited user signs in, the app creates the membership and retires the
pending share. After that, `household_memberships` is the source of truth for
role and read/write access.

| Method | Path | Description |
|---|---|---|
| GET | `/api/household-sharing` | Current household users and pending shares |
| POST | `/api/household-sharing/shares` | Owner-only: invite or update a pending household share by email |
| PATCH | `/api/household-sharing/shares/:id/access` | Owner-only: change a pending share's read/write access |
| PATCH | `/api/household-sharing/users/:id/access` | Owner-only: change a non-owner member's read/write access |
| DELETE | `/api/household-sharing/shares/:id` | Owner-only: revoke a pending share |
| DELETE | `/api/household-sharing/users/:id` | Owner-only: remove a household user |

### Merchant Logos
| Method | Path | Description |
|---|---|---|
| POST | `/api/merchant-logos/report` | Record whether a merchant logo loaded or failed |
| POST | `/api/merchant-logos/ensure` | Ensure a transaction has a cache row for manual override |
| POST | `/api/merchant-logos/search` | Search Logo.dev brand candidates when a secret key is configured |
| POST | `/api/merchant-logos/override` | Save a manual logo URL or category-icon override |

### SimpleFIN
| Method | Path | Description |
|---|---|---|
| GET | `/api/simplefin/status` | Connection status, last sync info |
| POST | `/api/simplefin/setup` | Connect with setup token + cutover date |
| POST | `/api/simplefin/sync` | Manual sync now |
| POST | `/api/simplefin/disconnect` | Disconnect (keeps data) |
| GET | `/api/simplefin/sync-log?limit=` | Recent sync history |

### Import
| Method | Path | Description |
|---|---|---|
| POST | `/api/import/rocket-money` | CSV upload (multipart/form-data). Inserts raw `original_merchant`, reapplies rules post-insert so Rocket Money Custom Names land as rule-owned `edited_merchant` values. |

### Health
| Method | Path | Description |
|---|---|---|
| GET | `/api/health` | Health check |

## File Inventory

### Backend (`backend/src/`)
- `server.js`, `config.js`, `auth.js`, `crypto.js`, `scheduler.js`
- `db/index.js`, `db/migrations.js`
- `lib/`: http, localDate, money, routeParams, upcomingProjection, upcomingSchedule
- `db/migrations/001` through `028`
- `routes/`: accounts, auth, budgets, categories, goals, health, household, householdSharing, import, merchantLogos, mha, netWorth, rules, simplefin, transactions, upcoming
- `services/`: csvImport, demoSeed, householdDefaults, merchantLogos, mhaSummary, oidc, ruleMatcher, sampleHouseholds, sessionStore, simplefinClient, simplefinSync, transferMatcher, upcomingReconciliation
- `test/`: Node built-in test runner coverage for backend helpers and calculation services

### Frontend (`frontend/`)
- `index.html` (PWA manifest link, pre-paint theme script, SW registration)
- `public/`: manifest.webmanifest, PNG/SVG icons, splash wordmarks, sw.js
- `src/main.jsx`, `src/App.jsx`, `src/Login.jsx`, `src/api.js`, `src/index.css`
- `src/hooks/useTheme.js`
- `src/components/`: AnimatedModal, AppDialog, AppIcon, AppRangeSlider, AppSelect, BottomTabs, BrandLogo, CollapseIndicator, CurrencyInput, DesktopSidebar, DropdownMenu, FilterSheet, InlinePopover, MoreDotsIcon, MoreSheet, PageHero, PercentInput, ReorderListItem, SearchField, SelectableListItem, SyncErrorBanner, rule editor primitives, transaction row primitives
- `src/pages/`: Accounts, Budgets, Categories, Dashboard, Goals, Household, HousingCalculator, MhaTracker, NetWorth, RetirementCalculator, Rules, Settings, Transactions, Upcoming

## Known Gotchas
- **better-sqlite3 `.iterate()` + write transaction** = "database connection is busy" — always use `.all()` instead
- **Node `fetch` rejects inline URL credentials** — strip from URL and send as Basic Auth header
- **SQLite integer 0 renders as "0" in JSX** with `&&` pattern — must coerce with `!!` (e.g., `{!!account.is_archived && <Badge />}`)
- **SQLite `ALTER TABLE ADD COLUMN`** cannot use function defaults like `datetime('now')` — must use constant default then backfill
- **`touch-action: manipulation`** on draggable elements blocks `@dnd-kit` touch events — reorder mode removes competing touch targets entirely rather than fighting activation heuristics
- **NavLink renders `<a>` elements** — need explicit `text-decoration: none; outline: none;` on all states to prevent flash-underline on tap
- **SW needs a fetch listener** (even pass-through) to satisfy PWA install criteria in some browsers
- **Rules with match counts** loads slowly with many rules — `withCounts=1` scans all transactions per rule. For 300+ rules × 8K+ transactions, expect 10–20 second initial load.
- **Windows build tooling:** use Node.js 20.19+ on PATH or the private `.tools` runtime. `cmd /c scripts\build-frontend.cmd` runs the frontend production build from the repo root, and `cmd /c scripts\check-backend.cmd` runs the backend syntax smoke check and tests. Both helpers prefer `.tools` when it exists locally, then fall back to system Node/npm.
- **Repeated page hero UI:** before adding or changing a page header, check `PageHero.jsx` first. Shared morph behavior belongs in `useMorphingPageHero`; page-specific stat/chrome content belongs in the page.
- **Native browser dialogs:** use `useAppDialog()` instead of `alert()` / `confirm()` so confirmations animate and share the app's button styling.
- **Button copy:** visible button labels should use Title Case for words, e.g. `+ New Category`, `Add Budget`, `Save`.
- **Selectable rows:** use `SelectableListItem` before creating a new selectable card/list row. Keep selected states green and compact two-line rows unless there is a strong page-specific reason.
- **Dropdown menus:** do not lock body scroll or blur the page. Keep them positionally anchored to the trigger.
- **Hidden date/month inputs can trigger mobile-browser layout quirks** — use `AppSelect` for compact month/year picker pills instead of hidden input/select overlays.
- **Rule chaining no longer works** — since conditions match against originals only, a rule can't match on a value a higher-priority rule just renamed to. This was an intentional v12 change (match counts were broken because of the old chaining semantics). If a hand-built rule relied on chained renames, rewrite it to condition on `original_description` or the unmodified upstream value.

## Planned
- **Phase 2 Budgets:** rollover (column already exists), income targets with proper direction, category groupings, spending pace
- **Phase 2 Dashboard:** spending-over-time and net-worth charts
- **Bulk merge suggestions:** Auto-pair SimpleFIN duplicates with RM accounts by institution + last-4
- **Credit score:** possible future manual tracker or integration
