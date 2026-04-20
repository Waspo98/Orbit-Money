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
- Use the bundled Windows Node runtime when system `npm` is unavailable:
  `.\.tools\node-v20.20.2-win-x64\npm.cmd`.
- After frontend changes, run `cmd /c scripts\build-frontend.cmd` from the repo root
  or `npm run build` in `frontend` with the bundled Node runtime on PATH.
- After backend changes, run a syntax smoke check with the bundled Node runtime
  (`.\.tools\node-v20.20.2-win-x64\node.exe --check backend\src\server.js`)
  plus any route/service-specific check that exists.
- For schema changes, add a new migration; never edit an applied migration.

## Frontend conventions
- Reuse `frontend/src/components/PageHero.jsx` and its exported
  `useMorphingPageHero` hook for morphing page headers; do not copy the
  scroll/resize measurement code into pages.
- Keep primary navigation data in `BottomTabs.jsx`; `DesktopSidebar.jsx`
  imports it so mobile and desktop stay in sync.
- Use `AnimatedModal`, `DropdownMenu`, `FilterSheet`, `MoreSheet`, and
  `SyncErrorBanner` rather than recreating equivalent overlays or menus.
- Use `useAppDialog` from `frontend/src/components/AppDialog.jsx` for alert
  and confirmation flows; avoid native `alert()` / `confirm()` in page code.
- Keep route-level pages focused on data loading and composition. Move shared
  display primitives into `frontend/src/components` once a pattern appears in
  two places.

## Backend conventions
- Validate and normalize route inputs at the route boundary before writing to
  SQLite.
- Preserve original/edited transaction provenance. User edits write to
  `edited_*` fields with `source='user'`; rules and system-derived edits must
  not mutate original imported values.
- Keep account, transaction, budget, and rule behavior compatible with the
  existing SQLite migrations and Docker volume.

## Done when
- Build passes
- Existing features still work
- Mobile layout still works well
- Docs are updated
