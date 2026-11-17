# Android phone port: tv-shell audit, Electron coupling audit, approach decision

## Context
The fork's goal (CLAUDE.md) is an Android phone and tablet app that reuses `apps/web`,
while the Electron and PWA builds keep working. CLAUDE.md currently plans a new Capacitor
shell. Upstream already ships `tv-shell/`, a Fire TV / Android TV WebView APK. It was
merged on 2026-11-07 (#1918) and the maintainer (4gray) has edited it since (#2147,
#2248, plus agent docs). The upstream download page already says of it: *"You can try it
on Android tablets. Compact phone screens still need layout work."*

This document is a read-only audit and recommendation. No code was changed. Approving
it means accepting approach A. The roadmap PRs start only on a separate request.

---

## 1. tv-shell audit

### 1.1 Shape
```
APK (org.tvshell.iptvnator, minSdk 25, targetSdk 35, Java 11, JDK 17 build)
├── ShellApp            starts TvShellServer (NanoHTTPD 2.3.1) on 127.0.0.1:43330..43334
├── MainActivity        full-screen WebView created in code → http://127.0.0.1:<port>/
│   ├── TvShellBridge   window.TvShell.postMessage (AndroidX WebMessageListener, loopback origin only)
│   ├── ExternalPlayer  ACTION_VIEW video/* intents (VLC, Kodi, MX Player…)
│   ├── WebFileChooser  SAF document picker for <input type=file>
│   └── SettingsActivity  layout width, preferred player, data reset, diagnostics, remote debugging
└── backend/ (pure-Java library, JVM-tested)
    ├── ShellSite       serves assets/www (the built PWA) + injects tv.js, viewport, app-config.js
    └── WebBackend      a Java port of the apps/web-backend route contract
```
Sizes: about 1,600 lines of app Java (MainActivity has 766), 2,800 lines of backend Java,
1,611 lines in `tv.js`, plus a devserver and tests.

### 1.2 How it serves and wraps the PWA (`ShellSite.java`, `TvShellServer.java`)
- Serves `dist/apps/web` (`pnpm nx build web --configuration=pwa`), which
  `scripts/bundle-pwa.sh` copies into `app/src/main/assets/www`. It is stored uncompressed
  in the APK.
- Edits `index.html` at serve time:
  - sets `<base href="/">`;
  - pins the viewport to `width=<960|1280|1600>` at an explicit scale;
  - adds `<script src="tvshell/tv.js" defer>` before `app-config.js`. It must be
    same-origin because the app's CSP blocks inline scripts.
- `/assets/app-config.js` is synthesized as `BACKEND_URL: "/api"`.
- Service-worker files return 404 on purpose, and the bundle script deletes them.
- Any unknown route falls back to `index.html`. Hashed chunks are cached as immutable;
  everything else is `no-store`.
- No IPTVnator source file is modified.

### 1.3 What the Java backend implements (`WebBackend.java`)
| Route | Behaviour | Parity with `apps/web-backend` |
|---|---|---|
| `GET /health`, `/config.js` | static | yes |
| `POST /provider-targets` | `{url}` → `sha256` targetId held in memory; only http(s) | partial: no private-network block, no LRU cap/expiry |
| `GET /parse` | fetch + streaming M3U parse. A line-by-line port of iptv-playlist-parser, checked against 43 fixtures from the real JS parser | yes |
| `GET /xtream` | `player_api.php` proxy, `{action,payload}`, connection-test protocol discovery (`canTryHttp`) | yes |
| `GET /stalker` | portal proxy with MAC/token/serial identity headers (`StalkerIdentity`), 15 s / 30 s `create_link` | yes |
| `POST /connectivity-guard/reset` | stub `{reset:false}` | stub, no breaker |
| `GET /parse-xml` | **501** | no (the PWA does not call it anyway) |
| `/v1/*` server storage | 404 (the "no server storage" answer) | no |
| `OPTIONS` | `ACAO: *` | yes |

Shared behaviour:
- **CORS**: there is no CORS problem by construction. The PWA and `/api` are the same
  origin, and the providers are fetched from Java (`AndroidHttpFetcher` → `HttpFetcher`).
- **Redirects**: up to 5. Authorization, Cookie, SN and Host headers are dropped when a
  redirect changes origin.
- **Size limits**: 16 KiB request bodies. Responses are capped at 1/8 of the heap (64 MB
  max), and an oversized declared length is refused with `ERR_RESPONSE_TOO_LARGE`.
- **Error handling**: OOM and StackOverflow are caught per request.
- **Logging**: diagnostics go through `DiagnosticRedactor`.

### 1.4 Storage, EPG, playback and downloads
- **Storage**: everything is the PWA's own browser storage (IndexedDB and localStorage)
  inside the WebView, bound to the origin `http://127.0.0.1:<port>`. The first port is
  remembered and retried because changing it "loses" all the data. `allowBackup=false`.
  There is no SQLite.
- **EPG**: Xtream and Stalker *provider* EPG works through `/xtream` and `/stalker`.
  XMLTV does not exist here, and the PWA hides it anyway (`supportsEpg=false`).
- **Playback**: the PWA's own engines run in the WebView (video.js, hls.js, ArtPlayer,
  DASH ClearKey). `tv.js`:
  - hooks `iptvnator:playback-issue` and native media errors;
  - offers an external player for unplayable streams (raw `.ts`, AC3) through an
    `ACTION_VIEW` intent;
  - intercepts "Copy stream URL" (both `navigator.clipboard.writeText` and the CDK
    textarea copy) to offer the same.

  Fullscreen uses `onShowCustomView`. The screen stays on only while media plays
  (`setKeepScreenOn`). There is **no native player** (no ExoPlayer) and no per-stream
  User-Agent or Referer.
