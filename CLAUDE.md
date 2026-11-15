@AGENTS.md

## Fork: Android port

- Goal: Android phone/tablet mode in `tv-shell/` (approach A in
  `docs/android/AUDIT.md`); changes stay in `tv-shell/` where possible,
  upstreamable PRs; fork identity via a Gradle flavor. Electron and PWA builds
  must keep working.
- Read `tv-shell/AGENTS.md` before touching `tv-shell/`.
- Baseline: `pnpm run serve:frontend:pwa` and `pnpm run build:frontend:pwa`.
- Environment: Windows, PowerShell, no Visual Studio. Install with
  `pnpm install --frozen-lockfile --ignore-scripts`; never build the Electron app.
  Run bash scripts via Git Bash.
- Before every commit: `pnpm run lint`, `pnpm run test:frontend`,
  `pnpm run typecheck:web`, `pnpm run build:frontend:pwa`.
- One feature per commit; never touch unrelated files. Don't edit AGENTS.md or
  `.claude/` (upstream-owned; this fork rebases often).

### Upstream constraints on this port

- Trademark: a distributed build needs its own name (not "IPTVnator" or a
  look-alike), app ID and icon; "based on IPTVnator" is fine; keep the MIT notice.
- Detect the Android shell through a capability contract, not class or
  `window.electron` checks.
- New TS code lives in Nx projects with `scope:*`/`domain:*`/`type:*` tags and
  `@iptvnator/*` aliases; files under 300 lines (hard limit 400).
- Preserve persisted browser data; synthetic data only in fixtures, docs, commits.
- Use the redacting logger; never log credentials or portal details.
- User-visible changes need a `.changes/` note (`pnpm run release:notes:validate`).
- This file: max 30 lines / 2 KiB, one `@AGENTS.md` import (`pnpm run agents:validate`).
