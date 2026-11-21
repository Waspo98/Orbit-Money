# Orbit Money

Orbit Money is a local-first personal finance tracker for Android and the web. Keep your data on your device or connect to your own Docker and SQLite server, import Rocket Money CSV exports, and sync bank accounts through SimpleFIN.

## Features

- **Storage modes:** Run offline on-device in Local mode, or connect to a self-hosted Express and SQLite server for multi-device access and household sharing.
- **Dashboard:** Configurable cards for accounts, budget pulse, top spending, monthly cash flow, biggest expenses, subscriptions, upcoming bills, goals, retirement, mortgage, MHA, and a recent transaction review queue.
- **Accounts and real estate:** Track checking, savings, credit, investment, loan, mortgage, cash, and property accounts with dated balance snapshots, linked mortgages, account merging, and SimpleFIN closed-account prompts.
- **Transactions and rules:** Search, filter, edit, split, and categorize transactions without overwriting imported bank values. Priority-ordered rules automatically rename, categorize, ignore, or flag transfers.
- **Budgets and trends:** Set monthly category caps, inspect spending mix by category or merchant, and review 3, 6, or 12 months of income and category history.
- **Credit card tracker:** Track annual fees, statement close and due dates, utilization, benefit credits across reset cadences, welcome bonus progress, and per-person Chase 5/24 slots.
- **Goals and upcoming:** Allocate multiple asset accounts to savings goals by percent or fixed dollar amount with extra-savings ETA projections, and track recurring bills, subscriptions, and paychecks.
- **Calculators and household:** Model retirement scenarios, drawdown runway, and home buying or selling costs from household profiles and linked retirement accounts. Includes an optional Ministerial Housing Allowance (MHA) tracker with per-year tax estimates.
- **Bank sync and imports:** Import Rocket Money CSV exports, sync through SimpleFIN Bridge (server-side with AES-256-GCM encrypted credentials, or direct from the client in Local mode), and optionally enrich merchant logos with Logo.dev.
- **Auth and sharing:** Local password login, OpenID Connect (OIDC) SSO, or both, plus partner sharing by invited email with read-only or read-write access.
- **Backups and device controls:** Export and restore full JSON workspace backups, optionally sync backups to Google Drive, lock the Android app with biometrics, launch actions from Android shortcuts, and receive optional Web Push notifications.

## Platforms

- **Android:** Native Compose Multiplatform app (Android 7.0+ / API 24+) supporting phones, foldables, and tablets. Rolling debug APKs are published in GitHub Releases (`OrbitMoney-debug.apk` under `debug-latest`, and side-by-side `OrbitBeta-debug.apk` under `beta-latest` using application ID `app.overbay.orbit.beta`).
- **Web PWA and self-hosted server:** Docker Compose stack bundling the Express + SQLite API with the compiled Compose Multiplatform WebAssembly (`wasmJs`) PWA.

## Self-Hosting Quick Start

### Requirements

- Docker and Docker Compose
- A `.env` file based on `.env.example`
- An external Docker network named `web_proxy`

Create the Docker network once if it does not exist yet:

```sh
docker network create web_proxy
```

### Setup

Copy the example environment file and edit it:

```sh
cp .env.example .env
```

For a password-protected install, set at least:

- `AUTH_PROVIDER=local`
- `ADMIN_USERNAME`
- `ADMIN_PASSWORD`
- `SESSION_SECRET`

Generate a 64-character hex secret for `SESSION_SECRET` (and later `SIMPLEFIN_ENCRYPTION_KEY` if you connect SimpleFIN):

```sh
openssl rand -hex 32
```

On Windows PowerShell:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
-join ($bytes | ForEach-Object { $_.ToString('x2') })
```

Leave optional keys blank until you use them. In particular, leave `API_KEY=` blank unless you want programmatic header access, and leave `SIMPLEFIN_ENCRYPTION_KEY=` blank until you connect SimpleFIN. Orbit Money refuses to start if required secrets still contain placeholder text from `.env.example`.

Start the container:

```sh
docker compose pull
docker compose up -d
```

Open `http://localhost:5008` in your browser, or point the Orbit Money Android app to your server URL.

Verify health:

```sh
curl http://localhost:5008/api/health
```

Stop the container:

```sh
docker compose down
```

### Updating an Existing Install

```sh
git pull
docker compose pull
docker compose up -d
```

### Default Docker Layout

- **Image:** `ghcr.io/waspo98/orbit-money:latest`
- **Container name:** `orbit-money`
- **Port:** `5008`
- **Network:** external `web_proxy`
- **Volume:** `orbit-money-data` mounted at `/app/data` (`budget.db` and `sessions.db`)

Keep the volume name intact when upgrading so your SQLite database stays mounted. If you fork the repository and publish your own image, update `image:` in `docker-compose.yml`.

## Environment Variables

Required values:

- `AUTH_PROVIDER`: `local`, `oidc`, or `both`.
- `ADMIN_USERNAME`: Local login username (used with `local` or `both`).
- `ADMIN_PASSWORD`: Local login password (used with `local` or `both`).
- `SESSION_SECRET`: Secret used to sign session cookies.

Optional and feature-specific values:

- `API_KEY`: Enables API access via the `X-API-Key` header. Leave blank if unused.
- `OIDC_ISSUER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_REDIRECT_URI`: Required when `AUTH_PROVIDER` is `oidc` or `both`. Legacy `AUTHENTIK_*` variable names are still read as fallbacks.
- `OIDC_SCOPES`: OIDC scopes, defaulting to `openid email profile`.
- `OIDC_LOGIN_LABEL`: Custom label for the OIDC button (default: `Log in with OIDC`).
- `SIMPLEFIN_ENCRYPTION_KEY`: 64-character hex string required before connecting SimpleFIN on the server.
- `LOGO_DEV_PUBLISHABLE_KEY`: Enables merchant logo display.
- `LOGO_DEV_SECRET_KEY`: Enables server-side brand search for manual logo overrides.
- `WEB_PUSH_PUBLIC_KEY`, `WEB_PUSH_PRIVATE_KEY`, `WEB_PUSH_SUBJECT`: Enable PWA push notifications. Generate VAPID keys with `npx web-push generate-vapid-keys`; set `WEB_PUSH_SUBJECT` to a `mailto:` or HTTPS contact URL.
- `ENABLE_SAMPLE_DATA`: Set to `1` to allow temporary sample households from the login screen. Leave `0` for normal installs.
- `SESSION_NAME`: Session cookie name (default: `connect.sid`).
- `PORT`: HTTP listen port (default: `5008`).
- `TZ`: Server timezone (default: `America/Chicago`).

## Auth, Sharing, and Offline Behavior

Users, households, memberships, and pending invites live in SQLite. Existing single-user databases migrate into household `1`, and new installs default to `My Household`. When using OIDC with Authentik, Authelia, Keycloak, or another provider, configure the redirect URI as:

```text
https://your-orbit-domain.example/api/auth/oidc/callback
```

New OIDC users receive their own household with default categories and settings. Household owners can invite partners by email from Settings and grant read-only or read-write access.

In Local mode, Orbit Money stores its snapshot on-device and works offline without a server. In Self-Hosted mode, both the Android app and the Web PWA cache the latest workspace snapshot locally (and the PWA service worker caches the app shell and WebAssembly bundle) so the app opens offline in read-only mode. Owner households can read cached data offline indefinitely; shared members must validate access online at least once every 7 days. Edits, syncs, imports, and admin actions require a server connection, and signing out clears cached household data.

When Web Push VAPID keys are configured, Settings can enable browser push notifications for weekly summaries, paycheck arrivals, SimpleFIN sync issues, and manual balance snapshot reminders.

## Repository Layout

```text
.
|-- androidApp/          # Android application wrapper, manifest, icons, shortcuts
|-- composeApp/          # Shared Kotlin Multiplatform + Compose Multiplatform UI & engines
|   `-- src/
|       |-- commonMain/  # Screens, ViewModels, domain engines, repository, themes
|       |-- androidMain/ # Biometrics, foldables, SAF import/export, 120Hz, Drive sync
|       `-- wasmJsMain/  # Web PWA entry point, service worker, manifest, localStorage
|-- backend/             # Node.js + Express API, SQLite migrations, SimpleFIN & push scheduler
|-- deploy/
|   `-- beta/            # Maintainer Docker beta stack
|-- docs/
|   `-- reference.md     # Architecture, schema, API routes, and implementation notes
|-- scripts/             # Build, test, APK release, and deploy helper scripts
|-- docker-compose.yml   # Self-host Docker Compose file
|-- Dockerfile           # Multi-stage wasmJs PWA + Express runtime image
|-- .env.example         # Example environment configuration
`-- README.md
```

## Building and Testing

Prerequisites for building from source:

- JDK 17+ and Android SDK (compileSdk 35) for Android and Compose Multiplatform builds
- Node.js 20.19+ for backend checks and tests
- Docker for container builds

Common build and verification commands:

```sh
# Android debug APK (copies OrbitMoney-debug.apk or OrbitBeta-debug.apk to repo root)
./gradlew :androidApp:assembleDebug

# Shared KMP / Android unit tests
./gradlew testDebugUnitTest

# WebAssembly PWA distribution (outputs to composeApp/build/dist/wasmJs/productionExecutable)
./gradlew :composeApp:wasmJsBrowserDistribution

# Backend syntax check and Node test suite
cd backend && node --check src/server.js && npm test

# Local Docker image build and startup
docker compose up --build -d
```

On Windows, helper wrappers are available in `scripts/`:

- `cmd /c scripts\check-backend.cmd` runs the backend syntax check and test suite.
- `cmd /c scripts\build-frontend.cmd` builds the `wasmJs` PWA distribution.
- `cmd /c scripts\build-apk.cmd` compiles the debug APK and uploads it to the matching GitHub pre-release (`debug-latest` or `beta-latest`; pass `-SkipUpload` to build locally only).
- `cmd /c scripts\deploy-live.cmd` and `cmd /c scripts\deploy-beta.cmd` handle on-demand Docker builds and container restarts for maintainer deployments.

GitHub Actions workflows for live and beta Docker deployments (`.github/workflows/deploy-live.yml` and `.github/workflows/deploy-beta.yml`) run on manual `workflow_dispatch` only; pushing `v*` tags publishes versioned images via `.github/workflows/publish-release-image.yml`. Self-hosters do not need GitHub Actions to run Orbit Money.

## Storage and Backups

Back up the `orbit-money-data` Docker volume before upgrading or testing schema changes. Database updates run automatically on startup from versioned SQL files in `backend/src/db/migrations/`. You can also export and restore full household backups as JSON from Settings.

All money totals are stored as integer cents in SQLite and Kotlin domain models (`Long`) rather than floating-point numbers. Percentages remain decimal percent values, and credit card APRs are stored in integer basis points.

## Further Documentation

- [docs/reference.md](docs/reference.md): Architecture details, database migrations, engine behavior, and REST API routes.
- [deploy/beta/README.md](deploy/beta/README.md): Maintainer Docker beta stack configuration.
