# Phone UX review and redesign proposals (tv-shell touch mode)

Review date: 2026-11-17. Scope: phones in tv-shell touch mode, portrait (≤640 px)
and landscape. Desktop, TV and Electron are out of scope and must stay unchanged.
No product code was changed for this review.

## 1. Setup

- **Device:** phone AVD `LisBora_API37` (Pixel 8, 1080×2400 at 420 dpi, API 37).
  The page is 411×840 CSS px in portrait and 864×359 in landscape, between the
  system bars.
- **Build:** debug APK from `c7b660eb9`. Its web bundle is the
  `dist/apps/web` build that includes `18b154d4a`, the last apps/libs commit.
- **Data:** synthetic only.
  - `tv-shell/devserver/mock-provider.mjs --port 18080`: M3U `sample.m3u`, with a
    playable clip.
  - `apps/xtream-mock-server` (3211): `marketing`/`marketing`, a fictional catalogue
    with local artwork and short EPG.
  - `apps/stalker-mock-server` (3210): `portal.php` with the marketing-demo MAC
    `00:1A:79:00:00:07`.
  - All three ports go through `adb reverse`.
- **Not exercised:** the Xtream live streams in the marketing set don't decode in
  the WebView, so the Xtream player shows its error card. Playback screens use the
  Stalker and M3U clips. The marketing EPG had no programme at the current time in
  the multi-channel guide, so its programme-search dropdown is reviewed from code
  only.
