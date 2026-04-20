# Server Info — The Overbay HAL 9000

## System Overview

| Property | Value |
|---|---|
| **OS** | Windows 10 Pro 64-bit (Windows 11 upgrade planned before RAID migration) |
| **Location** | Urbana, IL — Timezone: `America/Chicago` |
| **LAN IP** | `192.168.86.200` (static) |
| **Docker Root** | `C:\Docker\Compose\` |
| **Scripts** | `C:\Scripts\` (`DailyBackup.ps1`, `rclone.exe` v1.72) |
| **CPU** | AMD Ryzen 5 3600X |
| **RAM** | 16 GB DDR4 |
| **GPU** | AMD Radeon RX 5700 XT (8 GB) |
| **Motherboard** | ASUS ROG STRIX B450-F GAMING |

## Storage

| Drive | Hardware | Role | Docker Mount |
|---|---|---|---|
| **C:\** | Crucial NVMe 1 TB | OS, Docker configs, AppData, RetroArch (`C:\RetroArch`) | — |
| **D:\** | SanDisk SSD 240 GB | "The Scratch Disk" — Plex transcode (`D:\PlexTranscode`), torrent staging (`D:\Downloads`) | `/ssd_temp` |
| **B:\** | Seagate 8 TB | Main data: media libraries (`B:\Plex 2.0\`), torrents (`B:\Downloads`), books (`B:\Books`), ROMs (`B:\ROMs`) | `/B`, `/books`, `/data/roms` |
| **E:\** | WD Internal 8 TB | Media storage | `/E` |
| **F:\** | WD External 8 TB | Media storage / RAID staging | `/F` |
| **[Offline]** | Seagate 8 TB | Waiting to be installed (RAID 5 target with B, E, F = four 8 TB drives → ~24 TB usable) | — |

## Networking

### Architecture
Public HTTP/S services are exposed via **Cloudflare Tunnel** under the `overbay.app` domain. All tunnel-facing containers join the **`web_proxy`** external Docker network and are routed by container name.

The media automation stack uses a "split stack" design: download and indexer containers route through **Gluetun** (PIA VPN, Chicago endpoint, local network access `192.168.86.0/24`), while manager containers (Sonarr, Radarr, Lidarr) run on the standard Docker bridge for reliable metadata lookups. Managers reach VPN-routed services via the host IP `192.168.86.200` (not localhost).

Any service piggybacking on Gluetun uses `network_mode: "service:gluetun"` and is accessed through Gluetun's published ports.

### Remote Access
**Primary:** WireGuard VPN (`wg-easy` container) + Windows Remote Desktop. Clients join the `192.168.86.0/24` subnet via tunnel. RDP is never publicly exposed. Split tunnel (`WG_ALLOWED_IPS=192.168.86.0/24`) routes only home-subnet traffic through the VPN — all other internet traffic is unaffected. UDP `51820` is forwarded at the router. WireGuard runs as a Windows service on client machines for pre-login connectivity.

**Fallback:** Parsec (System Service / Shared Mode).

### Key Networking Notes
- **WinRM:** Always use `-SkipNetworkProfileCheck -Force` — Hyper-V virtual adapters (Docker Desktop, WSL2) are classified as Public and can't be reclassified.
- **Google Wifi DNS:** Adding a secondary non-Pi-hole DNS undermines blocking. If Pi-hole is deployed, it must be sole DNS. Per-device DNS visibility is lost (router proxies all queries).

## Service Registry

All Docker services use Compose v2 with per-service subdirectories under `C:\Docker\Compose\`. Compose convention: relative paths for configs (`./config:/config`), absolute drive letters for content (`B:\:/B`). PIA VPN credentials stored in `.env` files, never in compose files.

### Dashboard
| Service | URL | Port | Image | Compose Path | Network | Notes |
|---|---|---|---|---|---|---|
| **Dashy** | `dashboard.overbay.app` | 4000 | — | `C:\Docker\Compose\Dashy\` | `web_proxy` | Landing page / link dashboard |
| **Docker Dashboard** | `dash.overbay.app` | 5057 | Custom (Python/Flask) | `C:\Docker\Compose\Docker Dashboard\` | `web_proxy` | Container management, update checking, one-click pull-and-restart. HTTP Basic Auth. Connects via `tcp://host.docker.internal:2375`. HAL 9000 theme. |

### Media Streaming
| Service | Port | Type | Notes |
|---|---|---|---|
| **Plex** | — | Native Windows install | Transcode dir: `D:\PlexTranscode`. TMDB agent recommended for Pokémon (reflects US regional seasons). |
| **Jellyfin** | — | Native Windows install | — |

### Media Automation (Arr Stack)

**VPN-routed (via Gluetun):**

