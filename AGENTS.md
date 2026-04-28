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

## Product values
- Orbit Money should feel simple, friendly, and visually clear. Accuracy is
  sacred for financial calculations, so it is acceptable to add complexity when
  correctness requires it.
- Primary users are the household owners, currently Neal and his wife. Build as
  if the app may eventually have public users, but do not prematurely add public
  SaaS complexity unless the task calls for it.
- Prioritize visual clarity first, speed of entry second, and deep financial
  accuracy as a non-negotiable foundation beneath both.
- Mobile is the primary experience. Desktop is a close second. Unfolded phones
  and tablets with two-column layouts are useful but lower priority.
- Prefer focused screens with simpler choices. Hide advanced or less common
  options behind dialogs, sheets, detail views, or additional settings.
- Match the current balance of simplicity and power in the app. Do not make the
  UI feel like either a toy or a dense professional dashboard.
- Make the codebase teachable for a newer developer through clear names, local
  patterns, and small explanatory comments where they prevent confusion.

## Decision rules for agents
- Before creating any page, component, hook, service, route, schema, or helper,
  search for the existing closest primitive or pattern and use it as the default
  starting point.
- If an existing primitive is almost right but has tradeoffs, pause and explain
  the tradeoff before choosing a one-off workaround. Be specific: what would be
  awkward about reuse, what a workaround would cost, and whether improving the
  primitive would help future work.
- When code reveals an imperfect repeated pattern, flag it. The likely preferred
  direction is to improve the shared pattern immediately, but explain scope and
  risk first.
- Refactoring means changing code structure without intentionally changing user
  behavior. When already working in an area, prefer cleaning the pattern properly
  if the change is understandable, testable, and not too broad.
- Template adherence matters even when deviations work. Keep similar entities
  mechanically similar: routes should look like routes, hooks like hooks,
  components like components, and tests like tests.
- Calculation correctness is the highest priority. Treat money math, dates,
  budgets, recurring transactions, transfers, refunds, and reports as product
  logic that requires extra care and validation.
- Do not delete files, features, or large blocks of code without explicit
  approval. If something appears unused, report it first unless the user has
  already approved removal.
- Ask before touching auth, Docker, ports, volumes, database schema, migrations,
  deployment scripts, or storage/backup behavior. Explain the impact first.
- For broad changes, present a short plan before editing. Then work step by step
  and keep changes small enough to review.

## Project-specific notes
- Public app URL is handled through Cloudflare Tunnel.
- Public-facing services must stay compatible with the existing Docker/network setup.
- Update docs when behavior changes.
- If storage or backup behavior changes, flag it clearly.
- Beta is Docker-only. When asked to deploy beta, use
  `cmd /c scripts\deploy-beta.cmd` from the repo root, which delegates to
  `deploy\beta\deploy-beta.cmd` and runs Docker Compose project
  `orbitmoney-beta` on host port `5019`.
- Do not use a Vite dev server, local preview server, or `start-beta` helper for
  beta. There is intentionally no supported non-Docker beta deployment path.

## Versioning and releases
- Do not bump the app version for routine rebuilds, beta deploys, live deploys,
  or small follow-up fixes. Redeploying many times in a day should not create
  many versions.
- Treat version bumps as release milestones. Suggest a version bump when work is
  being promoted from Beta to main, when a meaningful user-facing feature lands,
  when calculation behavior changes, or when a batch of fixes is ready to be
  described as a release.
- Ask before changing version files, creating Git tags, or creating GitHub
  Releases. Include the recommended version number and why it is a patch, minor,
  or major bump.
- Prefer semantic versions for the app: patch for small fixes, minor for new
  features or meaningful improvements, and major only for public/user-visible
  breaking changes. Keep the app in `0.x` until it is considered public-ready.
- Keep visible app version labels, package versions, Git tags, and GitHub
  Releases aligned when doing an intentional release.

## Preferred workflow for agents
- Inspect before editing.
- Do not read `.env` unless the task requires debugging runtime config.
- Prefer small, reversible changes.
- Before changing auth, Docker, ports, volumes, or database storage, explain the impact first.
- Use Node.js 20+ for local validation. The public repo does not track a
  portable Node runtime; `scripts\build-frontend.cmd` and
  `scripts\check-backend.cmd` prefer a private `.tools` runtime if one exists
  and fall back to system Node/npm.
- After frontend changes, run `cmd /c scripts\build-frontend.cmd` from the repo root
  or `npm run build` in `frontend` with Node.js 20+ on PATH.
- After backend changes, run `cmd /c scripts\check-backend.cmd` from the repo
  root, plus any route/service-specific check that exists.
- For schema changes, add a new migration; never edit an applied migration.

## Frontend conventions
- Before creating new UI, first look for an existing component or CSS primitive
  that matches the interaction: page hero, modal/dialog, dropdown, selectable
  list item, sheet, button, card, pill, chart, or form field. Extend the shared
  primitive when a pattern appears in more than one place.
- Reuse `frontend/src/components/PageHero.jsx` and its exported
  `useMorphingPageHero` hook for morphing page headers; do not copy the
  scroll/resize measurement code into pages.
- Keep primary navigation data in `BottomTabs.jsx`; `DesktopSidebar.jsx`
  imports it so mobile and desktop stay in sync.
- Use `AnimatedModal`, `DropdownMenu`, `FilterSheet`, `MoreSheet`, and
  `SyncErrorBanner` rather than recreating equivalent overlays or menus.
- Use `SelectableListItem` for two-line selectable rows such as goal/category
  pickers; selected rows should use the shared green active treatment.
- Use `useAppDialog` from `frontend/src/components/AppDialog.jsx` for alert
  and confirmation flows; avoid native `alert()` / `confirm()` in page code.
- Use Title Case for visible button labels, for example `+ New Category`,
  `Add Budget`, and `Save`.
- Overlay behavior is intentional: modals/dialogs/sheets blur the app backdrop
  and lock body scroll; dropdown menus stay anchored to their trigger, do not
  blur the page, and do not lock scroll.
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
