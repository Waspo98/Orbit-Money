# Orbit Money

Orbit Money is a self-hosted personal finance tracker built as an installable
PWA. It imports Rocket Money CSV exports, syncs bank data through SimpleFIN,
stores data in SQLite, and runs as a Docker Compose app.

## Features

- Dashboard with configurable cards, recent transaction review, budgets, goals,
  subscriptions, upcoming items, net worth, retirement, and mortgage snapshots
- Account, transaction, budget, spending trends, category, rule, savings goal,
  upcoming, retirement calculator, housing calculator, household, MHA tracker,
  and net worth screens
- Rocket Money CSV import
- SimpleFIN bank sync with encrypted access URL storage
- Credit card profile tracking
- Merchant logo enrichment with optional Logo.dev keys
- Local login, optional OIDC login, or both, with household-scoped data
- Household sharing for OIDC users by invited email
- SQLite storage in a Docker volume
- Installable PWA with manifest, icons, and service worker
- Read-only offline PWA support after a successful online load

## Requirements

- Docker and Docker Compose
- A `.env` file based on `.env.example`
- A Docker network named `web_proxy`

Create the external Docker network once if you do not already have it:

```powershell
docker network create web_proxy
```

## Quick Start

Copy the example environment file:

```powershell
Copy-Item .env.example .env
notepad .env
```

For a first local install with password login, set at least:

- `AUTH_PROVIDER=local`
- `ADMIN_USERNAME`
- `ADMIN_PASSWORD`
- `SESSION_SECRET`

Generate a strong `SESSION_SECRET` with:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
-join ($bytes | ForEach-Object { $_.ToString('x2') })
```

Leave optional integrations blank until you use them. In particular, leave
`API_KEY=` blank unless you intentionally want API-key access, and leave
`SIMPLEFIN_ENCRYPTION_KEY=` blank until you connect SimpleFIN.

Start Orbit Money:

```powershell
docker compose pull
docker compose up -d
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

- `AUTH_PROVIDER`, one of `local`, `oidc`, or `both`
- `ADMIN_USERNAME`, when using local auth
- `ADMIN_PASSWORD`, when using local auth or `AUTH_PROVIDER=both`
- `SESSION_SECRET`

Optional or feature-specific values:

- `API_KEY` enables programmatic API access through the `X-API-Key` header.
  Leave it blank unless you want this access path.
