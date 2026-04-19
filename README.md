# Orbit Money

Orbit Money, currently named Budget Tracker in the app, is a self-hosted
personal finance tracker built as an installable PWA. It imports Rocket Money
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
- `PORT` defaults to `5008`. Do not change this unless you also update the
  Docker, tunnel, and documentation assumptions.
- `TZ` defaults to `America/Chicago`.

For SimpleFIN encryption, `SIMPLEFIN_ENCRYPTION_KEY` must be a 64-character hex
string.

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

- Container name: `budget-tracker`
- Host/container port: `5008`
- Docker network: external `web_proxy`
- Docker volume: `budget-data`
- Data mount: `/app/data`

Do not rename the service, port, network, or volume casually. Those names are
part of the deployment setup.

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

There is not currently a root-level `check` script or automated test suite. When
changing behavior, manually smoke test the affected area and at minimum confirm:

- The frontend builds.
- The Docker container starts.
- `/api/health` returns `status: ok`.
- Login still works.
- Mobile layout still feels usable.
- The PWA manifest and service worker still load.

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
