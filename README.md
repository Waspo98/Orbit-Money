# Budget Tracker — Technical Reference

> **Operational summary lives in `Server_info.md`.** This document covers implementation details needed when modifying or debugging the app.

## Stack
- **Frontend:** React 18 (Vite build, client-side routing via React Router v6)
- **Backend:** Node.js 20 + Express
- **Auth:** Session-based login (credentials in `.env`); `API_KEY` header for programmatic access
- **Data:** SQLite (better-sqlite3) + Docker named volume
- **Key dependencies:** `express`, `express-session`, `better-sqlite3`, `multer` (CSV upload), `papaparse` (CSV parsing), `@dnd-kit/core` + `@dnd-kit/sortable` (accounts reorder)

## Dockerfile
Multi-stage build:
- **Stage 1 (build):** `node:20-slim` — not Alpine, because Rollup/musl hangs on Docker Desktop + WSL2
- **Stage 2 (prod):** `node:20-alpine` — requires `python3 make g++` for better-sqlite3 native compilation (removed after install)
- Uses `--no-audit --no-fund` flags to prevent npm hangs

## Data Storage
All data lives in Docker named volume `budget-data` mounted at `/app/data`:

| File | Purpose |
|---|---|
| `budget.db` | SQLite database — accounts, transactions, categories, rules, budgets, sync config, sync log |
| `sessions.db` | Session store (separate connection, managed by `connect-sqlite3`) |

### Database Schema (10 migrations)

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

### Key Data Model Notes