- **Downloads and export**: no downloads. Backup **export is disabled**
  (`settings-backup.facade.ts:47` checks the `tv-shell` class). Import works through the
  SAF picker.

### 1.5 How the Angular app detects and talks to it
- **Angular believes it is the PWA.** `window.electron` is absent, so
  `RuntimeCapabilitiesService.isPwa` is true, `PwaService` and `PwaXtreamDataSource` are
  used, and `BACKEND_URL=/api`.
- **Detection is a CSS class, not a capability.** `tv.js` adds `tv-shell` (and
  `android-tv-shell`) to `<html>` before bootstrap. Three places read it:
  - `apps/web/src/styles.scss:147,173` (user-select, overscroll);
  - `settings-backup.facade.ts:47` (`supportsExport`);
  - `libs/portal/shared/ui/.../grid-window.ts:19` (grid windowing).
- **JS → native**: `window.TvShell.postMessage(JSON array)`. Commands are `onReady`,
  `openExternal`, `onUrlCopied`, `onPlaybackError`, `setKeepScreenOn`, `exitApp`,
  `showExitHint`, `toast`, `showKeyboard` and `openSettings`. The bridge accepts only
  main-frame messages from the loopback origin, capped at 64 KiB.
- **Native → JS**: `evaluateJavascript` calls into `tv.js` for Back, media keys and
  D-pad events.
- The Angular code never calls the bridge. Only `tv.js` does.

### 1.6 How it is built
- Gradle (`:backend:check` and `:app:assembleRelease`) after the PWA build and
  `bundle-pwa.sh`. It is **not an Nx project**.
- CI is `.github/workflows/build-tv-shell-apk.yml`, triggered by changes to `tv-shell/**`,
  `package.json` or `pnpm-lock.yaml`. It runs:
  - node fixture tests;
  - a parser-fixture check;
  - the Playwright remote-control suites against the devserver;
  - the Java tests;
  - the APK build (signed with the versioned debug key, or release secrets);
  - an optional pre-release.
- `versionCode` is the commit time in minutes.
- Emulator tests (`tests/android-*.mjs`) cover the bridge, the file picker, protocol
  discovery and background media.

