# Orbit Money

Orbit Money is a self-hosted personal finance tracker built as an installable
PWA. It imports Rocket Money CSV exports, syncs bank data through SimpleFIN,
stores data in SQLite, and runs as a Docker Compose app.

## Features

- Account, transaction, budget, category, rule, savings goal, retirement calculator, household, and net worth views
- Rocket Money CSV import
- SimpleFIN bank sync
- Merchant logo enrichment with optional Logo.dev keys
- Local admin login or Authentik OIDC login, with household-scoped data
- SQLite storage in a Docker volume
- Installable PWA with manifest and service worker

## Requirements

- Docker and Docker Compose
- A `.env` file based on `.env.example`
- An existing Docker network named `web_proxy`

The default Compose file keeps the current production-compatible Docker network,
port, container name, and volume name. If you do not already have the external
network, create it once:

```powershell
docker network create web_proxy
```

## Quick Start

Copy the example environment file and replace the placeholders:

```powershell
Copy-Item .env.example .env
notepad .env
```

Start Orbit Money:

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

## Environment

Required values:

- `AUTH_PROVIDER`, either `local` or `authentik`
- `ADMIN_USERNAME`, when using local auth
- `ADMIN_PASSWORD`, when using local auth
- `SESSION_SECRET`

Optional or feature-specific values:

- `API_KEY` enables programmatic API access through the `X-API-Key` header.
  Multi-user API requests may pass `X-Household-ID`; otherwise household `1`
  is used for backward compatibility.
- `AUTHENTIK_ISSUER_URL`, `AUTHENTIK_CLIENT_ID`, `AUTHENTIK_CLIENT_SECRET`, and
  `AUTHENTIK_REDIRECT_URI` are required when `AUTH_PROVIDER=authentik`.
- `SIMPLEFIN_ENCRYPTION_KEY` is required before SimpleFIN sync can be used.
- `LOGO_DEV_PUBLISHABLE_KEY` enables merchant logo display.
- `LOGO_DEV_SECRET_KEY` enables server-side brand search for manual logo overrides.
- `SESSION_NAME` changes the session cookie name. The default is `connect.sid`.
- `PORT` defaults to `5008`.
- `TZ` defaults to `America/Chicago`.

`SIMPLEFIN_ENCRYPTION_KEY` and `SESSION_SECRET` should be strong random values.
`SIMPLEFIN_ENCRYPTION_KEY` must be a 64-character hex string.

## Authentik And Multi-User Data

Orbit Money stores users, households, and memberships in SQLite. Existing
single-user data is migrated into household `1` (`Neal Household`) and remains
in the same Docker volume. The first Authentik user to sign in claims that
legacy household so the current data follows the real owner into SSO.

New Authentik users receive their own household with default app settings and
category defaults copied from the app default tables. Those defaults are stored
in SQLite as `app_default_settings` and `app_default_categories` so future
defaults can be changed without rewriting existing households.

For Authentik, configure an OAuth2/OpenID provider with this callback:

```text
https://your-orbit-domain.example/api/auth/oidc/callback
```

## Docker Layout

The default Compose service uses:

- Container name: `orbit-money`
- Host/container port: `5008`
- Docker network: external `web_proxy`
- Docker volume: `orbit-money-data`
- Data mount: `/app/data`

Do not rename the service, port, network, or volume casually if you are updating
an existing install. SQLite data lives in the Docker volume.

## Repository Layout

```text
.
|-- backend/             # Express API, SQLite setup, migrations, services
|-- frontend/            # React/Vite PWA
|-- deploy/
|   `-- beta/            # Maintainer-only Docker beta deployment
|-- docs/
|   `-- reference.md     # Technical reference and feature notes
|-- scripts/             # Docker-oriented deploy/build helpers
|-- docker-compose.yml   # Main self-host Compose file
|-- Dockerfile           # Multi-stage frontend build + backend runtime
|-- AGENTS.md            # Coding-agent project instructions
`-- README.md
```

The repo intentionally does not track local runtime tools, local databases,
Docker volumes, `node_modules`, or private server notes.

## Beta Deployment

The beta deployment is maintainer infrastructure, not a separate copy of the
app. It lives in `deploy/beta/`, builds from this repo root, and runs a separate
Docker container and volume:

```powershell
cmd /c scripts\deploy-beta.cmd
```

Beta is always deployed through Docker. Do not use a Vite dev server, preview
server, or `start-beta` helper for beta; the supported beta path rebuilds and
restarts the `orbit-money-beta` Docker container on port `5019`.

See `deploy/beta/README.md` for the beta port, volume, demo-data behavior, and
maintainer deploy script.

## Build And Verification

Frontend production build:

```powershell
cmd /c scripts\build-frontend.cmd
```

Backend syntax smoke check:

```powershell
node --check backend\src\server.js
```

Backend automated tests:

```powershell
cd backend
npm test
```

Production-style Docker smoke test:

```powershell
docker compose up --build -d
Invoke-RestMethod http://localhost:5008/api/health
```

There is not currently a root-level `check` script. When changing behavior,
run the relevant package tests, manually smoke test the affected area, and at
minimum confirm that the frontend builds, the container starts, `/api/health`
returns `status: ok`, login still works, mobile layout remains usable, and the
PWA manifest/service worker still load.

## Maintainer Deploys

This repo includes optional GitHub Actions workflows for the maintainer's
self-hosted Windows runner:

- Pushes to `main` call `scripts\deploy-live.cmd`
- Pushes to `Beta` call `scripts\deploy-beta.cmd`, which delegates to
  `deploy\beta\deploy-beta.cmd`

Those workflows are specific to the maintainer's server checkout and Docker
host. Self-hosters do not need GitHub Actions to run the app.

## Storage And Backups

Back up the `orbit-money-data` Docker volume before upgrading, changing storage
settings, or experimenting with migrations. Schema changes are applied through
versioned SQL migrations in `backend/src/db/migrations/`.

Money values are stored in SQLite as integer cents and serialized through the
API as dollar values for the frontend. Percentage fields remain decimal percent
values, not money.

## Development

Docker is the supported runtime. For code validation outside Docker, install
Node.js 20+ and run package commands directly in `backend/` or `frontend/`.
Local dependency folders and build output are ignored by Git.

## Documentation

- `docs/reference.md` covers implementation details, API routes, data model
  notes, and known gotchas.
- `deploy/beta/README.md` covers the maintainer beta container.
