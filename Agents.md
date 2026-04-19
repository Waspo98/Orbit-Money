# AGENTS.md

## Stack
- Frontend: React + Vite
- Backend: Node + Express
- Data: SQLite
- Deploy: Docker Compose on Windows
- App type: installable PWA

## Non-negotiables
- Do not remove existing features unless explicitly asked.
- Do not change ports, volume names, or auth behavior unless explicitly asked.
- Preserve Docker behavior and deployment compatibility.
- Keep mobile UX polished.
- Keep the PWA installable.

## Project-specific notes
- Public app URL is handled through Cloudflare Tunnel.
- Public-facing services must stay compatible with the existing Docker/network setup.
- Update docs when behavior changes.
- If storage or backup behavior changes, flag it clearly.

## Preferred workflow for agents
- Inspect before editing.
- Do not read `.env` unless the task requires debugging runtime config.
- Prefer small, reversible changes.
- Before changing auth, Docker, ports, volumes, or database storage, explain the impact first.
- After frontend changes, run `npm run build` in `frontend`.
- After backend changes, run the backend smoke/test command if available.
- For schema changes, add a new migration; never edit an applied migration.

## Done when
- Build passes
- Existing features still work
- Mobile layout still works well
- Docs are updated