**Sign convention:** expenses are negative, income is positive (flipped from Rocket Money's positive-expense format during CSV import).

**Transaction sources:** `csv_import`, `simplefin`, `manual` — tracked in `source` column, deduplicated via `external_id`.

**Original / edited provenance (migration 008):** Rules and manual edits never mutate the raw imported values. Each editable field has an `original_*` column (populated at import, never touched afterward) and a nullable `edited_*` column with an `edited_*_source` tag (`'user'`, `'rule:{id}'`, or NULL). Display values are computed as `COALESCE(edited_X, original_X)` at the API layer so the UI sees one logical merchant/category/flag regardless of how it got there. User edits are sticky — rules never override `source='user'`.

**Rules:** JSON-serialized `conditions` (array of `{field, operator, value}`) and `actions` (array of `{type, value}`). Condition fields: `merchant`, `original_description`, `amount`, `account_id`, `category_id`. All condition evaluation runs against originals only, making match counts stable. Action types: `rename`, `categorize`, `mark_transfer`, `mark_ignored`. First rule (priority DESC, id ASC) to claim a given field wins; lower-priority rules skip it.

**Account types:** `checking`, `savings`, `credit`, `investment`, `loan`, `mortgage`, `cash`, `other`.

**Overlap strategy (SimpleFIN + Rocket Money):** cutover date approach — RM owns transactions before cutover, SimpleFIN owns after. Pre-cutover RM transactions are deleted during sync if SimpleFIN provides the same period.

**Budgets:** One row per `category_id` (globally applied). The `budgets` table retains the `rollover` column from migration 001 for future Phase 2 work but it's not consumed by the current UI.

## Features

### Core Transactions
- Rocket Money CSV import: auto-creates accounts, sign-flips amounts, and generates rename rules from Custom Name columns
- SimpleFIN bank sync with AES-256-GCM encrypted access URL storage
- Tap-to-expand transaction cards — grid-template-rows animated expand/contract (220ms), auto-scroll-into-view on expand (accounts for bottom tabs height)
- Inline edit modal: merchant, category, notes (date/amount read-only — bank ground truth)
- Transaction deletion and transfer/ignored toggles
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
- Transfer auto-matcher: pairs opposite-sign same-day transactions in transfer-flagged categories
- Scheduler: daily at 6 AM (configurable) + manual "Sync Now"
- Sync log with status tracking (success / error / partial)
- Partial sync detection: if SimpleFIN reports account connection issues, status shows as yellow "Partial sync" rather than red "Error"
- Node `fetch` rejects inline URL credentials — implementation strips them and sends as Basic Auth header

### Accounts
- Full CRUD + merge (reassigns all transactions to target, deletes source) + archive/unarchive
- **Reorder mode:** dedicated toggle that replaces normal rows with full-width drag handles; in-mode there are no competing tap targets, activation distance is 0 (any movement), and tapping "Done" exits. Outside reorder mode, no `@dnd-kit` listeners are mounted at all — eliminating the press-and-hold activation problems that dogged earlier versions.
- Sort order persisted to `sort_order` column
- "See transactions" button filters the Transactions page via `?accounts=X` query param
- Three-dot dropdown menu per account (Edit, Merge, Archive, Delete)
- Edit modal: name, type, institution only (balance and last-4 are intentionally not editable)

### Budgets
- **Monthly caps, global per category.** One amount per category that applies to every month. Editing `Groceries` updates the cap for every past and future month.
- **Month navigation** via `‹ / ›` arrows plus a visible-but-transparent `<select>` overlaying a styled pill — opens the native month picker on tap. Chose `<select>` over a hidden `<input type="month">` because hidden month-type inputs caused unexpected mobile-browser zoom on page load.
- **Summary card:** overall spent of budgeted, percentage, remaining/over, plus an unbudgeted-spending callout
- **Income / Expenses / Net** three-column stat row at the top, scoped to the viewed month, excludes ignored + transfer transactions
- **Three sections:** Budgeted (sorted most-over-budget first, per-row progress bars with over/remaining footnotes), Spent without a budget (quick-add CTAs), and No activity (collapsed toggle)
- Progress bars transition green → yellow (≥85%) → red (>100%)
- Endpoints: GET `/api/budgets?month=YYYY-MM`, GET `/api/budgets/months`, PUT `/api/budgets` (upsert), DELETE `/api/budgets/:id`

### Dashboard
Multi-card overview page at `/dashboard`. Stacked on mobile, 2-column grid on ≥900px with Recent Activity spanning full width. Data comes from parallel calls to existing endpoints — no dashboard-specific backend.

- **Accounts card:** net worth (sum of active balances), breakdown by group (Cash = checking+savings+cash, Investments, Credit cards, Loans, Other). Rows with zero balance are hidden.
- **This month card:** Day X of Y + compact Income / Expenses / Net trio
- **Budget pulse card:** overall progress bar + up to 3 "attention" categories (over-budget first, then 85%+), or a success message when all are on track
- **Top spending card:** up to 7 categories with horizontal bars scaled to the biggest spender; bars use each category's color
- **Recent activity card:** last 10 transactions, tap-to-navigate to full Transactions page
- Shimmer skeleton loading per card
- Empty state routes new users to Import / SimpleFIN setup

### Responsive Layout
- **Phone (< 640px):** Single column, bottom tabs
- **Tablet/Foldable (640–1079px):** Centered content (max-width 820px), bottom tabs
- **Desktop (≥ 1080px):** Left sidebar (260px) + content area, bottom tabs hidden

### Navigation
- **Bottom tabs (5):** Dashboard, Transactions, Budgets, Accounts, More
- **Desktop sidebar:** Same 5 items as bottom tabs
- **Hamburger menu (top-right):** Import, Settings, Theme toggle, Sign out
- **More tab:** Opens bottom sheet (mobile) or centered modal (desktop) with cards for Rules, Settings, Import, and coming-soon placeholders (Mortgage Calculator, Net Worth, Goals, Credit Score)
- **Settings page:** SimpleFIN configuration and sync log only. The Rules management card was removed after the Rules feature got its own dedicated page.
- **React Router v6:** Client-side routing with browser back/forward support. All routes served via Express catch-all for deep-link support.

### UI Details
- `AnimatedModal` component: render-prop pattern (`{({ close }) => ...}`), 180ms slide-in/slide-out via `.closing` CSS class
- Scroll lock: `document.body.style.overflow = 'hidden'` on all modals, hamburger menu, and More sheet
- Three-way theme toggle (☀️ / 💻 / 🌙): localStorage persistence with pre-paint script in `index.html` to avoid flash
- 40+ CSS custom properties for light/dark themes, emerald-600/500 accent
- Inter Tight (Google Fonts) throughout — no serif fonts
- Bottom tabs: 80px height (Material Design baseline), 5 columns
- Dismissable sync-error banner keyed by error message text in localStorage
- Mobile zoom suppression: `text-size-adjust: 100%` on `html` + 16px minimum font-size on editable form controls ≤767px (prevents both Android Chrome text boosting and iOS Safari focus zoom)

### PWA
- `manifest.webmanifest` with SVG icons (regular + maskable)
- Service worker (`sw.js`): pass-through fetch, no offline caching (satisfies install criteria only)
- Installable on Android Chrome, desktop Chrome/Edge
- `theme-color` meta tags for light and dark schemes

## API Endpoints

### Auth
| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/login` | Session login |
| POST | `/api/auth/logout` | Session logout |
| GET | `/api/auth/me` | Check auth status |

### Transactions
| Method | Path | Description |
|---|---|---|
| GET | `/api/transactions?...` | Paginated list with full filter/search/sort. Returns display values (COALESCE'd) + originals + edit metadata + `has_edits` flag, plus `grandTotal` for "X of Y shown" UI |
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
| GET | `/api/categories` | List all (32 seeded from Rocket Money) |

### Budgets
| Method | Path | Description |
|---|---|---|
| GET | `/api/budgets?month=YYYY-MM` | Monthly overview: `budgeted` / `unbudgeted` / `inactive` arrays + summary with `total_budgeted`, `total_spent_in_budgets`, `total_spent_unbudgeted`, `total_spent`, `total_income`, `total_expenses`, `total_net`. Month defaults to current month. |
| GET | `/api/budgets/months` | Distinct months with transaction activity (for month picker) |
| PUT | `/api/budgets` | Upsert `{ category_id, amount, rollover? }` — global per-category |
| DELETE | `/api/budgets/:id` | Delete budget |

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
- `db/migrations/001` through `010`
- `routes/`: auth, health, import, transactions, accounts, categories, rules, simplefin, budgets
- `services/`: csvImport, ruleMatcher (exports `loadRules`, `computeEdits`, `countMatches`, `reapplyRulesToAllTransactions`, `reapplyRulesToTransaction`, `revertEditsForRule`, `applyRulesToDraft`), simplefinClient, simplefinSync, transferMatcher

### Frontend (`frontend/`)
- `index.html` (Inter Tight font, PWA manifest link, pre-paint theme script, SW registration)
- `public/`: manifest.webmanifest, icon.svg, icon-maskable.svg, sw.js
- `src/main.jsx`, `src/App.jsx` (BrowserRouter, passes accounts + categories to Transactions and Dashboard), `src/Login.jsx`, `src/api.js` (get/post/put/patch/del), `src/index.css` (~3000 lines)
- `src/hooks/useTheme.js`
- `src/components/`: AnimatedModal, BottomTabs, DesktopSidebar, DropdownMenu, FilterSheet, HamburgerMenu, MoreSheet, SyncErrorBanner
- `src/pages/`: Dashboard, Transactions, Budgets, Accounts, Rules, Settings, Import

## Known Gotchas
- **better-sqlite3 `.iterate()` + write transaction** = "database connection is busy" — always use `.all()` instead
- **Node `fetch` rejects inline URL credentials** — strip from URL and send as Basic Auth header
- **SQLite integer 0 renders as "0" in JSX** with `&&` pattern — must coerce with `!!` (e.g., `{!!account.is_archived && <Badge />}`)
- **SQLite `ALTER TABLE ADD COLUMN`** cannot use function defaults like `datetime('now')` — must use constant default then backfill
- **`touch-action: manipulation`** on draggable elements blocks `@dnd-kit` touch events — reorder mode removes competing touch targets entirely rather than fighting activation heuristics
- **NavLink renders `<a>` elements** — need explicit `text-decoration: none; outline: none;` on all states to prevent flash-underline on tap
- **SW needs a fetch listener** (even pass-through) to satisfy PWA install criteria in some browsers
- **Rules with match counts** loads slowly with many rules — `withCounts=1` scans all transactions per rule. For 300+ rules × 8K+ transactions, expect 10–20 second initial load.
- **Hidden `<input type="month">` triggers mobile-browser layout quirks** — use a `<select>` overlaying a styled pill instead when you want a native month picker
- **Rule chaining no longer works** — since conditions match against originals only, a rule can't match on a value a higher-priority rule just renamed to. This was an intentional v12 change (match counts were broken because of the old chaining semantics). If a hand-built rule relied on chained renames, rewrite it to condition on `original_description` or the unmodified upstream value.

## Planned
- **Phase 2 Budgets:** rollover (column already exists), income targets with proper direction, category groupings, spending pace
- **Phase 2 Dashboard:** month-over-month comparison on the This Month card, spending-over-time and net-worth charts
- **Recurring bills / subscriptions detection**
- **Goals tracking**
- **Bulk merge suggestions:** Auto-pair SimpleFIN duplicates with RM accounts by institution + last-4
- **Mortgage calculator, credit score** (placeholders in More sheet)
