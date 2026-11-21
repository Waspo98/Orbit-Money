# Orbit Money

Orbit Money is a local-first personal finance tracker for Android and the web. Run offline on Android in Local mode or self-host the Express and SQLite server in Docker (`linux/amd64` and `linux/arm64`), import Rocket Money CSV exports, and sync bank accounts through SimpleFIN.

## Features

- **Storage modes:** Run offline on-device in Local mode, or connect to a self-hosted Express and SQLite server for multi-device access and household sharing.
- **Dashboard:** Configurable cards for accounts, budget pace, top spending, monthly cash flow, biggest expenses, subscriptions, upcoming bills, goals, retirement, mortgage, MHA, and a recent transaction review queue.
- **Accounts and real estate:** Track checking, savings, credit, investment, loan, mortgage, cash, and property accounts with dated balance snapshots, linked mortgages, account merging, and SimpleFIN closed-account prompts.
- **Transactions and rules:** Search, filter, edit, split, and categorize transactions without overwriting imported bank values. Priority-ordered rules rename, categorize, ignore, or flag transfers.
- **Budgets and trends:** Set monthly category caps, inspect spending mix by category or merchant, and review 3, 6, or 12 months of income and category history.
- **Credit card tracker:** Track annual fees, statement close and due dates, utilization, benefit credits across reset cadences, welcome bonus progress, and per-person Chase 5/24 slots.
- **Goals and upcoming:** Allocate multiple asset accounts to savings goals by percent or fixed dollar amount with extra-savings ETA projections, and track recurring bills, subscriptions, and paychecks.
- **Calculators and household:** Model retirement scenarios, drawdown runway, and home buying or selling costs from household profiles and linked retirement accounts. Includes an optional Ministerial Housing Allowance (MHA) tracker with per-year tax estimates.
- **Bank sync and imports:** Import Rocket Money CSV exports, sync through SimpleFIN Bridge (server-side with AES-256-GCM encrypted credentials, or direct from the client in Local mode), and optionally enrich merchant logos with Logo.dev.
- **Auth and sharing:** Local password login, OpenID Connect (OIDC) SSO, or both, plus partner sharing by invited email with read-only or read-write access.
- **Backups and device controls:** Export and restore full JSON workspace backups, optionally sync backups to Google Drive, lock the Android app with biometrics, launch actions from Android shortcuts, and receive optional Web Push notifications.

## Platforms

