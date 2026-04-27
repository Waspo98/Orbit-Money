# Orbit Money Beta

This folder defines the maintainer beta Docker deployment for Orbit Money.

- Container: `orbit-money-beta`
- Compose project: `orbitmoney-beta`
- Host port: `5019`
- Container port: `5008`
- Docker volume: `orbitmoney-beta_orbit-money-beta-data`
- Suggested Cloudflare hostname: `orbitbeta.overbay.app`

The beta deployment builds from the repository root and uses the root `.env`
for shared secrets. It overrides:

- `AUTH_PROVIDER=local`, `ADMIN_USERNAME=admin`, and `ADMIN_PASSWORD=admin` so the seeded demo beta is easy to review.
- `SESSION_NAME=orbit_beta.sid` so local beta and production browser sessions do not collide.
- `SEED_DEMO_DATA=1` so a fresh beta volume starts with demo data.

Beta is Docker-only. Do not run beta through Vite, a local preview server, or a
`start-beta` helper. The supported path always rebuilds and restarts the
`orbit-money-beta` Docker container.

Run from the project root:

```bat
scripts\deploy-beta.cmd
```

Or run the underlying maintainer script from this folder:

```bat
deploy-beta.cmd
```

That script ultimately runs:

```bat
docker compose -f "deploy\beta\docker-compose.yml" -p orbitmoney-beta up --build -d
```

After startup, test locally:

```text
http://localhost:5019
http://localhost:5019/api/health
```

Before deploying a frontend change to beta, verify the production build from
the repository root:

```bat
cmd /c scripts\build-frontend.cmd
```

For Cloudflare Tunnel, point `orbitbeta.overbay.app` at:

```text
http://localhost:5019
```
