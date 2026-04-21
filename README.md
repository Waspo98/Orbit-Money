# Orbit Money

Orbit Money is a self-hosted personal finance tracker built as an installable
PWA. It imports Rocket Money
CSV exports, syncs bank data through SimpleFIN, stores data in SQLite, and runs
as a Docker Compose service on Windows.

The app is currently deployed behind Cloudflare Tunnel. Public networking,
ports, Docker volume names, and auth behavior are intentional and should be
changed only with care.

## Stack

- Frontend: React 18 + Vite
- Backend: Node.js 20 + Express
- Database: SQLite through `better-sqlite3`
- Auth: single-admin session login, with optional `X-API-Key` access
- Runtime: Docker Compose
- App type: installable PWA

## Project Layout

```text
.
|-- backend/              # Express API, SQLite setup, migrations, services
|   `-- src/
|       |-- db/           # DB connection and migrations
|       |-- routes/       # API routes
|       `-- services/     # CSV import, rules, SimpleFIN sync, transfer matching
|-- frontend/             # React/Vite PWA
|   |-- public/           # manifest, icons, service worker
|   `-- src/              # app shell, pages, components, hooks
|-- Orbit Money Beta/     # Separate beta Docker deployment
|-- docker-compose.yml    # Production-style Compose service
|-- Dockerfile            # Multi-stage frontend build + backend runtime
|-- Agents.md             # Instructions for coding agents
|-- Reference.md          # Technical reference and feature notes
`-- Server_info.md        # Private deployment/operator notes
```

## Environment

Copy the example env file and replace the placeholder values before running the
app:

```powershell
Copy-Item .env.example .env
notepad .env
```

Required values:

- `ADMIN_USERNAME`
- `ADMIN_PASSWORD`
- `SESSION_SECRET`

Optional or feature-specific values:

- `API_KEY` enables programmatic API access through the `X-API-Key` header.
- `SIMPLEFIN_ENCRYPTION_KEY` is required before SimpleFIN sync can be used.
- `LOGO_DEV_PUBLISHABLE_KEY` enables merchant logo enrichment in transaction
  lists. When it is not set, transactions continue to use category icons.
- `PORT` defaults to `5008`. Do not change this unless you also update the
  Docker, tunnel, and documentation assumptions.
- `SESSION_NAME` defaults to `connect.sid`. Leave production on the default;
  beta overrides this so localhost sessions do not collide.
- `TZ` defaults to `America/Chicago`.

For SimpleFIN encryption, `SIMPLEFIN_ENCRYPTION_KEY` must be a 64-character hex
string.

Merchant logos are resolved through Logo.dev by unique merchant name and cached
in SQLite as display enrichment. Logo image bytes are not stored by Orbit Money;
the browser/provider cache handles the image files, and failed logo loads fall
back to the transaction category icon.

## Run With Docker Compose

This is the primary way the app is intended to run.

```powershell
docker compose up --build -d
```

Open:

```text
http://localhost:5008
```

Check health:

```powershell
Invoke-RestMethod http://localhost:5008/api/health
```

Stop the app:

```powershell
docker compose down
```

The Compose service uses:

- Container name: `orbit-money`
- Host/container port: `5008`
- Docker network: external `web_proxy`
- Docker volume: `orbit-money-data`
- Data mount: `/app/data`

Do not rename the service, port, network, or volume casually. Those names are
part of the deployment setup.

Storage rename note: the production SQLite volume was renamed from the old
project-prefixed volume to `orbit-money-data` as part of the Orbit Money rename.

## Run The Beta Docker Deployment

The beta deployment lives in `Orbit Money Beta/` and runs as a separate Docker
container with its own data volume. Use it for branch testing before merging
changes to the public production app.

```powershell
docker compose -f "Orbit Money Beta\docker-compose.yml" -p orbitmoney-beta up --build -d
```

Or from the beta folder:

```bat
deploy-beta.cmd
```

Open:

```text
http://localhost:5019
```

Check health:

```powershell
Invoke-RestMethod http://localhost:5019/api/health
```

The beta deployment uses:

- Cloudflare hostname target: `orbitbeta.overbay.app`
- Container name: `orbit-money-beta`
- Host port: `5019`
- Container port: `5008`
- Docker network: external `web_proxy`
- Docker volume: `orbitmoney-beta_orbit-money-beta-data`
- Data mount: `/app/data`

The beta container reads the parent `.env`, but overrides:

- `SESSION_NAME=orbit_beta.sid`
- `SEED_DEMO_DATA=1`

That keeps beta browser sessions separate from production on localhost and seeds
demo data into the beta-only volume when the beta database is empty.

## Local Development