- **Screenshots:** outside the repository, under
  `C:\Users\mrttm\iptvnator-ux-review\2026-11-17\` (`<root>` below).
  - `before/`: the current app. `p-` is portrait, `l-` landscape; `x` Xtream, `st`
    Stalker, `m3u` M3U.
  - `mockups/`: the proposals, made by injecting prototype CSS and DOM into the
    running WebView.
  - `css/`: the CSS (and DOM script) of each mockup.
  - `scripts/`: the scratch driver.
- **Re-running:** `scripts/ux.mjs` drives Playwright `_android` against
  `emulator-5554` only; screenshots come from `adb exec-out screencap`. Run
  `node scripts/ux.mjs scripts/steps/<step>.mjs` from the repo root. `mocks.mjs`
  holds the prototype styles.

## 2. Screen inventory

| Screen | Portrait | Landscape |
|---|---|---|
| Home | `before/p-home.png`, `p-home-dark.png` | `before/l-home.png` |
| Sources | `before/p-sources.png` | — |
| Add playlist | `before/p-add-dialog-picker.png`, `p-add-dialog-xtream.png` | `before/l-add-dialog.png` |
| Movies / detail | `before/p-x-movies.png`, `p-x-movie-detail.png` | `before/l-x-movies.png`, `l-x-movie-detail.png`, `l-x-movie-inline-player.png` |
| Series / detail | `before/p-x-series.png`, `p-x-series-detail.png`, `p-x-series-episodes.png` | — |
| Xtream Live | `before/p-x-live-idle.png`, `p-x-live-drawer.png`, `p-x-live-category.png`, `p-x-live-playing.png` | — |
| In-category search | `before/p-x-live-search-scoped.png`, `p-x-live-search-scoped-nokbd.png` | — |
| Search + programme guide | `before/p-x-live-search-playing-epg.png`, `p-st-live-search-playing-epg.png` | — |
| Multi-channel guide | `before/p-x-guide.png`, `p-x-guide-search.png` | — |
| Stalker Live | `before/p-st-live-idle.png`, `p-st-live-list.png`, `p-st-live-playing.png` | `before/l-st-live-list.png`, `l-st-live-playing.png` |
| M3U list / player | `before/p-m3u-list.png`, `p-m3u-playing.png`, `p-m3u-controls.png`, `p-player-settings-sheet.png` | — |
| Fullscreen | — | `before/l-fullscreen.png`, `l-fullscreen-controls.png` |
| Settings | `before/p-settings.png`, `p-settings-drawer.png`, `p-settings-playback.png` | `before/l-settings.png` |
| Command palette | `before/p-command-palette.png` | `before/l-command-palette.png` |
| Global favorites | `before/p-global-favorites.png` | — |

**What already works well:**
- The category drawer, movie and series detail pages, posters and episode rows.
- Fullscreen: landscape with the bars hidden.
- The add-playlist form fields, and the settings pages' own layout.

Measured sizes below come from `getBoundingClientRect` on the AVD.

## 3. Findings by severity

### Critical
- **C1. Landscape live leaves the video an 81×123 px column.** The rail (60 px), the
  in-flow categories panel (323 px) and the channel list (400 px) take the whole
  864 px width. Nothing applies between 641 px and a short landscape height.
  - Evidence: `before/l-st-live-playing.png`.
  - Code: `workspace-shell.component.scss` (≤640 px only), `_portal-layout.scss`.
- **C2. Portrait live while playing gives the video 115 px.** On Xtream and Stalker
  the list keeps a fixed 353 px (42vh) even with 2 results; with 2 rows, 220 px of
  it is blank. The inline guide takes 264 px. The player error card is cut off at
  that height. This is request 2c.
  - Evidence: `before/p-x-live-playing.png`, `p-x-live-search-playing-epg.png`,
    `p-st-live-search-playing-epg.png`.
  - Code: `_portal-layout.scss:199-278`, `live-stream-layout.component.scss:146-173`.

### High
- **H1. The top icon bar.** About 10 unlabeled icons share a 52 px strip that
  scrolls sideways.
  - Advanced search sits off-screen, and Settings is pinned over a gradient.
  - The *Recently added* icon (`new_releases`) reads as a warning badge.
  - With the 56 px header, 108 px of chrome sits above every page.
  - Evidence: `before/p-x-movies.png`, `p-sources.png`. This is request 2a.
- **H2. On a phone you can't clear the category scope from the search.** The
  dismissible scope chip is `display: none` at ≤640 px (header scss :450-453). Only
  the list title says "Newsroom". To search all categories you open the drawer and
  tap the selected category again. This is request 2b.
  - Evidence: `before/p-x-live-search-scoped.png`, `p-x-live-search-scoped-nokbd.png`.
- **H3. The header search field is 60–150 px wide.** It sits between the switcher
  (140 px), the Add button and route actions. The placeholder truncates to
  "Search in t", "Filter this s", or "Sea" on Live, where the grid toggle takes a slot.
- **H4. Landscape gets the desktop chrome.** This is the known issue 3a.
  - `Ctrl+K` badges, the keyboard-shortcuts button, and the in-flow categories panel.
  - The vertical rail is 507 px tall on a 359 px screen, so Search and Settings
    need a scroll to reach.
  - Evidence: `before/l-x-movies.png`, `l-home.png`, `l-x-movie-detail.png`.
- **H5. The video player setting is hard to find.** Settings opens on General with
  the section list hidden in the drawer. The setting is drawer → Playback → first
  row. The player's own settings sheet (subtitles, speed) has no player choice.
  This is the known issue 3b.
  - Evidence: `before/p-settings.png`, `p-settings-drawer.png`,
    `p-player-settings-sheet.png`.
- **H6. The multi-channel guide is built for a keyboard and a wide screen.**
  - A keyboard legend runs along the bottom ("↑ ↓ channel · ← → programme · Enter
    play · I details"), and Close guide carries an `Esc` key cap.
  - The toolbar takes 4 rows (about 170 px).
  - The 232 px channel column leaves about 180 px of programme lane.
  - The programme-search results are a 420 px / 50vh absolute dropdown with no
    phone rule (`epg-guide.component.scss:166-184`, from code). On a phone it
    covers the grid. This is the second surface that matches 2c.
  - Evidence: `before/p-x-guide.png`, `p-x-guide-search.png`.

### Medium
- **M1. The command palette.**
  - It shows a `⌘K` key cap on Android.
  - Two-column rows truncate both columns at 411 px.
  - The soft keyboard covers most results; in landscape one row is left.
  - The bolt icon that opens it sits in every search field and doesn't explain itself.
  - Evidence: `before/p-command-palette.png`, `l-command-palette.png`.
- **M2. In landscape, the movie inline player pushes the video below the fold.** A
  "Now playing" title and actions sit in a narrow column above it.
  - Evidence: `before/l-x-movie-inline-player.png`; also the `min-height: 320px`
    vs `70vh` rule in `portal-inline-player.component.scss:93-111`.
- **M3. The M3U player box is 366 px tall for a 231 px 16:9 picture.** That's
  135 px of letterbox, from the 50/50 split in `video-player.component.scss:116-148`.
  - Evidence: `before/p-m3u-playing.png`.
- **M4. The "System" theme ignores Android dark mode in the shell.** With
  `cmd uimode night yes` the page stays `light-theme`; choosing *Dark* works. The
  WebView reports a light `prefers-color-scheme`. This is a tv-shell (activity
  theme / WebView) fix.
  - Evidence: `before/p-home-dark.png`, taken with *Dark* chosen by hand.
- **M5. The dashboard hero labels a played Stalker live channel "MOVIE · CONTINUE
  WATCHING".** Seen once; needs triage (upstream).
  - Evidence: `before/p-home-dark.png`, `l-home.png`.
- **M6. The add-playlist footer stacks Clear, Cancel, Test and Add as full-width
  rows.** In landscape the actions are below the fold.
  - Evidence: `before/p-add-dialog-xtream.png`, `l-add-dialog.png`.
- **M7. The per-channel guide toolbar truncates.** The channel and programme names
  shrink to "Aur… Mornir". The "Schedule only" notice repeats in 50 px on every
  channel.
  - Evidence: `before/p-st-live-playing.png`.

### Low
- **L1. Sticky hover:** a row keeps its hover fill after a tap or scroll
  (`before/p-m3u-list.png`, row 4).
- **L2. The guide's player strip crops the error card** to "Copy the URL…"
  (`before/p-x-guide.png`).
- **L3. A volume slider in phone fullscreen** duplicates the hardware keys
  (`before/l-fullscreen-controls.png`).
- **L4. Stray `599px` breakpoints** sit next to the 640 px convention
  (`channel-list-container.component.scss:67-75`,
  `series-playback-navigation-controls.component.scss:51`). From code.

## 4. Proposals

Gating rules for every proposal:
- **Portrait:** `@media (max-width: 640px)`, the existing convention.
- **Phone landscape:** `@media (pointer: coarse) and (max-height: 500px)`. Android
  TV reports `pointer: none`, so TV never matches. Don't use `(hover: none)` alone,
  which TV also matches.
- **Fork-only behaviour:** gated in TS on
  `RuntimeCapabilitiesService.shell?.mode === 'touch'` (`826dfaa5a`). Never the
  `tv-shell-touch` class.

The shell (rail, header) is in the lazy `/workspace` chunk, so its component CSS
doesn't count toward `renderer.initialBytes`. Nothing below needs a new rule in the
global `styles.scss`.

### P1. Bottom navigation with a grouped Play button (2a, H1)

| | Option A: speed dial | Option B: bar morphs in place |
|---|---|---|
| Before | `before/p-x-movies.png` | same |
| After | `mockups/2a-A-bottom-nav-closed.png`, `2a-A-speed-dial-open.png`, dark: `2a-A-bottom-nav-dark-home.png` | `mockups/2a-B-bar-morph-open.png` |
| Interaction | A raised centre **Play** button shows the current section's icon and label. A tap fans out Movies / Live TV / Series over a scrim. Below them sit smaller Recently added and Search chips, and Radio for Stalker. | A tap on Play swaps the bar's items for ✕ + the three labelled sections. Choosing one restores the bar. |
| Pros | One tap to see all sections. The current section is always visible on the button. Room for the provider's extra links. | No overlay. Stays in the thumb zone. |
| Cons | Overlay and scrim; a new pattern for the app. | Secondary links (Recently added, Search, Radio) don't fit. The bar changes meaning under the thumb. |

- **Bar contents (shared):** Home · Sources · **Play** · Favorites · Recent.
  Settings moves to a gear in the header (see P2).
- **Play with no active source:** it opens Sources / Add.
- **M3U:** Play groups All channels and Groups.

**Recommendation:** A.

**Implementation:**
- A new `workspace-shell-bottom-nav` component in
  `libs/workspace/shell/feature`, rendered by the shell inside
  `@defer (when isPhoneLayout())`, so its CSS and JS are a separate chunk loaded
  only on phones.
- It reuses `WorkspaceShellRouteStateService` (`workspaceLinks`,
  `primaryContextLinks`, `railContext`, which already falls back to the active
  playlist).
- The rail gets `display: none` at ≤640 px.
- Guideline change: "Rails become rows" → "the rail becomes a bottom bar" in
  `iptvnator-ui-guidelines.md` and `workspace-shell.md`.

**Upstream:** reverses a documented upstream pattern, so open an issue first;
fork-only until the maintainer agrees (it could also be gated on `shell.mode`).
**Effort:** L.

### P2. Where search lives once the bar moves (with 2b, H2, H3)

| | Option A: header icon that expands | Option B: search tab in the bottom bar |
|---|---|---|
| Before | `before/p-x-live-search-scoped-nokbd.png` | same |
| After | `mockups/search-A-2b-A-expanded-search-scope-chip.png` (closed state: header in `2a-A-bottom-nav-closed.png`) | `mockups/search-B-2b-B-search-tab-scope-toggle.png` |
| Interaction | The header shows ☰ · full-width switcher · 🔍 · + · ⚙. 🔍 expands the existing route-aware field over the header, with the scope chip visible and dismissible. | Bar: Home · Sources · Play · **Search** · Settings. The tab opens a search screen: a full-width field, then a scope toggle, then results. |
| Keeps | Today's per-route behaviour (local filter on Live/Movies, remote search on the search route). | Search is one tap from anywhere. |
| Costs | One extra tap to start typing. | Favorites and Recent lose their tabs. The tab needs its own route semantics (`…/search` exists for Xtream and Stalker, not M3U). |

**Recommendation:** A. It keeps the app's search contract and fits P1-A's bar.
**Upstream:** the expanding field is upstreamable on its own at ≤640 px (it also
fixes H3). **Effort:** M.

### P3. Clearing the category scope (2b, H2)

| | Option A: dismissible chip + "Search all categories" row | Option B: scope toggle with counts |
|---|---|---|
| After | `mockups/search-A-2b-A-expanded-search-scope-chip.png` | `mockups/search-B-2b-B-search-tab-scope-toggle.png` (`Newsroom · 2` / `All categories · 6`) |
| How | Stop hiding `.search-chip--scope` at ≤640 px (truncate its label). While a term is active, add a row above the results, "2 results in Newsroom · Search all categories →", calling the existing `clearCategoryScope()`. The same action goes in the empty state. | A segmented control under the header while typing. Counts for all categories come from the loaded store (Xtream and Stalker hold the full list for local filtering). |
| Effort | S (CSS + one template row) | M (counts per mode, Stalker ITV/radio scope only in the store) |

**Recommendation:** A now; B only if P2-B is chosen. **Upstream:** A is very
likely accepted (it restores an existing control on phones).

### P4. Search results vs a channel's programme guide (2c, C2, H6, M7)

| | Option A: cap the list, give the video 16:9 | Option B: player on top + Channels / Guide tabs |
|---|---|---|
| Before | `before/p-st-live-search-playing-epg.png` (list 353 px, video 115 px, guide 264 px) | same |
| After | `mockups/2c-A-results-capped-video-16x9.png` (list 252 px, video 231 px, guide 237 px) | `mockups/2c-B-player-top-tabs-channels.png`, `2c-B-player-top-tabs-guide.png` |
| How | While a channel plays at ≤640 px: list `height: min(content, 30vh)`; `.video-player` gets `aspect-ratio: 16/9` as its floor; the guide takes the rest. Same rule in M3U (fixes M3). | While a channel plays: video first at 16:9, then a tab bar, then one pane, either the result list or the guide. Idle state is unchanged (list fills the screen). |
| Pros | CSS only, in the shared `live-layout` mixin and the M3U stylesheet. | The standard phone video layout. Each pane gets about 480 px. Nothing is starved. |
| Cons | Three panes still share 732 px, and guide cards stay cramped. | Template change in three live layouts (Xtream, Stalker, M3U); tab state to keep. |

**Recommendation:** A first (small, upstreamable), then B as the phone layout.

**Multi-channel guide (H6), alongside either option:**
- at ≤640 px, show the programme-search results as a full-width sheet capped at
  40vh;
- hide the keyboard legend and the `Esc` key cap on `(pointer: coarse)`;
- narrow the channel column (logo + number) at ≤640 px;
- collapse the toolbar's second row behind a filter button.

**Upstream:** likely accepted (phone-width rules, no product change). **Effort:** A S,
B M–L, guide M.

### P5. Phone landscape (3a, C1, H4, M2)

| | Option A: compact landscape | Option B: portrait bottom bar in landscape |
|---|---|---|
| Before | `before/l-st-live-playing.png` (video 81×123 px) | same |
| After | `mockups/3a-A-compact-landscape.png` (video 484×251 px) | `mockups/3a-B-bottom-bar-landscape.png` (video 544×59 px) |
| How | On `(pointer: coarse) and (max-height: 500px)`: | Reuse P1's bar at any coarse, short viewport. |
| | • narrow rail with the same grouped Play (6 items fit in 359 px); | |
| | • categories become the drawer, as on phones; | |
| | • hide `Ctrl+K` and the shortcuts button; | |
| | • list 320 px beside the video; | |
| | • guide collapsed to its 52 px bar by default. | |
| Verdict | Recommended. | Rejected: 64 px of bar on a 359 px screen starves every page. |

- **Small piece that can ship first:** hide the `Ctrl+K` text, the shortcuts button
  and the `⌘K` palette key cap on `(pointer: coarse)` (fixes H4's hints and M1).
- **M2 fix:** let `.player-shell__viewport` use its 220 px floor on short screens.
- **Upstream:** hints and M2 are very likely accepted. The compact layout is likely,
  because it uses a media query any touch phone benefits from, including the PWA.
- **Effort:** hints S, compact layout M.

### P6. Finding the video player setting (3b, H5)

| | Option A: settings index on phones | Option B: section tab strip |
|---|---|---|
| Before | `before/p-settings.png`, `p-settings-drawer.png` | same |
| After | `mockups/3b-A-settings-index.png` | `mockups/3b-B-settings-tab-strip.png` |
| How | At ≤640 px, `/workspace/settings` shows the section list (icon, name, one-line summary, current player as a value) instead of redirecting to General. Each section opens with Back to the list. The drawer stays for deep links. | A horizontally scrolling chip strip of sections at the top of every settings page. |
| Pros | The standard Android/iOS settings pattern. "Video player" is visible on the first screen. | Reachable from every section. |
| Cons | A routing change: the redirect becomes phone-aware. | Ten chips scroll sideways, so Playback is often off-screen. It duplicates the drawer. |

- **Add-on C, recommended with either:** a "Video player" row at the top of the
  player's own settings sheet (`mockups/3b-C-player-sheet-video-player-row.png`).
  The default player is chosen where the problem shows.
- **Upstream:** A and C are likely accepted.
- **Fork-only alternative:** surface the player choice in touch mode via
  `shell.mode` (e.g. on first playback error, next to the external-player offer).
- **Effort:** A M, B S, C S–M.

### Other fixes (no options)

| Finding | Fix | Upstream? | Effort |
|---|---|---|---|
| M4 System theme ignores Android dark mode | Make the shell activity theme follow night mode (DayNight parent) so the WebView reports `prefers-color-scheme: dark`. Check `algorithmicDarkening` stays off. | tv-shell, upstreamable | S |
| M5 Live channel labelled MOVIE in the hero | Triage the recent-item type for Stalker ITV. | Upstream bug report | S |
| M6 Add-playlist footer | At ≤480 px: Add full width; Test, Cancel, Clear on one row (or Clear in an overflow). In landscape, a sticky footer. | Likely | S |
| L1 Sticky hover | Wrap row hover fills in `@media (hover: hover)`. | Likely | S |
| L2 Cropped error card | Compact error variant in the guide strip. | Likely | S |
| L4 `599px` breakpoints | Align to 640 px. | Likely | S |

## 5. Decisions (2026-11-17)

1. **Bottom bar:** yes, with Play as a **speed dial** (P1-A).
2. **Bar contents:** Home · Sources · Play · Favorites · Recent. Settings becomes a
   gear in the header.
3. **Search placement:** a header search icon that expands into the route-aware
   field (P2-A).
4. **Category scope:** the dismissible scope chip inside the expanded search, plus
   P3-B's toggle under it ("Newsroom · 2 | All categories · 6"). If counting results
   across all categories is expensive for a provider, that provider gets P3-A's
   "Search all categories" link instead, and the cost is reported.
5. **Live layout while playing:** go straight to **P4-B** on phones: player on top,
   then Channels / Guide tabs. P4-A is dropped.
6. **Landscape:** compact landscape (P5-A). P5-B is rejected.
7. **Settings:** the section index on phones (P6-A), plus the "Video player" row in
   the player's settings sheet (add-on C).
8. **Gating:** media queries for every upstreamable batch. The shell touch
   capability (`shell.mode === 'touch'`) gates only the bottom bar and the search
   placement.
9. **Upstream:** the user opens an upstream issue with the mockups. Work doesn't
   wait for it.

## 6. Implementation batches

One feature per commit. Each batch:
- runs the four gates and `pnpm run perf:initial-bytes:check`;
- adds a `.changes/` note when user-visible;
- updates the Phone Layout section of `iptvnator-ui-guidelines.md` when it changes a
  pattern;
- re-captures its screens with `scripts/`, on the phone AVD and on the tablet for
  the shared rules.

`mobile-layout.e2e.ts` gets real landscape sizes (844×390, 915×412 with
`hasTouch`), since today's 640×360 "landscape" is inside the phone rule.

| # | Batch | Items | Upstream | Size |
|---|---|---|---|---|
| 1 | Touch hints | Hide `Ctrl+K`, the shortcuts button, the palette `⌘K`, the guide legend and `Esc` key caps on `(pointer: coarse)`; real-landscape E2E | likely | S |
| 2 | Scope on phones | Decision 4: the "Newsroom · 2 \| All categories · 6" toggle under the header while a scoped search is active (or the "Search all categories" link where counting is expensive). The chip inside the expanded field comes with batch 7 | likely | S–M |
| 3 | Live tabs | Decision 5 (P4-B): while a channel plays at ≤640 px, player on top at 16:9, then Channels / Guide tabs, one pane at a time (Xtream, Stalker, M3U) | likely | M–L |
| 4 | Settings | P6-A index on phones + add-on C player-sheet row | likely | M |
| 5 | Small fixes, one commit each | M2 inline player, M6 dialog footer, L1 sticky hover, L2 error card, L4 breakpoints, M4 System theme in the shell (tv-shell), M5 live channel labelled MOVIE on the home hero | likely | S each |
| 6 | Bottom bar | P1-A speed dial (deferred phone-only component) + header gear; gated on the shell touch capability | issue first | L |
| 7 | Search placement | P2-A expanding header search with the scope chip (needs 6); gated on the shell touch capability | maybe | M |
| 8 | Compact landscape | P5-A (rail Play grouping shares 6's model) | likely | M |

Batches 1–5 come first. The guide's phone rules from P4 (search sheet, narrower
channel column, toolbar) move to batch 3 if they fit, otherwise to a later batch.

## 7. Implementation status (2026-11-17)

Batches 1–5 are done, one commit per feature. Before/after captures from the
phone AVD are in `<root>/after/` (`b1-` … `b5f-`).

| Batch | Commit | Result |
|---|---|---|
| 1 Touch hints | `bbed24c6d` | `Ctrl+K`, the shortcuts button and key caps hidden on `(pointer: coarse)`; E2E at 844×390 and 915×412 |
| 2 Scope on phones | `4e0409080` | Xtream toggle "Newsroom · 2 \| All categories · 6", both ways; Stalker gets the "All categories" link (its VOD and series search runs on the portal, so an all-category count would cost a request per search) |
| 3 Live tabs | `01bac7135` | Player on top at 16:9 with Channels / Programme guide tabs (Xtream, Stalker, M3U); playing video 115 → 231px |
| 4 Settings | `ce648960b` | Section list on phones with the current player on the Playback row; "Video player" row in the player's settings sheet on touch screens |
| 5 System theme | `61c5b8498` | Touch mode answers the page's `prefers-color-scheme` from Android's dark theme (the WebView reports light whatever the setting); live changes included |
| 5 Live typed as movie | `50412a447` | Stalker entries saved from live or radio are typed live (the hero read "MOVIE") |
| 5 Landscape inline player | `9d6c0d7e2` | Video and controls fit under the header at 864×359 |
| 5 Dialog footer | `3a08d7f52` | Add-playlist footer on three rows instead of four |
| 5 Sticky hover | `bddc0cf34` | Row and method-card hover fills need `(hover: hover)` |
| 5 Guide strip error | `6e42d6956` | A player under 200px tall shows the badge and a one-line headline |
| 5 Breakpoints | `896e29780` | 599px rules moved to 640px; two dead rules removed |

- **Launch initial bytes:** 1,201,053 before batch 1, 1,200,860 after batch 5
  (−193). Batch 4 moved the settings routes out of the startup bundle (−267);
  the Stalker type fix added 74 (the normalizer is in that bundle). Still over
  the ratchet limit (1,200,301), as recorded in PROGRESS.md.
- **New file-level entries:** `@iptvnator/ui/components/phone-layout` and
  `@iptvnator/ui/components/live-phone-tabs` (barrels sit in the startup bundle,
  which splits by module).
- **Not run:** Firefox and WebKit E2E (browsers not installed here); the TV AVD
  (the shell changes only run in touch mode).
- **Batches 6–8** (bottom bar `307290cfd`, search placement `04400e524`,
  compact landscape `c670b6b84`) are done, fork-only behind the shell touch
  capability; batch 8's live-layout rule is a plain media query. Details in
  PROGRESS.md.