### 1.7 TV-specific vs reusable on phones
| TV-specific (must change or gate for phones) | Reusable as-is |
|---|---|
| `tv.js` D-pad FocusFinder, focus rings, Back/Menu/media key mapping, reduced motion | NanoHTTPD server + `WebBackend` (whole backend) |
| **Fixed layout-width viewport (960/1280/1600) with pinned scale**: this defeats the app's 640 px phone breakpoint and pinch-zoom. Phones need `width=device-width` | `ShellSite` static serving, SPA fallback, config injection |
| `MainActivity.dispatchKeyEvent`, long-press Menu / hold Back for settings | `ExternalPlayer` intents, `WebFileChooser` (SAF) |
| `hideSystemUi()` immersive-sticky at all times (phones need status/nav bars + safe-area insets outside playback) | Renderer-death recovery, load watchdog, error panel |
| `LEANBACK_LAUNCHER`, banner, `touchscreen required=false` | Port pinning / origin stability logic |
| 1 GB-device response caps (could be relaxed on phones) | `TvShellBridge` and WebMessageListener plumbing, redactor |
| Settings activity designed for remote navigation | CI workflow, devserver, fixture tooling |

Phone-only gaps that tv-shell does not address at all:
- orientation changes (handled via `configChanges`, but layout follows the fixed viewport);
- `adjustResize` for the soft keyboard;
- Picture-in-Picture;
- MediaSession and background audio for radio;
- share-intent and file "open with" for `.m3u`;
- SAF export (`ACTION_CREATE_DOCUMENT`).

**Security note, more serious on phones**: any app on the device can reach the loopback
server and use it as an open fetch proxy. There is no shared secret between the WebView
and the server. (Fixed by roadmap step 2, `68c82cc1a`: see `PROGRESS.md`.)

---

## 2. Electron coupling audit (renderer: `apps/web` + `libs/**`)

**The contract:**
- `ElectronBridgeApi` lives in `libs/shared/interfaces/src/lib/electron-api.interface.ts:755-1552`.
- It is exposed by `apps/electron-backend/src/app/api/main.preload.ts:309-1353` through
  `contextBridge.exposeInMainWorld('electron', …)`.
- It has about 200 methods. Channel constants are in
  `libs/shared/interfaces/src/lib/ipc-commands.ts`.

**Runtime selection:**
- `RuntimeCapabilitiesService` (`libs/services/src/lib/runtime-capabilities.service.ts`)
  sets `isElectron = !!window.electron`. Every `supports*` flag checks
  `typeof window.electron[method] === 'function'`.
- `app.config.ts:87-92` picks `ElectronService` or `PwaService`.
- `xtreamDataSourceFactory` picks the SQLite or PWA data source through
  `supportsXtreamSqliteDataSource`.
- `playlist-storage-backend.ts` picks server, SQLite or IndexedDB.

**No Node primitives**: no renderer file imports `electron`, `fs`, `path` or
`child_process`. `data.service.ts:56-65` holds legacy `isElectron` / `ipcRenderer` stubs
that return null.

> Key implication for any Android shell: exposing a *partial* `window.electron` flips
> `isPwa` to false. That silently disables the PWA paths (`supportsHistoryStorage` and
> others are `isPwa || method`) and switches `DataService` to `ElectronService`. An
> Android shell must therefore either **stay "PWA" and provide the web-backend HTTP
> contract** (what tv-shell does), or upstream a new runtime kind in the capability
> contract.

Column key: "Android replacement" is what a native Android shell would need. "tv-shell"
says whether tv-shell already covers it today.