Docker is the smoothest path for normal use. If you want to run the frontend and
backend separately during development, start from the repo root and install
dependencies in each package:

```powershell
Set-Location backend
npm install

Set-Location ..\frontend
npm install
```

The backend reads environment variables from the current Node process. For a
simple local setup, create a backend-local env file:

```powershell
Set-Location ..\backend
Copy-Item ..\.env.example .env
notepad .env
```

Add these lines to the local `backend/.env` file so SQLite data stays in the
repo's ignored `data/` folder instead of `/app/data`, and so local development
can run beside the Docker service without fighting over port `5008`:

```text
PORT=5018
DATA_DIR=../data
```

If you are using the workspace-local portable Node runtime, the helper scripts
under `scripts/` set PATH for you.

Agent-friendly verification commands from the repo root:

```powershell
cmd /c scripts\build-frontend.cmd
.\.tools\node-v20.20.2-win-x64\node.exe --check backend\src\server.js
```

The first command builds the Vite PWA with the bundled Windows Node runtime.
The second is the available backend syntax smoke check; there is not currently
a dedicated backend test suite.

One-click local beta launcher:

```powershell
.\scripts\start-beta.cmd
```

This starts the local backend on `http://localhost:5018`, starts the Vite
frontend on `http://localhost:5173`, and opens the frontend in your browser.

Local dev also seeds a small demo dataset by default when the local database is
empty:

- 3 demo accounts
- 50 demo transactions
- sample monthly budgets

The seed is controlled by `SEED_DEMO_DATA=1` in `backend/.env`; it is not enabled
by the root Docker `.env.example`.

Reset the local demo database and start fresh:

```powershell
.\scripts\reset-demo-data.cmd
```

Start the backend:

```powershell
.\scripts\dev-backend.cmd
```

In a second terminal, start the frontend:

```powershell
.\scripts\dev-frontend.cmd
```

The Vite dev server opens on its printed URL, usually `http://localhost:5173`.
The helper script points Vite's `/api` proxy at `http://localhost:5018`.

Stop the local dev servers:

```powershell
.\scripts\stop-dev.cmd
```

If you run Vite manually, you can choose the backend proxy target with:

```powershell
$env:VITE_API_PROXY_TARGET = 'http://localhost:5018'
```

## Build And Verification

Frontend production build:

```powershell
.\scripts\build-frontend.cmd
```

Production-style Docker build and run:

```powershell
docker compose up --build -d
Invoke-RestMethod http://localhost:5008/api/health
```

## GitHub-Triggered Deploys

The repo includes GitHub Actions workflows for remote deploys:

- Pushes to `Beta` run `.github/workflows/deploy-beta.yml`
- Pushes to `main` run `.github/workflows/deploy-live.yml`

Both workflows expect a GitHub Actions self-hosted runner installed on the
Windows server that hosts Docker. The runner must have these labels:

```text
self-hosted
Windows
X64
```

Install the runner from GitHub:

```text
GitHub repo -> Settings -> Actions -> Runners -> New self-hosted runner
```

Choose Windows and run the commands GitHub gives you on the server. After the
runner is online, GitHub can deploy by calling the local scripts in this repo:

```text
Orbit Money Beta\deploy-beta.cmd
scripts\deploy-live.cmd
```

The deploy scripts intentionally refuse to run if the server checkout has staged
or unstaged changes. Commit or stash local edits before relying on automatic
deploys.

There is not currently a root-level `check` script or automated test suite. When
changing behavior, manually smoke test the affected area and at minimum confirm:

- The frontend builds.
- The Docker container starts.
- `/api/health` returns `status: ok`.
- Login still works.
- Mobile layout still feels usable.
- The PWA manifest and service worker still load.

## Versioning

Orbit Money uses SemVer-compatible package versions and displays a shorter beta
label in the app. For beta releases, keep the user-facing build label in
`frontend/src/version.js` as `v<major>.<minor>b`; the current beta build is
`v0.1b`.

## Housing Calculator And Real Estate

Mortgage accounts can store an optional `estimated_value` in the accounts table.
When a mortgage has an estimated value, the Dashboard includes a `Real Estate`
net worth category calculated as estimated home value minus the mortgage
balance. Mortgage balances are excluded from the `Loans` category so they are
not double-counted.

## Net Worth

The Net Worth page is available from the More menu. It uses active accounts to
show current assets, liabilities, allocation, account contributions, and a
monthly net worth trend.

The trend is derived from existing account balances and transaction history; it
does not add new storage or change backup behavior. Mortgage accounts use the
same `estimated_value - mortgage balance` equity calculation as the Dashboard.

