# Budget Tracker

Self-hosted personal finance tracker for The Overbay HAL 9000. Node.js + Express backend, React + Vite frontend, SQLite storage. Designed to pair with SimpleFIN for bank sync and import history from Rocket Money.

This is the **Phase 1 scaffold** — infrastructure, schema, and auth only. No features yet. Running this gets you a working login + placeholder page that confirms the full stack (Docker → Express → SQLite → React → Cloudflare Tunnel) is wired up correctly.

---

## What's In This Scaffold

- Multi-stage `Dockerfile` (`node:20-slim` build → `node:20-alpine` prod, matching Lawn Tracker's WSL2-safe pattern)
- `docker-compose.yml` on `web_proxy` network with named `budget-data` volume
- SQLite schema for all Phase 1 tables (accounts, transactions, categories, rules, budgets, goals, simplefin config)
- Migration runner — applies SQL files in `backend/src/db/migrations/` in order, tracked in `_migrations` table
- 32 default categories seeded from your Rocket Money CSV analysis, with icons, colors, transfer/income flags
- Session-based auth (admin credentials from `.env`) + `X-API-Key` header support
- Indefinite session cookies (10-year expiry)
- Placeholder React app that shows DB counts after login

## What's NOT In This Scaffold (Yet)

- CSV import
- SimpleFIN sync
- Rules engine
- Transactions UI
- Accounts/Categories management UI
- Real design system (minimal CSS for now; full theming in Phase 1 step 10)

---

## Setup

### 1. Copy to the server

```powershell
# From your dev machine or the server itself, place these files at:
C:\Docker\Compose\Budget Tracker\
```

### 2. Create `.env` from the example

```powershell
cd "C:\Docker\Compose\Budget Tracker"
copy .env.example .env
```

Edit `.env` and replace the placeholder values. Generate strong random strings with:

```powershell
# PowerShell one-liner for a 32-byte hex random string
-join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Max 16) })
```

Do this for `SESSION_SECRET`, `API_KEY`, and `SIMPLEFIN_ENCRYPTION_KEY`.

### 3. Free up `money.overbay.app` in Cloudflare Tunnel

Since we're reclaiming this hostname from Actual Budget:

1. Stop and remove the Actual Budget container + volume
2. Edit your Cloudflare Tunnel config and change the `money.overbay.app` service from `http://actual_server:5006` to `http://budget-tracker:5008`
3. Remove the Actual Budget backup step from `DailyBackup.ps1`

### 4. Build and start

```powershell
docker compose up -d --build
```

First boot will:
- Run migrations (creates all tables)
- Seed 32 default categories
- Start Express on port 5008

### 5. Verify

Visit `https://money.overbay.app` (or `http://192.168.86.200:5008` for direct LAN access). You should see a login page. Log in with the credentials from `.env`.

Once logged in, the placeholder page will show:
- Transactions: 0
- Categories: 32
- Accounts: 0

If you see that, the scaffold is healthy and we're ready for CSV import.

---

## Directory Layout

```
Budget Tracker/
├── README.md                            # this file
├── .env.example                         # template for .env
├── .gitignore
├── .dockerignore
├── docker-compose.yml
├── Dockerfile
├── backend/
│   ├── package.json
│   └── src/
│       ├── server.js                    # Express entry
│       ├── config.js                    # env var loader
│       ├── auth.js                      # session/API-key middleware
│       ├── db/
│       │   ├── index.js                 # SQLite connection
│       │   ├── migrations.js            # migration runner
│       │   └── migrations/
│       │       ├── 001_initial_schema.sql
│       │       └── 002_seed_categories.sql
│       └── routes/
│           ├── auth.js                  # /api/auth/*
│           └── health.js                # /api/health
└── frontend/
    ├── package.json
    ├── vite.config.js
    ├── index.html
    └── src/
        ├── main.jsx
        ├── App.jsx
        ├── Login.jsx
        ├── api.js
        └── index.css
```

## Data Storage

All persistent data lives in the `budget-data` named Docker volume, mounted at `/app/data`:

| File | Purpose |
|---|---|
| `budget.db` | Main SQLite database (accounts, transactions, categories, rules, etc.) |
| `sessions.db` | `connect-sqlite3` session store (separate file by design) |

## Backup Integration

After the scaffold is verified, add this step to `C:\Scripts\DailyBackup.ps1`:

```powershell
# Budget Tracker database
docker cp budget-tracker:/app/data/budget.db "$BackupStaging\budget-tracker\budget.db"
```

(No need to back up `sessions.db` — it's transient.)

## Gotchas

- **Native module compilation:** `better-sqlite3` is a native module. The Dockerfile includes Alpine build tools (`python3`, `make`, `g++`) and removes them after install. If builds fail, you may need to bump the `better-sqlite3` version to one with prebuilt Alpine binaries.
- **First run after schema change:** Add a new file like `003_whatever.sql` to `migrations/`. The runner picks up new files in sorted order on next restart. Never edit an already-applied migration — always create a new one.
- **Indefinite sessions:** Cookies are set with a 10-year `maxAge`. Clearing your browser cookies logs you out; otherwise you'll stay logged in essentially forever.

## What's Next

Once this scaffold is running and verified, the next iteration adds:

1. Rocket Money CSV import (the 8,428-row file)
2. Account type mapping UI (classify each imported account)
3. Auto-generate rename rules from your Rocket Money Custom Names
4. Basic transactions list view

After that: accounts/categories management, the rules engine, and SimpleFIN sync.