| Files (representative) | Electron API | Feature | Android replacement | tv-shell |
|---|---|---|---|---|
| `apps/web/src/app/services/electron.service.ts`, `pwa.service.ts`, `pwa-xtream-request.ts`, `xtream-api.service.ts`, `xtream-connection-test.service.ts` | `xtreamRequest`, `XTREAM_REQUEST` via `sendIpcEvent` | Xtream API | CORS-free provider fetch (`/api/xtream` contract) | **yes** |
| `libs/portal/xtream/data-access/.../xtream-api.service.ts:80` | `xtreamCancelSession` | abort Xtream requests | request cancellation (PWA: no-op) | partial (no-op, same as PWA) |
| `.../xtream-url.service.ts:307` | `xtreamProbeUrl` | stream URL / extension probe | HEAD probe | no (PWA skips it) |
| `electron.service.ts`, `stalker-auth.api.ts`, `stalker-request.utils.ts`, `stalker-portal-discovery.service.ts` | `stalkerRequest` | Stalker portal | proxy with MAC/token/serial headers (`/api/stalker`) | **yes** |
| `electron.service.ts`, `m3u-state/effects.ts`, `playlist-refresh.service.ts`, `source-list-refresh.service.ts` | `fetchPlaylistByUrl`, `refreshPlaylist`, `cancelPlaylistRefresh`, `onPlaylistRefreshEvent`, `autoUpdatePlaylists` | M3U by URL / refresh | fetch + parse (`/api/parse`); background refresh | **yes** for URL/refresh via `/parse`; no managed progress or startup auto-update (same as PWA) |
| `libs/playlist/shared/util/.../playlist-file-import.service.ts` | `openPlaylistFromFile`, `updatePlaylistFromFilePath`, `getPathForFile` | local M3U import | SAF picker → File | **yes** (SAF `<input type=file>`); no path-based refresh |
| `services/playlist-open-request.service.ts` | `onPlaylistOpenRequest`, announce/ack | OS "open with" `.m3u` | `ACTION_VIEW`/`SEND` intent filter → JS | no |
| `libs/services/.../database-electron.service.ts`, `playlist-storage-backend.ts`, `electron-xtream-data-source.ts`, `xtream-catalog-commit.service.ts`, `xtream-pending-favorites.ts` | ~80 `db*` methods, `onDbOperationEvent`, `dbCancelOperation` | SQLite catalog cache, playlists, favorites, recent, app state, restore | WebView IndexedDB is enough (PWA path); optional native SQLite for big catalogs | **yes** via PWA path (IndexedDB / `PwaXtreamDataSource`) |
| `playback-position-runtime-bridge.service.ts` | 10 `db*PlaybackPosition*`, `onPlaybackPositionUpdate` | resume positions | PWA storage; positions reported back from external players | partial: PWA storage yes; nothing comes back from VLC |
| `portal/shared/data-access/.../unified-favorites-data.service.ts`, `workspace/.../global-favorites.service.ts` | `dbAddFavorite`, `dbReorderGlobalFavorites`, `dbGet/SetAppState` | unified / global favorites | PWA storage | **yes** (PWA path) |
| `libs/services/src/lib/search…`, `app.routes.ts:40-51` | `dbGlobalSearch`, `dbGetGlobalRecentlyAdded` | global search across portals | indexed local search | no (route hidden in PWA) |
| `catalog-title-match.service.ts`, `vod-source-pin.service.ts`, `vod-source-discovery.service.ts`, `stream-probe.service.ts` | `dbMatchTitles`, `dbFindTitleSources`, `dbVodSourcePin*`, `probeStreamUrl` | VOD multi-source | SQLite-backed matching + probe | no |
| `tmdb/tmdb-cache.service.ts` | `dbGet/SetTmdbMetadata`… | TMDB cache | persistent cache (PWA: in-memory LRU) | partial (in-memory) |
| `libs/epg/data-access/.../epg-runtime-bridge.service.ts`, `epg-preferences.service.ts`, `epg-source-settings.service.ts`, `xtream-full-epg.service.ts`, `xtream-xmltv-fallback.service.ts`, `epg-queue.service.ts`, `portal-channels-list.component.ts` | ~20 `EPG_*` methods, `openEpgFileDialog`, mapping | XMLTV import, guide, search, mapping | XMLTV fetch/parse/store (native worker or `/parse-xml` + renderer storage) | no (501; provider EPG **yes**) |
| `supportsProviderCatchUp` consumers | `isElectron` | Xtream catch-up | test archive URLs in WebView / external player | no |
| `apps/web/.../external-playback.service.ts`, `xtream-external-live-sessions.service.ts`, `xtream-native-handoff.ts`, `playback-history-gate.service.ts`, `settings-playback-section.component.ts`, `settings-form.facade.ts` | `openInMpv`, `openInVlc`, `closeExternalPlayerSession`, `onExternalPlayerSessionUpdate`, `getExternalPlayerAvailability`, `set*PlayerPath` | external players | `ACTION_VIEW` intent + installed-player query; position return | partial: intent launch from `tv.js` (unplayable / copy URL), not wired to the app's "Open in…" menus; no sessions |
| `libs/ui/playback/.../embedded-mpv-*`, `second-stream.component.ts`, `settings-embedded-mpv.facade.ts`, `settings-store.embedded-mpv.ts` | ~25 `EMBEDDED_MPV_*` | native player, tracks, subtitles, recording | ExoPlayer/Media3 (or libVLC) native player | no |
| `downloads.service.ts`, `downloads-metadata-update.ts` | 16 `downloads*` | VOD downloads | `DownloadManager` / WorkManager + SAF folder | no |
| `recordings.service.ts` | 8 `recordings*` | live recording | foreground service + muxer | no |
| `libs/ui/playback/.../electron-stream-headers.service.ts`, `m3u-state/effects.ts:222`, `m3u-workspace-route-session.service.ts` | `setUserAgent` | per-stream UA / Referer / Origin | `shouldInterceptRequest` header rewrite or a native player with headers | no |
| `services/playback-keep-awake.service.ts` | `setPlaybackKeepAwake` | keep awake | Wake Lock API or `FLAG_KEEP_SCREEN_ON` | **yes** (`tv.js` → `setKeepScreenOn`) |
| `settings/settings-backup.facade.ts`, `playlist-info-file-actions.ts` | `saveFileDialog`, `writeFile` | backup / playlist export | SAF `ACTION_CREATE_DOCUMENT` (WebView has no blob download) | no (export disabled) |
| `parental-lock/parental-lock-bridge.ts` | `setParentalLockState`, `dbSetCategoryLocks` | parental lock filter | PWA in-renderer filter | **yes** (PWA path) |
| `portal/shared/data-access/.../source-health*.ts` | `probeM3uSource`, `cancelSourceProbe`, `resetHostConnectivityGuard` | source health, connectivity guard | native probe + breaker | partial (guard reset stub only) |
| `video-player.component.ts:1689`, `unified-live-tab-remote-control.ts`, `stalker-live-stream-layout.component.ts`, `live-stream-layout.component.ts`, `settings-remote-control.facade.ts` | `onChannelChange`, `onRemoteControlCommand`, `updateRemoteControlStatus`, `getLocalIpAddresses` | phone remote control (desktop is the target) | LAN server (not needed on a phone) | no (n/a) |
| `settings-hosted-server.component.ts`, `settings-server-section.component.ts`, `source-sync/*`, `activity-/favorites-runtime-client.ts`, `history-sync-runtime.ts` | `hostedServer*`, `sourceSync*`, `*LocalRequest` | hosted server, source sync | PWA direct `/v1` HTTP + IndexedDB stores | partial: PWA sync client works against a remote server; not hosting |
| `app-update-*.ts`, `settings-app-update.facade.ts`, `app-maintenance.service.ts`, `app-startup-recovery.component.ts`, release-notes dialog | `APP_UPDATE_*`, `getAppVersion` | desktop auto-update | Play Store / in-app update; SW off | n/a (APK update; SW deliberately off) |
| `window-controls.component.ts`, `workspace-keyboard-shortcuts.service.ts`, `workspace-back-gestures.service.ts`, `settings-unload-guard.service.ts`, `app-menu-actions.ts` | `WINDOW_*`, `adjustZoomLevel`, `onWindowBackRequested`, close guard, `onAppMenuAction` | window chrome, zoom, back, close guard | system Back → history; insets | partial: Back → close dialog / history / exit in `MainActivity` + `tv.js` |
| `app.config.ts:88`, `runtime-config.ts`, `playlist-cross-context-lock.ts:91`, `app.component.ts` | `window.electron` presence, `platform` | runtime choice, `.electron` class, SW off | a capability descriptor for the Android shell | partial (`tv-shell` class marker) |

