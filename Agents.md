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

## Done when
- Build passes
- Existing features still work
- Mobile layout still works well
- Docs are updated