| Service | Port | Image | Purpose |
|---|---|---|---|
| **qBittorrent** | 8080 | `linuxserver/qbittorrent` | Downloader. Incomplete → `/ssd_temp`, complete → `/B/Downloads` |
| **Prowlarr** | 9696 | `linuxserver/prowlarr` | Indexer manager |
| **FlareSolverr** | 8191 | `flaresolverr/flaresolverr` | Cloudflare bypass for Prowlarr |
| **Shelfarr** | — | — | Book acquisition (Anna's Archive). Piggybacked on Gluetun via `network_mode: "service:gluetun"` |

**Standard network (Docker bridge):**

| Service | Port | Image | Purpose |
|---|---|---|---|
| **Sonarr** | 8989 | `linuxserver/sonarr` | TV management. Note: cannot consume TVDB alternate orderings; use Standard series type + TheXEM for scene-to-TVDB mapping. |
| **Radarr** | 7878 | `linuxserver/radarr` | Movie management |
| **Lidarr** | 8686 | `linuxserver/lidarr` | Music management |

Compose path for entire Arr stack: `C:\Docker\Compose\media\` (single compose file with Gluetun, all Arr services, and qBittorrent).

### Books
| Service | URL | Port | Image | Compose Path | Network | Notes |
|---|---|---|---|---|---|---|
| **Grimmory** | `books.overbay.app` | 6060 | `grimmory/grimmory:latest` | `C:\Docker\Compose\BookLore\` | `web_proxy` | Ebook library browser/reader. Migrated from BookLore. DB: MariaDB sidecar. Library mount: `B:\Books` → `/books` (read-only). MariaDB dump note: `root` user is socket-auth only; dumps must use the application user credentials with `mariadb-dump`. |

**Deprecated:** BookLore (replaced by Grimmory), Calibre-Web (container exists but stopped; kept as backup).

### Photos
| Service | URL | Port | Image | Network | Notes |
|---|---|---|---|---|---|
| **Immich** | `photos.overbay.app` | 2283 | `immich` | `web_proxy` | Photo management |

### Finance
| Service | URL | Port | Container | Network | Notes |
|---|---|---|---|---|---|
| **Actual Budget** | — | 5006 | `actual_server` | `web_proxy` | **Deprecated.** Replaced by Orbit Money. Container may still exist but is no longer the primary finance tool. |

### Networking Services
| Service | Port | Image | Compose Path | Notes |
|---|---|---|---|---|
| **wg-easy** | 51821 (web UI, local only) | `ghcr.io/wg-easy/wg-easy` | `C:\Docker\Compose\wireguard\` | WireGuard VPN server + peer management. Tunnel: UDP 51820 (router-forwarded). Config: `./config:/etc/wireguard`. Web UI not exposed via Cloudflare. |

### File Sync
| Service | Port | Image | Compose Path | Notes |
|---|---|---|---|---|
| **Syncthing** | 8384 (web UI, local only) | `lscr.io/linuxserver/syncthing:latest` | `C:\Docker\Compose\Syncthing\` | Hostname: `OverbayServer`. Ports: 8384 (UI), 22000 TCP/UDP (sync), 21027 UDP (discovery). Volumes: `C:\Docker\Compose\Syncthing\config` → `/config`, `C:\RetroArch` → `/data/retroarch`, `B:\ROMs` → `/data/roms`. Syncs RetroArch saves/states across Steam Deck, AYN Thor, and server. ROMs: server = Send Only (authority), Thor = Receive Only. Standalone emulator saves (NetherSX2, MelonDS, Dolphin, Azahar) each get their own Syncthing folder under `C:\RetroArch\standalone saves\`. |

### Custom Apps
| Service | URL | Port | Stack | Compose Path | Network | Database | Notes |
|---|---|---|---|---|---|---|---|
| **Job Tracker** | `jobs.overbay.app` | 5055 | Python/Flask | `C:\Docker\Compose\Job Tracker\` | `web_proxy` | SQLite (`/data/jobs.db`, Docker volume) | Job alert scraper with Gmail SMTP alerts. See `job-tracker-reference.md` for full technical detail. |
| **Lawn Tracker** | `lawncare.overbay.app` | 8085 | Node.js/Express + React | `C:\Docker\Compose\Lawn Tracker\` | `web_proxy` | JSON files + photos (Docker named volume `lawn-data` at `/app/data`) | Seasonal lawn care tracker for Kentucky Bluegrass (Zone 6a). Session auth + API key. Multi-stage Dockerfile (build: `node:20-slim`, prod: `node:20-alpine`). iCal feed at `/api/calendar.ics`. Weather/soil temp via Open-Meteo. See `lawn-tracker-reference.md` for full technical detail. |
| **Orbit Money** | `money.overbay.app` | 5008 | Node.js/Express + React | `C:\Docker\Compose\Orbit Money\` | `web_proxy` | SQLite (`/app/data/budget.db`, Docker named volume `orbit-money-data`) | Personal finance tracker replacing Rocket Money + Actual Budget. Full feature set: Rocket Money CSV import, SimpleFIN bank sync, rules engine with original/edited value provenance, transaction search/filter/sort, monthly budgets with progress tracking, and a multi-card dashboard. PWA installable. See `Reference.md` for full technical detail. Storage was renamed from the old project-prefixed volume during the Orbit Money rename. |

## Backup

| Property | Value |
|---|---|
| **Status** | SECURE |
| **Schedule** | Weekly — Tuesdays @ 3:00 AM (Windows Scheduled Task) |
| **Script** | `C:\Scripts\DailyBackup.ps1` |
| **Tool** | Rclone + Windows Shadow Copies (VSS) |
| **Destination** | `overbay_gdrive:Overbay_Backups` (Google Drive, personal OAuth app) |

**Current backup scope:**

| Target | Method |
|---|---|
| Docker Compose configs (`C:\Docker\Compose\`) | Rclone sync |
| Plex Registry + Database/Metadata | Rclone sync (via VSS) |
| Grimmory/BookLore MariaDB | `mariadb-dump` using app user credentials |
| Job Tracker SQLite | File copy from Docker volume |
| Actual Budget data | File copy |
| Lawn Tracker data (`state.json`, `custom-tasks.json`, `photos/`) | `docker cp lawn-tracker:/app/data ./lawn-backup` |
| Orbit Money SQLite | `docker cp orbit-money:/app/data/budget.db ./budget-backup/` |

**Reminder:** After adding any new container with a database (MariaDB, SQLite, Postgres, etc. in a Docker volume), update `DailyBackup.ps1` to include a dump/export step.

## Operational Gotchas

Quick-reference for recurring pitfalls — check here before troubleshooting.

- **Docker Dashboard `--project-directory` bug:** Linux compose corrupts Windows volume path labels. Workaround: PyYAML pre-processing to rewrite relative → absolute Windows paths in a temp file.
- **Docker Dashboard `working_dir` label:** Compose v2 uses `com.docker.compose.project.working_dir`. Corrupted `/tmp` labels require case-insensitive directory scan fallback under `/compose/`.
- **Syncthing nested folders:** Cannot create a Syncthing folder inside an existing synced folder. Standalone emulator saves must live outside the RetroArch saves scope.
- **PS2 memcard sync:** Monolithic `.ps2` files → `.sync-conflict` files expected when playing on multiple devices. Resolve by keeping the most-recent-timestamp copy.
- **Sonarr + TVDB alternate orderings:** Not supported (long-requested, no near-term fix). Use Standard series type + TheXEM mapping; manual search/import for gaps.
- **rclone + MariaDB:** The `root` user in Grimmory/BookLore MariaDB is restricted to socket auth. Dumps must use the application user credentials.
- **New public-facing services:** Must join `web_proxy` network and be added to Cloudflare Tunnel config.
- **WireGuard split tunneling:** Destination-based (`WG_ALLOWED_IPS=192.168.86.0/24`). No per-app config needed — new apps route correctly automatically.
- **Orbit Money: better-sqlite3 `.iterate()`:** Using `.iterate()` inside a write transaction causes "database connection is busy." Always use `.all()`.
- **Orbit Money: SQLite `ALTER TABLE ADD COLUMN`:** Cannot use function defaults like `datetime('now')`. Use a constant default then backfill.
- **Orbit Money: SQLite integer 0 in JSX:** `{value && <Component />}` renders literal "0" when value is integer 0. Coerce with `!!value`.
- **Orbit Money: frontend verification on Windows:** use `cmd /c scripts\build-frontend.cmd` from `C:\Docker\Compose\Orbit Money`; it wires the bundled Node runtime into PATH.
- **Orbit Money: shared page hero:** reuse `frontend/src/components/PageHero.jsx` and `useMorphingPageHero` for sticky/morphing header behavior. Do not re-copy the scroll/resize measurement logic into new pages.

## Infrastructure Roadmap

**In progress:** Windows 11 upgrade → RAID 5 migration (four 8 TB drives via Windows Storage Spaces, ~24 TB usable). OS upgrade must complete before RAID — never layer upgrade risk on an active array. F:\ drive used as staging.

**On the horizon:** Pi-hole (strong candidate; single-instance with manual DNS revert procedure or secondary instance on Raspberry Pi Zero for redundancy), Pokémon Plex/Sonarr setup (TMDB agent + XEM).

**Evaluating:** Uptime Kuma, Paperless-ngx (highest-impact), Readarr, Audiobookshelf, Stirling-PDF, RomM, Mealie, Portainer (post-RAID), Netdata or Grafana+Prometheus (post-RAID).