**Summary**:
- tv-shell already covers everything the browser PWA can do (Xtream, Stalker, M3U by URL
  and file, provider EPG, favorites, recents, positions, parental lock, sync client),
  because the Angular app treats it as the PWA.
- What it does not cover is exactly the PWA's documented gap list
  (`docs/architecture/pwa-self-hosted.md#runtime-limitations`): XMLTV, catch-up, global
  search, native players, downloads, recordings, stream headers, VOD multi-source, source
  health and export.

---

## 3. Approach comparison for an Android phone app

| | **A. Extend tv-shell with a phone/tablet mode** | **B. New Capacitor shell** |
|---|---|---|
| **Effort to first usable phone build** | Low: days. Needed: a device-width viewport when not on TV (`UiModeManager`/touch check), skip or neutralise `tv.js` focus handling on touch, keep system bars and insets outside fullscreen, `adjustResize`, normal launcher-only manifest bits. Backend, storage, file import, external player and CI already exist. | Medium–high. New Gradle/Capacitor project, `@capacitor/*` deps in root `package.json` and lockfile, Nx project + tags. **The CORS/back-end problem must be solved again**: Capacitor's origin (`https://localhost`) is not the provider, and the PWA calls `/api/xtream`, `/api/stalker`, `/api/parse`. Options: (1) re-implement the web-backend contract in TS in the renderer over CapacitorHttp, duplicating `apps/web-backend` and tv-shell's Java port; (2) embed a server, which is just tv-shell again; (3) add a third runtime to `RuntimeCapabilitiesService`, `app.config.ts`, `DataService` and `XTREAM_DATA_SOURCE` (invasive shared-code change). |
| **Feature coverage at parity** | The full PWA feature set on day one, already validated on devices. | The same PWA feature set at best, once the backend problem is solved. Capacitor itself adds no IPTV features. |
| **Going beyond the PWA** (downloads, export, native player, XMLTV) | Each needs a native piece plus an app-side capability: SAF export, `DownloadManager`, a Media3 player Activity reached through the bridge. Plain Java/AndroidX, no plugin layer. | The same app-side capability work. The ready plugins (Filesystem, Share, KeepAwake, StatusBar, community ExoPlayer/video plugins) save some native code, but plugin quality and version churn become a dependency. |
| **Playback** | WebView engines + intent hand-off to VLC/MX. A native Media3 player can be added later as an in-app Activity (solves `.ts`, AC3, headers). | Identical WebView playback. Native playback still needs a plugin or custom code. No inherent advantage. |
| **Storage / origin** | Origin `http://127.0.0.1:<port>`; the port-pinning workaround is already built and tested. | Stable `https://localhost` origin, which is a genuine small advantage. |
| **Security** | Must add a loopback shared secret (more apps on phones). This is upstreamable and benefits TV too. | No loopback server under option (1) or (3). |
| **Maintainability vs upstream (rebasing fork)** | Changes stay inside `tv-shell/` (an upstream-owned subtree with its own AGENTS.md), plus at most a small capability hook in `apps/web`. Upstream fixes to tv-shell arrive automatically. Rebases stay small. | A second Android shell drifts from tv-shell. Root lockfile changes conflict on every rebase, and they also retrigger the tv-shell CI (it watches `pnpm-lock.yaml`). Every backend-contract change upstream must be mirrored in a third implementation. |
| **Chance of upstream accepting it** | High. The maintainer merged tv-shell, keeps editing it, and the website already invites tablet use while noting "compact phone screens still need layout work". A "phone/tablet mode" PR fills a gap upstream has named. | Low. It duplicates an existing upstream Android shell, adds a framework and deps, and option (3) changes the capability contract for a runtime upstream doesn't ship. |
| **Fork constraints (CLAUDE.md)** | tv-shell is Java and not an Nx project. The 300/400-line rule is a TS rule, but the spirit says split `MainActivity` (766 lines) rather than grow it. Distribution under a distinct name, app ID and icon via a Gradle `productFlavor`. | Fits "Nx project with tags" naturally, but that structure fixes nothing on the real hard problems. |