- `OIDC_ISSUER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, and
  `OIDC_REDIRECT_URI` are required when `AUTH_PROVIDER=oidc` or `both`.
- `OIDC_SCOPES` defaults to `openid email profile`.
- `OIDC_LOGIN_LABEL` customizes the OIDC login button. The default is
  `Log in with OIDC`.
- Existing private installs that still use the old `AUTHENTIK_*` variable names
  continue to work, but new installs should use `OIDC_*`.
- `SIMPLEFIN_ENCRYPTION_KEY` is required before SimpleFIN sync can be used. It
  must be a 64-character hex string.
- `LOGO_DEV_PUBLISHABLE_KEY` enables merchant logo display.
- `LOGO_DEV_SECRET_KEY` enables server-side brand search for manual logo
  overrides.
- `WEB_PUSH_PUBLIC_KEY`, `WEB_PUSH_PRIVATE_KEY`, and `WEB_PUSH_SUBJECT` enable
  installable-PWA push notifications. Generate VAPID keys with
  `npx web-push generate-vapid-keys`; `WEB_PUSH_SUBJECT` should be a contact
  URL or mailto address such as `mailto:you@example.com`.
- `ENABLE_SAMPLE_DATA=1` shows `Continue with sample data` on the login page
  and allows temporary sample households. Leave it `0` for normal installs.
- `SESSION_NAME` changes the session cookie name. The default is `connect.sid`.
- `PORT` defaults to `5008`.
- `TZ` defaults to `America/Chicago`.

Orbit Money refuses to start with known placeholder values for required secrets.
This is intentional: a copied `.env.example` should not accidentally become a
public deployment with predictable credentials.

## Auth And Multi-User Data

Orbit Money stores users, households, memberships, and pending household shares
in SQLite. Existing single-user data is migrated into household `1`. New public
installs use the neutral default name `My Household`; existing installs only get
renamed by migration if they still have the old default household label.

For a simple self-hosted install, keep `AUTH_PROVIDER=local` and use the
username/password login. For SSO, set `AUTH_PROVIDER=oidc`. To show both the
local form and the OIDC button on the login page, set `AUTH_PROVIDER=both`.
Authentik, Authelia, Keycloak, and similar providers should use this callback:

```text
https://your-orbit-domain.example/api/auth/oidc/callback
```

New OIDC users receive their own household with default app settings and
categories. Household owners/admins can invite another OIDC user by email from
Settings.

## Offline PWA Behavior

Orbit Money caches the app shell and last successful household API reads so an
installed PWA can reopen without internet. Offline mode is read-only: create,
edit, delete, import, restore, SimpleFIN sync, and sharing/admin actions require
reconnection. The app shows an offline banner while disconnected and clears
cached household financial data when the user signs out.

Cached data is scoped to the signed-in user and household. Owner households can
use their cached data offline without a time limit. Shared/member households
must have a successful online access validation within the last 7 days before
cached data is shown offline.

## Push Notifications

When Web Push keys are configured, Preferences can enable push notifications for
weekly snapshots, income arrivals, SimpleFIN sync issues, and manual account
snapshot reminders. The backend scheduler decides when notifications are due,
then the installed PWA service worker displays them even when Orbit is not open.
Notification clicks deep-link back into Orbit, including directly opening the
Add Record popup for account snapshot reminders.

## Docker Layout

The default Compose service uses:

- Image: `ghcr.io/waspo98/orbit-money:latest`
- Container name: `orbit-money`
- Host/container port: `5008`
- Docker network: external `web_proxy`
- Docker volume: `orbit-money-data`
- Data mount: `/app/data`

Do not rename the service, port, network, or volume casually if you are updating
an existing install. SQLite data lives in the Docker volume.

Update an existing install with:

```powershell
git pull
docker compose pull
docker compose up -d
```

The Docker image is published to GitHub Container Registry. If you fork the repo
and publish your own image, update the `image:` value in `docker-compose.yml`.

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

The repo intentionally does not track `.env`, local runtime tools, local
databases, Docker volume backups, `node_modules`, build output, or private
server notes.

## Build And Verification

Frontend production build:

```powershell
cmd /c scripts\build-frontend.cmd
```

Backend syntax smoke check and automated tests:

```powershell
cmd /c scripts\check-backend.cmd
```

Production-style Docker smoke test from the published image:

```powershell
docker compose pull
docker compose up -d
Invoke-RestMethod http://localhost:5008/api/health
```

Local source-build smoke test:

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

This repo includes optional GitHub Actions workflows for GitHub Container
Registry and the maintainer's self-hosted Windows runner:

- Pushes to `main` publish `ghcr.io/waspo98/orbit-money:latest` and `:main`,
  then call `scripts\deploy-live.cmd`
- Pushes to `Beta` publish `ghcr.io/waspo98/orbit-money:beta`, then call
  `scripts\deploy-beta.cmd`, which delegates to `deploy\beta\deploy-beta.cmd`
- Pushing a tag like `v0.75.0` publishes matching version image tags for
  release installs

The deploy jobs run on the maintainer's self-hosted Windows runner. The deploy
scripts publish the GHCR image when `ORBIT_PUBLISH_IMAGE=1`, then pull and
restart the matching Docker Compose service. If a manual deploy cannot pull the
published image, the scripts print the pull failure and build the same Compose
service locally before restarting it. Self-hosters do not need GitHub Actions to
run the app.

For public anonymous `docker compose pull` support, the GitHub Container
Registry package must be public. If the first published package is private, make
the package public from GitHub's package settings. If GHCR still returns
`denied`, Docker may be sending stale local registry credentials; the deploy
scripts will fall back to a local Docker build in that case.

If the package is public but a manual deploy still logs
`error from registry: denied`, clear Docker's saved GHCR login and retry the
pull so Docker uses anonymous public access:

```bat
docker logout ghcr.io
docker compose pull
```

## Releases

Deploys and releases are intentionally separate. A Docker deploy pulls the
published image and restarts the running app from a branch. A GitHub Release marks a stable,
named version with a Git tag, release notes, and GitHub's generated source
archives.

Use releases for meaningful milestones, not every rebuild. The maintainer flow
is semi-automatic: inspect changes since the previous tag, choose the next
semantic version, update version labels/files when approved, write clear release
notes, tag the release commit, push the branch and tag, then publish the GitHub
Release. Keep the app in `0.x` until it is considered public-ready.

## Beta Deployment

The beta deployment is maintainer infrastructure, not a second install path for
normal users. It lives in `deploy/beta/`, pulls the published beta image, and runs a
separate Docker container and volume:

```powershell
cmd /c scripts\deploy-beta.cmd
```

Beta is always deployed through Docker. Do not use a Vite dev server, preview
server, or `start-beta` helper for beta. See `deploy/beta/README.md` for the
beta port, volume, demo-data behavior, and maintainer deploy script.

## Storage And Backups

Back up the `orbit-money-data` Docker volume before upgrading, changing storage
settings, or experimenting with migrations. Schema changes are applied through
versioned SQL migrations in `backend/src/db/migrations/`.

Money values are stored in SQLite as integer cents and serialized through the
API as dollar values for the frontend. Percentage fields remain decimal percent
values, not money.

## Development

Docker is the supported runtime. For code validation outside Docker, install
Node.js 20.19+ or provide the private `.tools` runtime used by the helper
scripts. Local dependency folders and build output are ignored by Git.

## Documentation

- `docs/reference.md` covers implementation details, API routes, data model
  notes, and known gotchas.
- `deploy/beta/README.md` covers the maintainer beta container.
