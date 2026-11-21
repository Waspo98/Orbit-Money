# Orbit Money Beta

This folder defines the maintainer Docker beta deployment for Orbit Money.

- **Container:** `orbit-money-beta`
- **Image:** `ghcr.io/waspo98/orbit-money:beta`
- **Compose project:** `orbitmoney-beta`
- **Host port:** `5019`
- **Container port:** `5008`
- **Docker volume:** `orbitmoney-beta_orbit-money-beta-data`

The beta deploy script builds the `wasmJs` Web PWA bundle on the host, builds and pushes the beta Docker image from the checked-out `beta` branch, recreates `orbit-money-beta`, and verifies that the container version matches `backend/package.json`. It loads shared settings from the root `.env` and applies these container overrides:

- `AUTH_PROVIDER=local`, `ADMIN_USERNAME=admin`, and `ADMIN_PASSWORD=admin` for quick demo review. Do not expose this container publicly without changing those credentials or placing it behind external access controls.
- `SESSION_NAME=orbit_beta.sid` so beta and live sessions on localhost do not overwrite each other.
- `SEED_DEMO_DATA=1` so a fresh beta volume starts with demo accounts and transactions.

Beta runs only through Docker rather than a separate local dev server. Run from the repository root:

```bat
cmd /c scripts\deploy-beta.cmd
```

Or run `deploy-beta.cmd` directly from this folder. The script runs:

```bat
call scripts\build-frontend.cmd
docker build -t ghcr.io/waspo98/orbit-money:beta -t ghcr.io/waspo98/orbit-money:beta-<sha> .
docker push ghcr.io/waspo98/orbit-money:beta
docker push ghcr.io/waspo98/orbit-money:beta-<sha>
docker compose -f deploy\beta\docker-compose.yml -p orbitmoney-beta up -d --no-deps --force-recreate orbit-money-beta
```

The script does not pull from GHCR. Pushing images requires Docker push access to `ghcr.io/waspo98/orbit-money` via `GITHUB_TOKEN` or an active Docker registry login; if push fails, the script warns and still recreates the local beta container.

After startup, verify locally:

```text
http://localhost:5019
http://localhost:5019/api/health
```

Before deploying a web UI change to beta, you can test the PWA build from the repository root:

```bat
cmd /c scripts\build-frontend.cmd
```

If routing beta through a reverse proxy or Cloudflare Tunnel, forward your beta hostname to `http://localhost:5019`.