### Recommendation: **A, extend tv-shell with a phone/tablet mode**, and drop the Capacitor plan
1. **It solves the hard part already.** The work that gates any Android port is the
   CORS-free web-backend contract, origin-stable storage and SAF import. tv-shell
   already has it, tested against the real parser fixtures and shipped on devices.
   Capacitor would rebuild it or route around it with an invasive runtime change.
2. **Coverage and playback are identical** at parity. Every post-parity feature
   (export, downloads, native player, stream headers) costs the same app-side capability
   work either way. Only the native half differs, and plain AndroidX is enough.
3. **It rebases cleanly and is upstreamable.** Changes stay in `tv-shell/`, the
   maintainer has signalled interest in phones and tablets, and every merged PR shrinks
   the fork.
4. **Capacitor's real advantages** (a stable `https://localhost` origin, no loopback
   server, a plugin ecosystem) are small next to a duplicate shell, root dependency
   churn, and a third backend implementation.

### Suggested roadmap if A is approved (no code yet; each step is one upstreamable PR)
1. **Phone/tablet mode in tv-shell**: detect TV vs touch (`UiModeManager` +
   `PackageManager.FEATURE_TOUCHSCREEN`). In touch mode:
   - serve `width=device-width` (`ShellSite.viewport`);
   - do not inject `tv.js` focus handling. Split out a small `shell.js` for the
     bridge-only hooks (copy-URL and playback-issue → external player, keep-screen-on,
     `tv-shell` marker);
   - show system bars outside fullscreen, add safe-area insets, `adjustResize`;
   - set the manifest so the app installs on phones without leanback-only features.

   Split `MainActivity` while doing this.
