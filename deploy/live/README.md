# Orbit Money Live (Maintainer Stack)

This folder defines the maintainer live Docker deployment for Orbit Money on HAL 9000.

- **Container:** `orbit-money`
- **Image:** `ghcr.io/waspo98/orbit-money:main`
- **Compose project:** `orbitmoney`
- **Host port:** `5008`
- **Container port:** `5008`
- **Docker volume:** `orbit-money-data`
- **External network:** `web_proxy` (Cloudflare Tunnel ingress)

Use the root `deploy-live` helper script rather than running `docker compose` against the root `docker-compose.yml`, which is reserved for standalone public self-hosting:

```bat
cmd /c scripts\deploy-live.cmd
```

`scripts\deploy-live.cmd` checks out `main`, builds the `wasmJs` Web PWA bundle on the host, builds and pushes `ghcr.io/waspo98/orbit-money:main` and `:main-<sha>`, recreates `orbit-money`, and verifies the running version in `backend/package.json`. Public `:latest` tags are published separately from versioned releases via `.github/workflows/publish-release-image.yml`.