The Housing Calculator is available from More -> Housing Calculator. Selecting
a mortgage account prefills the estimated sale price from `estimated_value` and
the remaining mortgage balance from the account balance. The calculator keeps
the spreadsheet-style assumptions editable for selling fees, purchase details,
cash to close, and monthly payment estimates.

## Goals

The Goals page is available from the More menu. It lets savings targets be
created for retirement, college, a car, a home, an emergency fund, travel, or a
custom purpose.

Each goal can connect to multiple active asset accounts: checking, savings,
cash, investment, or other. For each connected account, the allocation can be a
percentage of the account balance or a fixed dollar amount. When a new or edited
goal would over-allocate an account, the wizard shows existing allocations and
can steal allocation from other goals if the user enables that option.

Goal progress is computed from current account balances and saved allocations.
The trend graph is derived from existing transaction history by walking account
balances backward over time, similar to the Net Worth page. ETA is projected
from recent monthly goal history, and the "imagine" control estimates a new ETA
with extra monthly savings added on top.

Storage is additive: migration `015_goal_allocations.sql` extends the existing
`goals` table with metadata and adds `goal_account_allocations`. It does not
change Docker volumes, ports, auth behavior, imports, or sync storage.

## Category Manager

The Category Manager is available from the More menu. It lets categories be
added, renamed, recolored, given a different emoji, or deleted.

When MHA Tracker is enabled app-wide, the category editor also includes an
`MHA Eligible` toggle for the category default.

Deleting a category clears that category from original and manually edited
transaction category fields, so affected transactions become uncategorized.
Related budgets are removed by the existing budget foreign key behavior. A
category cannot be deleted while any rule still references it as a condition or
categorization action; update or delete the rule first to avoid silent
automation changes.

This feature does not add new tables or migrations and does not change Docker
volumes, ports, auth behavior, imports, or sync storage.

## MHA Tracker

The MHA Tracker is an optional feature for Ministerial Housing Allowance
tracking. Settings includes an MHA Tracker toggle; when off, the tracker page is
hidden from the More menu and transaction cards do not show MHA controls.

When enabled, expanded transaction cards include an `MHA-Eligible` action. The
MHA Tracker page lists eligible transactions, lets active accounts and
categories be marked for automatic inclusion by default, lets categories be
marked for automatic exclusion, and shows year-specific totals:

- `MHA Eligible Total`: the sum of absolute eligible transaction amounts.
- `MHA Savings`: `MHA Eligible Total * 0.27`.

The page includes a year selector; the transaction list, transaction total, and
savings estimate all follow the selected year. Manual transaction-level
`MHA-Eligible` edits take precedence over automatic category ignores; otherwise,
ignored categories win over account/category auto-include defaults.

Storage is additive: migration `012_mha_tracker.sql` adds an app setting,
`accounts.mha_default_eligible`, and per-transaction MHA override columns.
Migration `013_mha_category_defaults.sql` adds `categories.mha_default_eligible`.
Migration `014_mha_category_ignore_defaults.sql` adds
`categories.mha_default_ignored`.
These changes do not affect Docker volumes, ports, auth behavior, imports, or sync
storage.

## Data And Storage

All production data lives in the Docker named volume `budget-data`, mounted at
`/app/data` in the container.

Important files inside the data directory:

- `budget.db`: main SQLite database
- `sessions.db`: Express session store

The app runs SQLite migrations automatically on backend startup. Migration files
live in:

```text
backend/src/db/migrations/
```

Migration rule:

- Never edit an already-applied migration.
- Add a new numbered migration for schema changes.
- Document storage or backup behavior changes clearly.

## PWA Notes

The frontend includes:

- `frontend/public/manifest.webmanifest`
- `frontend/public/sw.js`
- service worker registration in `frontend/index.html`

The current service worker is intentionally minimal. It passes requests through
to the network and exists to support installability; it does not implement full
offline caching.

## Agent Notes

This project has explicit coding-agent instructions in `Agents.md`.

Note: many coding tools conventionally look for `AGENTS.md`. This repo
currently uses `Agents.md`; consider renaming it later if you want maximum tool
compatibility.

Important defaults:

- Preserve existing features unless asked otherwise.
- Do not change ports, volume names, Docker behavior, or auth behavior unless
  explicitly asked.
- Keep mobile UX polished.
- Keep the PWA installable.
- Update docs when behavior changes.
- Flag storage or backup behavior changes clearly.

Before making code changes, read:

- `Agents.md`
- `Reference.md`
- Relevant source files

For deployment or infrastructure changes, also read:

- `Server_info.md`

## Useful Docs

- `Agents.md`: project rules for coding assistants
- `Reference.md`: technical reference, API list, data model notes, known gotchas
- `Server_info.md`: private server, networking, backup, and operations notes
- `.env.example`: required and optional environment variables
