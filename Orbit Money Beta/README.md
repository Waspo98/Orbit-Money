# Orbit Money Beta

This folder defines the beta Docker deployment for Orbit Money.

- Container: `orbit-money-beta`
- Compose project: `orbitmoney-beta`
- Host port: `5019`
- Container port: `5008`
- Docker volume: `orbitmoney-beta_orbit-money-beta-data`
- Suggested Cloudflare hostname: `orbitbeta.overbay.app`

The beta deployment builds from the parent project folder and uses the parent `.env` for shared secrets. It overrides:

- `ADMIN_USERNAME=admin` and `ADMIN_PASSWORD=admin` so the seeded demo beta is easy to review.
- `SESSION_NAME=orbit_beta.sid` so local beta and production browser sessions do not collide.
- `SEED_DEMO_DATA=1` so a fresh beta volume starts with demo data.

Run from this folder:

```bat
deploy-beta.cmd
```

Or run from the project root:

```bat
docker compose -f "Orbit Money Beta\docker-compose.yml" -p orbitmoney-beta up --build -d
```

After startup, test locally:

```text
http://localhost:5019
http://localhost:5019/api/health
```

For Cloudflare Tunnel, point `orbitbeta.overbay.app` at:

```text
http://localhost:5019
```