- **Android (Local or Self-Hosted):** Native Compose Multiplatform app (Android 7.0+ / API 24+) supporting phones, foldables, and tablets.
  - **Public releases:** Download the release-signed APK (`OrbitMoney-vX.Y.Z.apk`) from the [Latest GitHub Release](https://github.com/Waspo98/Orbit-Money/releases/latest). Use Local mode without a server, or point the app at your Docker server URL.
  - **Pre-release channels:** Rolling debug builds (`OrbitMoney-debug.apk` under `debug-latest`) and side-by-side beta builds (`OrbitBeta-debug.apk` under `beta-latest`, application ID `app.overbay.orbit.beta`) are also published on [GitHub Releases](https://github.com/Waspo98/Orbit-Money/releases).
- **Web PWA and self-hosted server:** Multi-arch Docker image (`ghcr.io/waspo98/orbit-money:latest` for `linux/amd64` and `linux/arm64`) bundling the Express + SQLite backend with the compiled WebAssembly (`wasmJs`) PWA.

## Self-Hosting Quick Start

You do not need to clone the repository to run Orbit Money.

### 1. Download `docker-compose.yml`

```sh
mkdir orbit-money && cd orbit-money
curl -fsSLO https://raw.githubusercontent.com/Waspo98/Orbit-Money/main/docker-compose.yml
```

If you manage Docker with Portainer, Dockge, or Unraid, copy the contents of [docker-compose.yml](docker-compose.yml) directly into a new stack instead.

### 2. Set `ADMIN_PASSWORD`

Create a `.env` file in the same directory as `docker-compose.yml` (or set `ADMIN_PASSWORD` directly under `environment:` in the Compose file):

```sh
cat <<'EOF' > .env
ADMIN_USERNAME=admin
ADMIN_PASSWORD=replace-with-a-strong-password
TZ=America/Chicago
EOF
```

`SESSION_SECRET` is optional. If omitted, Orbit Money generates a 64-character hex secret on first start and persists it at `/app/data/.session_secret` inside the `orbit-money-data` volume. If you want optional integrations (SimpleFIN, OIDC SSO, Logo.dev, Web Push), grab the full template with:

```sh
curl -fsSLO https://raw.githubusercontent.com/Waspo98/Orbit-Money/main/.env.example
cp .env.example .env
```

Orbit Money refuses to start if `ADMIN_PASSWORD` or `SESSION_SECRET` still contains placeholder text from `.env.example`. Leave optional keys blank until you configure them.

### 3. Start and verify

```sh
docker compose up -d
```

Open `http://localhost:5008` in your browser, or point the Orbit Money Android app to your server URL.

Verify the container health and running version:

```sh
curl http://localhost:5008/api/health
```

Stop the container:

```sh
docker compose down
```

### Default Docker Layout

- **Image:** `ghcr.io/waspo98/orbit-money:latest` (`linux/amd64`, `linux/arm64`)
- **Container name:** `orbit-money`
- **Port:** `5008` (override the host port with `ORBIT_PORT=8080` in `.env`)
- **Volume:** `orbit-money-data` mounted at `/app/data` (`budget.db`, `sessions.db`, and `.session_secret`)

Keep the `orbit-money-data` volume name intact across upgrades so your SQLite database and generated session secret persist.

## Updating and Automatic Updates

### Manual Update

```sh
docker compose pull
docker compose up -d
```

### Image Tags

- `:latest` tracks the newest versioned GitHub release (`vX.Y.Z`).
- `:v0.8.1`, `:0.8.1`, and `:0.8` pin to a specific release or minor series.
- `:main` and `:beta` track maintainer branch builds.

### Automatic Updates with Watchtower or DIUN

Because `ghcr.io/waspo98/orbit-money:latest` is public and database migrations run automatically at startup, Watchtower (`nickfedor/watchtower`) can pull new releases and recreate the container automatically:

```yaml
services:
  watchtower:
    image: nickfedor/watchtower
    container_name: watchtower
    restart: unless-stopped
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      - WATCHTOWER_CLEANUP=true
      - WATCHTOWER_POLL_INTERVAL=86400
```

If you prefer update notifications without unattended restarts on your financial database, use [DIUN](https://crazymax.dev/diun/) or run Watchtower in monitor-only mode (`WATCHTOWER_MONITOR_ONLY=true`) and back up `orbit-money-data` before upgrading.

## Environment Variables

Required for `AUTH_PROVIDER=local` or `both`:

- `ADMIN_PASSWORD`: Local login password.
- `ADMIN_USERNAME`: Local login username (default: `admin`).

Optional and feature-specific values:

- `AUTH_PROVIDER`: `local` (default), `oidc`, or `both`.
- `SESSION_SECRET`: Cookie signing secret. Leave blank to auto-generate in `/app/data/.session_secret`.
- `SESSION_NAME`: Session cookie name (default: `connect.sid`).
- `ORBIT_PORT`: Host port mapped to container port `5008` in `docker-compose.yml` (default: `5008`).
- `TZ`: Server timezone (default: `America/Chicago`).
- `API_KEY`: Enables programmatic API access via the `X-API-Key` header. Leave blank if unused.
- `OIDC_ISSUER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_REDIRECT_URI`: Required when `AUTH_PROVIDER` is `oidc` or `both`. Legacy `AUTHENTIK_*` variable names are still read as fallbacks.
- `OIDC_SCOPES`: OIDC scopes, defaulting to `openid email profile`.
- `OIDC_LOGIN_LABEL`: Custom label for the OIDC button (default: `Log in with OIDC`).
- `SIMPLEFIN_ENCRYPTION_KEY`: 64-character hex string required before connecting SimpleFIN on the server.
- `LOGO_DEV_PUBLISHABLE_KEY`: Enables merchant logo display.
- `LOGO_DEV_SECRET_KEY`: Enables server-side brand search for manual logo overrides.
- `WEB_PUSH_PUBLIC_KEY`, `WEB_PUSH_PRIVATE_KEY`, `WEB_PUSH_SUBJECT`: Enable PWA push notifications. Generate VAPID keys with `npx web-push generate-vapid-keys`; set `WEB_PUSH_SUBJECT` to a `mailto:` or HTTPS contact URL.
- `ENABLE_SAMPLE_DATA`: Set to `1` to allow temporary sample households from the login screen. Leave `0` for normal installs.

## Auth, Sharing, and Offline Behavior

Users, households, memberships, and pending invites live in SQLite. Existing single-user databases migrate into household `1`, and new installs default to `My Household`. When using OIDC with Authentik, Authelia, Keycloak, or another provider, configure the redirect URI as:

```text
https://your-orbit-domain.example/api/auth/oidc/callback
```

New OIDC users receive their own household with default categories and settings. Household owners can invite partners by email from Settings and grant read-only or read-write access.

In Local mode, Orbit Money stores its snapshot on-device and works offline without a server. In Self-Hosted mode, both the Android app and the Web PWA cache the latest workspace snapshot locally (and the PWA service worker caches the app shell and WebAssembly bundle) so the app opens offline in read-only mode. Owner households can read cached data offline indefinitely; shared members must validate access online at least once every 7 days. Edits, syncs, imports, and admin actions require a server connection, and signing out clears cached household data.

When Web Push VAPID keys are configured, Settings can enable browser push notifications for weekly summaries, paycheck arrivals, SimpleFIN sync issues, and manual balance snapshot reminders.

## Storage and Backups

Back up the `orbit-money-data` Docker volume before upgrading or testing schema changes. Database updates run automatically on startup from versioned SQL files in `backend/src/db/migrations/`. You can also export and restore full household backups as JSON from Settings.

All money totals are stored as integer cents in SQLite and Kotlin domain models (`Long`) rather than floating-point numbers. Percentages remain decimal percent values, and credit card APRs are stored in integer basis points.

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
|   |-- beta/            # Maintainer Docker beta stack
|   `-- live/            # Maintainer Docker live stack (web_proxy + local build)
|-- docs/
|   `-- reference.md     # Architecture, schema, API routes, and implementation notes
|-- scripts/             # Build, test, APK release, and deploy helper scripts
|-- docker-compose.yml   # Standalone self-host Docker Compose file
|-- Dockerfile           # Multi-stage wasmJs PWA + multi-arch Express runtime image
|-- .env.example         # Example environment configuration
|-- LICENSE              # MIT License
`-- README.md
```

## Building from Source and Maintainer Workflows

Prerequisites for building from source:

- JDK 17+ and Android SDK (compileSdk 35) for Android and Compose Multiplatform builds
- Node.js 20.19+ for backend checks and tests
- Docker with Buildx for container builds

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

# Local Docker image build (against maintainer live or standalone compose)
docker build -t ghcr.io/waspo98/orbit-money:local .
```

On Windows, helper wrappers live in `scripts/`:

- `cmd /c scripts\check-backend.cmd` runs the backend syntax check and test suite.
- `cmd /c scripts\build-frontend.cmd` builds the `wasmJs` PWA distribution.
- `cmd /c scripts\build-apk.cmd` compiles the debug APK and uploads it to the matching GitHub pre-release (`debug-latest` or `beta-latest`; pass `-SkipUpload` to build locally only).
- `cmd /c scripts\deploy-live.cmd` and `cmd /c scripts\deploy-beta.cmd` handle on-demand maintainer builds and container restarts (`deploy/live/` and `deploy/beta/`).

GitHub Actions workflows:

- `.github/workflows/deploy-live.yml` and `.github/workflows/deploy-beta.yml` run on manual `workflow_dispatch` only.
- `.github/workflows/publish-release-image.yml` runs on published GitHub Releases (and `workflow_dispatch`) to push multi-arch (`linux/amd64`, `linux/arm64`) release tags plus `:latest`.

## Further Documentation

- [docs/reference.md](docs/reference.md): Architecture details, database migrations, engine behavior, and REST API routes.
- [deploy/live/README.md](deploy/live/README.md): Maintainer Docker live stack configuration.
- [deploy/beta/README.md](deploy/beta/README.md): Maintainer Docker beta stack configuration.
- [LICENSE](LICENSE): MIT License.