2. **Loopback shared secret**: generate a per-launch token, inject it with
   `app-config.js`, and require it on `/api/*`.
   *Done differently (`68c82cc1a`)*: `app-config.js` is served to any local app, and a
   cookie leaked through redirects to other local ports. The token is now an
   `X-TvShell-Api` header, added by a native document-start script limited to the shell's
   origin. Details in `PROGRESS.md`.
3. **Capability hook instead of the class marker**: a small shell descriptor (e.g. in
   `__IPTVNATOR_CONFIG__`) read by `RuntimeCapabilitiesService`, so features like export
   and "open in external player" are gated by capability, not by the `tv-shell` class.
4. **SAF backup and playlist export** behind that capability.
5. **External-player menu integration** (Android intent player as a managed external
   player option), then a **native Media3 player** for `.ts`, AC3 and headers.
6. Later and optional: `.m3u` "open with"/share intents, PiP, MediaSession for radio,
   downloads.

The fork's distribution identity (name, app ID, icon) comes from a Gradle flavor that
stays in the fork. CLAUDE.md's "Capacitor shell" line should be updated once the choice
is approved. It was not edited here.

## Verification of this audit
- All claims trace to files read in this session: `tv-shell/README.md`, `AGENTS.md`,
  `AndroidManifest.xml`, `build.gradle`, `TvShellServer`, `TvShellBridge`, `ShellSite`,
  `WebBackend`, `ExternalPlayer`, `MainActivity` (settings, key and system-UI sections),
  the CI workflow, `RuntimeCapabilitiesService`, the `pwa-self-hosted.md` limitations,
  and the bridge inventory (`electron-api.interface.ts`, `main.preload.ts`, the call-site
  sweep).
- For implementation work later:
  - `node --test tv-shell/tests/*.test.mjs`
  - `cd tv-shell/android && ./gradlew :backend:check :app:assembleRelease`
  - the tv-shell Playwright suites against the devserver
  - emulator tests (`tests/android-*.mjs`) on a phone-profile AVD
  - the CLAUDE.md pre-commit gates for any `apps/web` change
