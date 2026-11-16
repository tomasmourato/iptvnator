# Android phone port: progress log

Plan: approach A in [AUDIT.md](AUDIT.md), a phone/tablet mode in `tv-shell/`.

## 2026-11-15 — baseline: unmodified tv-shell debug APK on a phone AVD

No code changed. The goal was to see what breaks on a phone before roadmap step 1.

### Setup
- AVD: Pixel 8 profile, 1080×2400 at 420 dpi, API 37 (`google_apis_playstore`, x86_64).
- Build: `pnpm nx build web --configuration=pwa`, then
  `bash tv-shell/scripts/bundle-pwa.sh dist/apps/web` (Git Bash), then
  `gradlew.bat :app:assembleDebug` in `tv-shell/android`. JDK 21 builds fine
  (Gradle 8.14.3, AGP 8.9.1); the README asks for 17.
- The SDK needed `platforms/android-35`, `build-tools/35.0.0` and the AVD's
  system image. `sdkmanager.bat` mangles `;` in package names on Windows, so I
  used `android sdk install system-images/android-37.0/google_apis_playstore/x86_64`
  (slash form) instead.
- Test data: `node tv-shell/devserver/mock-provider.mjs --port 18080` (synthetic),
  plus `adb reverse tcp:18080 tcp:18080`, so the playlist URL in the app is
  `http://127.0.0.1:18080/sample.m3u`. Port 18080 stays clear of the shell's
  own 43330–43334 range.
- I read the served page with `adb forward tcp:43399 tcp:43330` and
  `curl http://127.0.0.1:43399/`.

### What works
- **Startup and backend:** the app starts, and `/api/parse` imports the M3U
  (184 channels).
- **Touch basics:** taps and swipe-scrolling work. Tapping a text field opens
  the soft keyboard.
- **Playback:** the WebView plays the `.webm` clip. Fullscreen works through
  `onShowCustomView`.
- **Back:** it closes dialogs, goes back through history, and on the
  dashboard shows "Press Back again to exit".
- **Unplayable `.ts` streams:** the mock streams show the app's normal
  "stream could not be loaded" panel, as they would in the browser PWA.

### What is broken on a phone (by severity)
1. **Fixed desktop viewport, so the UI is about 3× too small.** The served meta
   tag is `width=1280, initial-scale=0.3211, minimum-scale=0.3211,
   maximum-scale=0.3211`. That is about 13 CSS px of text in about 4 physical px,
   with tap targets around 18 device px (for example the settings gear and the
   star buttons). The app's 640 px phone breakpoint never triggers, so you get
   the desktop two-pane layouts and pinch-zoom is locked. In landscape the
   scale becomes 0.7141, which is readable but still the desktop layout. Fix:
   `width=device-width` in touch mode (`ShellSite`), roadmap step 1.
2. **Rotation reloads the page and loses state.** After a rotation the channel
   that was playing is gone ("Please select a channel"), and a skeleton loader
   shows until the reload finishes. Cause: as the README says, "a display change
   that alters the scale reloads the same route". With `device-width` the scale
   would stay fixed, so this should go away along with item 1.
3. **Always immersive.** The status and navigation bars are hidden all the time
   (`hideSystemUi()`, `IMMERSIVE_STICKY`), and Android shows its "Viewing full
   screen" prompt on first launch. On a phone the bars should show outside
   fullscreen playback, with safe-area insets.
4. **Native shell settings can't be reached by touch.** They open only on a
   held Back key or the Menu key (`MainActivity.dispatchKeyEvent`), or from a
   button on the error panel. Phones with gesture navigation can't send a held
   Back, and `adb shell input keyevent --longpress 4` did not open them either.
   They need a touch entry point.
5. **TV focus styling leaks into touch use.**
   - D-pad focus rings sit on the playlist selector and on list rows after
     taps and loads.
   - The focused text field shows a second ring inside the Material outline.
   - On first open, the channel list was scrolled to row 30 with that row
     focused. Probably `tv.js` focus restore; not investigated.

   Fix: skip `tv.js` focus handling in touch mode (roadmap step 1: `shell.js`).
6. **Soft keyboard pans instead of resizing.** The manifest sets
   `windowSoftInputMode="adjustPan"`. The centred add-playlist dialog stayed
   visible here, but forms lower on the page will be covered. Roadmap step 1
   asks for `adjustResize`.
7. **Fullscreen stays in portrait.** The video is letterboxed in the middle of
   a portrait screen. Phones normally switch fullscreen video to landscape
   (sensor landscape while in the custom view).
8. **Desktop UI on a phone.** The player sits in a narrow right column with its
   controls about 1,000 device px below the picture. The page also shows desktop
   hints: `Ctrl+K` badges and a virtual-keyboard toggle button. Most of this
   should resolve once the phone breakpoint applies (item 1). Re-check after
   step 1.
9. **Manifest.** `LEANBACK_LAUNCHER` and `touchscreen required=false` didn't
   stop installing or launching on the phone (the normal `LAUNCHER` entry is
   present). For Play Store phone distribution, review this in step 1.

### Not checked yet
- File import (SAF picker).
- Xtream and Stalker flows against the mock.
- Handing off to an external player (no player app on the AVD).
- Tablet profile.
- Dark/light system theme.

### Next
Roadmap step 1: touch mode in tv-shell. It covers items 1–6 (and 7 at the
same time); then re-run this checklist on the phone AVD.

## Step 1: touch mode in tv-shell

Plan: roadmap step 1 of [AUDIT.md](AUDIT.md), with the nine findings above as
the acceptance checklist. TV mode must behave exactly as before, so every
commit that touches app code is checked against a TV baseline taken from the
unmodified tree. No upstream rebase happens during step 1, because it would
invalidate the baseline.

### Test setup
- **AVDs:**
  - phone: `LisBora_API37`, Pixel 8, 1080×2400 at 420 dpi, API 37;
  - tablet: `TvShell_Tablet_API37`, Pixel Tablet, 2560×1600 at 320 dpi,
    same API 37 image;
  - TV: `TvShell_TV_API34`, 1080p at 320 dpi (960 CSS px), Android TV 14
    (`system-images/android-34/android-tv/x86`), 2 GB RAM.

  `avdmanager.bat` mangles `;` in package paths on Windows, so the tablet
  and TV `config.ini` files were written by hand.
- **Builds:** JDK 17 (portable Microsoft OpenJDK 17.0.21), `gradlew :backend:check
  :app:assembleDebug`.
- **Test data:** the devserver's synthetic mock provider (`run-dev.sh`, host
  port 43331, `adb reverse tcp:43331 tcp:43331`).
- **Repeatable screens:** a scratch script, not committed:
  - clears the app's data;
  - captures home, the add-playlist dialog, the soft keyboard on the URL
    field, the channel list, three D-pad steps, playback, fullscreen and
    settings;
  - records a state fingerprint: viewport, `html` classes, `.tv-focus`
    elements, active element, IME and rotation.

  Two runs on the same build have identical facts and 0.000% pixel
  difference, so any later TV difference is real.

### Known harness limits (same before and after)
- The Playwright `_android` suites hang in `device.close()` after their last
  check. Results are compared by their PASS lines under a timeout.
- `android-file-input.mjs` can't run on Android TV: the image has no
  DocumentsUI. It runs on the phone and tablet.
- A held Back or Menu (the shell settings on TV) can't be injected: adb's
  `--longpress` repeats before the 650 ms threshold, and the production TV
  image has no root for `sendevent`. That path is checked by code review.

### TV baseline (unmodified `8bc278232`)
| Check | Result |
|---|---|
| `:backend:check` (JVM) | 61 passed |
| `node --test tv-shell/tests/*.test.mjs` | 13 passed |
| `tv-remote.mjs` / `tv-longlist.mjs` (Chromium, devserver) | 133 / 19 checks, 0 failed |
| `android-bridge` / `-protocol-discovery` / `-background-media` (TV AVD) | 1 / 1 / 4 PASS |
| `android-file-input` | n/a on TV (no DocumentsUI) |
| Served `index.html` | `width=1280, initial-scale=0.75, …`, plain `tvshell/tv.js` tag |
| Screens | D-pad ring on the playlist switcher at start, ring on channel 10 after load and channel 13 after three Downs, the OK key opens the keyboard on the URL field, fullscreen video |

The phone baseline is the same build on the Pixel 8 AVD:
- all four emulator suites pass (1 / 3 / 1 / 4 PASS);
- the screens reproduce findings 1 and 5, including the list opening on
  channel 30 with a focus ring.

### Commit 1: split MainActivity (no behaviour change)
`MainActivity` goes from 766 to 574 lines. Code moves verbatim into
package-private helpers:
- `KeyRouter`: keys, held Back/Menu, forwarding in fullscreen;
- `FullscreenController`: the custom view;
- `ExternalOffer`: the external-player dialog;
- `WindowChrome`: system bars;
- `ShellDiagnostics`: screen metrics and the diagnostics text.

| Check | Result |
|---|---|
| `:backend:check` | 61 passed |
| TV emulator suites | PASS lines identical to the baseline |
| Served `index.html` (TV) | byte-identical |
| TV screens | facts identical, 0.000% pixel difference on all 8 shots |
| Phone emulator suites | 1 / 3 / 1 / 4 PASS, the same as the baseline |
| Monorepo gates | test:frontend, typecheck:web, build:frontend:pwa pass; lint fails only on the pre-existing upstream `ui-playback` max-lines (#2211) |

### Commit 2: touch interface mode with a device-width viewport
- **Mode detection:** a pure-Java rule, `InterfaceMode` (JVM-tested). TV
  means the TV UI mode, or Leanback, or no touchscreen. Otherwise touch. The
  shell settings get an Interface choice: Automatic, TV or Touch. The
  layout-width choice hides in touch mode.
- **Touch serving:** `ShellSite` serves `width=device-width, initial-scale=1`
  and `<script src="tvshell/tv.js" data-tvshell-mode="touch" defer>`. The
  WebView allows pinch-zoom. The devserver mirrors this with `--touch` (or
  `TVSHELL_TOUCH=1`).
- **Rotation:** no reload in touch mode.
- **New emulator test:** `tests/android-touch.mjs` checks:
  - a device-width page at scale 1;
  - no horizontal scroll at phone width;
  - CDP pinch-zoom in and out;
  - a rotation that keeps the page (a JS marker survives) and the playing
    clip.

  It fails on the commit 1 APK (pinned `width=1280` viewport) and passes on
  this one.

| Check | Result |
|---|---|
| `:backend:check` | 65 passed (+4: touch serving, mode truth table) |
| `node --test` | 15 passed (+2: devserver TV and touch transformations) |
| `android-touch.mjs` phone / tablet | PASS: 411 CSS px page, rotation 411 → 914 with playback kept / PASS: 1280 CSS px, rotation 1280 → 800 |
| Phone emulator suites | 1 / 3 / 1 / 4 PASS, the same as the baseline |
| Tablet emulator suites | 1 / 2 / 1 / 4 PASS. File-input fails at "Cancel did not return to the app" with the **baseline APK too** (the tablet DocumentsUI layout); both file selections pass |
| TV emulator suites, served `index.html` | identical to the baseline |
| TV screens | facts identical. One run showed 0.059% difference on the settings shot (the rail's playlist-group outline caught mid-transition). The same APK's rerun is pixel-identical to the baseline, and to a rerun of the baseline APK, so it's run noise, not a change |
| Monorepo gates | as commit 1 (lint: only the upstream `ui-playback` failure) |

Phone after commit 2: portrait uses the app's phone layout at normal size,
with no `Ctrl+K` badge and no keyboard-shortcuts button. In landscape
(914 CSS px, above the app's 640 px breakpoint) the desktop header with
those hints returns. That's the app's own breakpoint, tracked under
finding 8.

Still open (commits 3–5): the bars stay hidden, the TV focus ring and the
row-30 scroll remain, and there's no touch entry to the shell settings.

### Commit 3: system bars, insets, keyboard resize and fullscreen orientation
- **Touch outside fullscreen** (`WindowChrome`, platform APIs only; no
  `androidx.core`, which is only transitively present at 1.1.0):
  - the theme's `FLAG_FULLSCREEN` is cleared and the bars stay visible;
  - the window draws edge to edge;
  - the root view is padded by the system-bar, cutout and IME insets;
  - the window uses `adjustResize`.

  No immersive flags are set, so the "Viewing full screen" prompt no longer
  appears.
- **Touch in fullscreen video** (`FullscreenController`): the bars hide (a
  swipe shows them briefly) and the screen turns to sensor landscape. Portrait
  video stays in portrait. Leaving fullscreen restores both.
- **TV:** unchanged. The window code is a no-op while the mode stays TV, and
  the manifest keeps `adjustPan`.
- **`android-touch.mjs` new checks:**
  - the status bar is visible and the WebView sits between the bars;
  - no immersive prompt;
  - fullscreen goes landscape without bars, and back;
  - the soft keyboard shrinks the page with the focused field visible.

| Check | Result |
|---|---|
| `:backend:check` | 65 passed |
| `android-touch.mjs` phone | 5/5 PASS: page 132..2337 px between the bars; keyboard 840 → 527 CSS px; rotation 411 → 864 (the cutout is now inset) |
| `android-touch.mjs` tablet | 5/5 PASS: page 48..1536 px; keyboard 744 → 400 CSS px |
| Phone / tablet emulator suites | 1 / 3 / 1 / 4 PASS on both (tablet file-input passed this time; it's flaky there, see commit 2) |
| TV emulator suites, served `index.html`, screens | identical to the baseline (0.000% on all 8 shots) |
| Monorepo gates | as before (lint: only the upstream `ui-playback` failure) |

### Commit 4: no remote-control focus layer in touch mode
- **`tv.js`:** reads `data-tvshell-mode="touch"` from its own script tag.
  - In touch mode it adds `tv-shell-touch` to `<html>` and installs neither
    the remote-control stylesheet (ring, `cursor:none`, hidden scrollbars,
    lite motion) nor the D-pad layer. That layer — focus mirror, key handler
    and initial focus — now sits in `installRemoteControl()`.
  - `scheduleRefocus()` is a no-op, and `api.back()` skips its focus side
    effects.
  - The bridge hooks stay: external player, copied URLs, playback errors,
    keep-screen-on, Back, `onReady`.
  - The `tv-shell` and `android-tv-shell` classes stay too, because the app
    relies on them (backup export, grid windowing, overscroll).
- **`MainActivity.focusPage()`:** replaces every
  `webView.requestFocus(View.FOCUS_DOWN)`, including those in
  `FullscreenController` and `ExternalOffer`.
  - TV: the same call.
  - Touch: plain window focus that never moves the page focus.
- **New Chromium test `tests/tv-touch.mjs`:** Pixel 7 emulation against a
  touch devserver (`--port 43332 --touch`). It checks:
  - the page is the touch page, with no TV stylesheet and nothing focused on
    load;
  - the phone header has no `Ctrl+K` badge or shortcuts button;
  - taps leave no ring;
  - the list opens at channel 1;
  - arrows don't drive a ring;
  - Back still works through `__tvShell.back()`.

  It's added to `.github/workflows/build-tv-shell-apk.yml` with a second dev
  server. With the committed (commit 3) `tv.js`, six of its checks fail.
- **`android-touch.mjs`:** checks that the playlist loads with no ring,
  nothing made focusable and the list at channel 1, and that opening a
  channel leaves no ring. It fails on the commit 3 APK.

| Check | Result |
|---|---|
| `:backend:check` / `node --test` | 65 / 15 passed |
| `tv-remote.mjs` / `tv-longlist.mjs` (Chromium, with the new `tv.js`) | 133 / 19 checks, every check line identical to the baseline, except video.js's generated element id in one message (`vjs_video_869` → `870`) |
| `tv-touch.mjs` | 14/14 checks |
| `android-touch.mjs` phone / tablet | 6/6 PASS on both |
| Phone / tablet emulator suites | 1 / 3 / 1 / 4 PASS on both |
| TV emulator suites, served `index.html` | identical to the baseline |
| TV screens | see below: no difference from commit 4 |
| Monorepo gates | as before (lint: only the upstream `ui-playback` failure) |

**TV screens, a second day.** Today the first comparison with yesterday's
baseline differed on the home shot: initial focus went to the Dashboard rail
link instead of the playlist switcher. The **unmodified baseline APK does the
same today**. Over 9 baseline runs and 5 commit 4 runs:
- the home focus flips between the switcher and the rail link in both;
- the settings page's initial focus flips once in each (baseline run 11,
  commit 4 run 3).

Every commit 4 run has a baseline run with identical facts and 0.000% pixel
difference on all 8 shots. These two initial-focus spots are existing races
in TV mode, not changes. From now on, compare TV screens against a baseline
run captured the same day.

### Commit 5: launcher shortcut to the shell settings
- A static shortcut, `res/xml/shortcuts.xml`. A long press on the app icon
  shows *IPTVnator settings* (*Réglages de IPTVnator* in French). It starts
  `MainActivity` with `org.tvshell.iptvnator.action.OPEN_SETTINGS`.
  `MainActivity` opens the settings:
  - in `onCreate`, but not on a recreate or a relaunch from Recents;
  - in `onNewIntent`, the singleTask path, which keeps the running page.

  `SettingsActivity` stays unexported. The shortcut's `targetPackage` is the
  applicationId, so a flavor with another ID overrides the file.
- **`android-touch.mjs`:**
  - new check: the shortcut is published (`dumpsys shortcut`), opens the
    settings over the running page, and Back returns to the same page (a JS
    marker survives). It fails on the commit 4 APK, at "the settings shortcut
    is published to the launcher".
  - the add-playlist wait grows from 20 to 60 s. A cold phone emulator took
    26 s today with both APKs.

| Check | Result |
|---|---|
| `:backend:check` | 65 passed |
| `android-touch.mjs` phone / tablet | 7/7 PASS on both |
| Phone / tablet emulator suites | 1 / 3 / 1 / 4 PASS on both |
| Launcher (phone) | long press shows *IPTVnator settings*; tapping it opens the settings, Back returns to the app. A cold start through the shortcut opens the settings, Back loads the app, and a later icon launch doesn't reopen the settings |
| TV, same-day baseline | 3 baseline runs and 3 commit 5 runs: PASS lines and served `index.html` identical. Commit 5 runs 1–2: facts identical, 0.000% on all 8 shots. Run 3: initial focus on the settings page went to the Dashboard rail link (the known race). Baseline run 6 did the same today and matches it exactly (facts identical, 0.000%) |
| `tv-remote.mjs` / `tv-longlist.mjs` | all checks passed (no `tv.js` change) |
| Monorepo gates | as before (lint: only the upstream `ui-playback` failure) |

## Step 1 summary (2026-11-16)
Touch mode is done in five code commits and this docs commit, all inside
`tv-shell/`. The acceptance checklist against the nine findings:

| # | Finding | Status | Where |
|---|---|---|---|
| 1 | pinned 1280 px viewport at 0.32 scale | fixed: device-width at scale 1, pinch-zoom | commit 2 |
| 2 | reload on rotation | fixed: page and playback kept | commit 2 |
| 3 | always immersive | fixed: bars visible, page between them, no prompt | commit 3 |
| 4 | shell settings unreachable by touch | fixed: launcher shortcut | commit 5 |
| 5 | TV focus rings, scroll restore | fixed: no D-pad layer in touch mode | commit 4 |
| 6 | keyboard pans instead of resizing | fixed: IME inset padding, `adjustResize` | commit 3 |
| 7 | fullscreen stays in portrait | fixed: sensor landscape (portrait video stays upright) | commit 3 |
| 8 | desktop hints on a phone | portrait fixed by the app's own ≤640 px rules; landscape still shows them | open (apps/web) |
| 9 | leanback manifest entries | kept on purpose (TV needs them); no install blocker | — |

- **TV parity:** every app-code commit was checked on the Android TV AVD
  against a baseline from the unmodified tree (same day from commit 4 on):
  emulator suites, served `index.html` and 8 screens. The only differences
  were two initial-focus races that the baseline APK shows too.
- **Docs:** `tv-shell/README.md` and `README.fr.md` describe touch mode, the
  *Interface* setting, the launcher shortcut, `tv-touch.mjs` and
  `android-touch.mjs`. The release note is
  `.changes/tv-shell-phone-tablet-mode.md`.
- **Open findings:**
  - item 8: a phone in landscape (above the app's 640 px breakpoint) still
    shows `Ctrl+K` and the shortcuts button. That needs an apps/web
    follow-up, for example gated by `(pointer: coarse)`;
  - a minor one: the app's own hover style can stay on a row after a tap
    (sticky hover). That's app styling, not the TV ring;
  - the add-playlist step can take over 20 s on a cold emulator (26 s seen
    with both the commit 4 and 5 APKs). The test now waits 60 s.
- **Rebased** on `upstream/master` `d824c5e50` (11 upstream commits, none in
  `tv-shell/` or `docs/android/`; no conflicts, lockfile unchanged). The
  pre-rebase branch is kept as `backup/android-capacitor-pre-rebase-20261116`.
- **To resume:**
  - AVDs: phone `LisBora_API37` (`-port 5554`), tablet
    `TvShell_Tablet_API37` (`-port 5558`), TV `TvShell_TV_API34`
    (`-port 5556`). Run one at a time.
  - Run `WWW=$PWD/dist/apps/web tv-shell/devserver/run-dev.sh` (mock on
    43331) and `node tv-shell/devserver/server.mjs --www dist/apps/web
    --port 43332 --touch`.
  - Build with JDK 17. The TV baseline is the unmodified `tv-shell/` of
    `upstream/master` with the same PWA bundle as the candidate:
    `git archive upstream/master tv-shell/android | tar -x -C <dir>`, copy
    `app/src/main/assets/www/` from the candidate tree, then
    `./gradlew :app:assembleDebug`. Capture the baseline on the same day as
    the candidate runs. The screenshot scripts are scratch tools, not
    committed.
  - In Git Bash, set `MSYS_NO_PATHCONV=1` before `adb shell … /sdcard/…`
    commands, or the path is rewritten to the Git install folder.

### After the upstream rebase (2026-11-16)
The baseline is `upstream/master`'s own `tv-shell/` built with the same new
PWA bundle, because upstream changed the web app (#2249 adds a TV home
screen). The two APKs' contents differ only in the fork's files: the
shortcut, the settings layout, `tv.js` and the code.

| Check | Result |
|---|---|
| TV emulator suites, served `index.html` | 3 baseline and 3 rebased runs: PASS lines (1 / 1 / 4) and `index.html` identical |
| TV screens | rebased runs 2–3: facts identical, 0.000% on all 8 shots. Run 1: only the known settings initial-focus race (none of the 3 baseline runs hit it this time) |
| `tv-remote.mjs` / `tv-longlist.mjs` / `tv-touch.mjs` | 133 / 19 / 14 checks, all passed |
| `node --test tv-shell/tests/*.test.mjs` | 15 passed |
| `android-touch.mjs` phone | 7/7 PASS |
| Phone emulator suites | 1 / 3 / 1 / 4 PASS, the same as before |
| lint | passes completely (upstream fixed the `ui-playback` max-lines failure) |
| typecheck:web / build:frontend:pwa | pass |
| test:frontend | flaky upstream test, see below |
| release notes / agent guidance | 60 notes valid / 4 files valid |

The tablet wasn't re-run after the rebase. It passed 7/7 on commit 5.

**Flaky upstream test.** Two of six `test:frontend` runs on the rebased tree
failed. The first failure's test wasn't recorded; the second is
`apps/web/src/app/settings/settings.component.storage-failure.spec.ts` ›
"SettingsComponent storage failures › warns when the persisted settings could
not be read". Its `beforeEach(waitForAsync(…))` exceeded Jest's 5000 ms hook
timeout; that suite took 75.8 s in the failing run (1 failed, 1200 passed). The
fork doesn't touch that file, so it is left to upstream.

## Fix: Xtream catalogues over 64 MiB (2026-11-16)

Found in real-world testing on a Pixel 10 (debug APK, 512 MB largeHeap). Two
real Xtream providers that work in another player failed on Movies:
provider A showed "Portal unavailable", provider B "Something went wrong".
Retry did not help, and the Live and Series tabs showed the same error.

### Diagnosis
- Captured redacted logcat and a WebView CDP trace (hosts, credentials and
  query values stripped as they were recorded) while Retry was tapped on each.
- One cause: the embedded backend refused one catalogue per provider with
  `ERR_RESPONSE_TOO_LARGE`.
  - A: `get_vod_streams` was 84.0 MiB.
  - B: `get_series` was 74.2 MiB (its 52.3 MiB VOD loaded).
- The cap was `min(64 MiB, heap/8)`, because the old path held a body three to
  five times over: a doubling buffer, a String, then a re-encoded copy.
- Ruled out: account checks (Active), category lists and live streams (up to
  19 MB) all succeeded. No TLS, cleartext, User-Agent or timeout errors; the
  slowest request took 12 s against a 30 s timeout.
- The Xtream import is all-or-nothing, so one oversized catalogue blocked
  every tab.

### Fix (`tv-shell/` backend only)
- `HttpFetcher.getRaw` reads the answer straight into `Utf8Sink` chunks, with
  no doubling buffer and no String.
- `JsonBytes` checks the JSON and the UTF-8 in place. It follows
  `Json.validate` plus `payloadOf`'s trim and BOM rules.
- `ProviderPayload` writes `{"action":…,"payload":` then the provider's own
  chunks by reference, then `}`. The answer is held once, from the socket to
  the WebView.
- Fallback: another charset, lenient JSON or plain text goes through the old
  decoding path and its old cap. Both paths produce byte-identical output.
- New pass-through cap for Xtream and Stalker: `min(256 MiB, heap/4)`. That is
  128 MiB on the phone, 144 MiB on the API 37 AVD and about 64 MiB on a 1 GB
  stick. `/parse` keeps `heap/8`.
- Mock provider: `--large-catalog [MiB]` (`devserver/large-catalog.mjs`)
  streams synthetic ~90 MiB `get_vod_streams` and `get_series` with non-ASCII
  names.
- New emulator check: `tests/android-large-catalog.mjs`.

### Results
| Check | Result |
|---|---|
| `:backend:check` | 71 passed (+6). New: envelope byte-identical to the decoding path over 28 bodies split at 1–4096 bytes; malformed UTF-8 and hostile nesting fall back; gzip and chunked raw reads; a child JVM at `-Xmx128m` relays a 30 MB catalogue (twice the old cap, 90% of the new one; 33 MB at 98% also passed) |
| `node --test` | 15 passed |
| `android-large-catalog.mjs`, phone AVD, **before** APK | FAIL: `get_vod_streams answered 95 bytes` (the `ERR_RESPONSE_TOO_LARGE` body) |
| `android-large-catalog.mjs`, phone AVD, fixed APK | PASS: 220 605 movies and 199 608 series (91 MiB each), all three tabs list items, no OOM |
| Phone AVD suites (bridge / protocol-discovery / file-input / background-media / touch) | 1 / 1 / 3 / 4 / 6 PASS |
| Pixel 10, real providers (`adb install -r`, data kept) | A: `get_vod_streams` 88 080 800 bytes in 14 s. B: `get_series` 77 821 992 bytes in 7 s. All six tabs list content, no OOM in logcat. Tested before `EncodedBody` and `RawResult` moved to their own files (a verbatim move to keep `HttpFetcher` under 300 lines); the final build passed the JVM suite and the AVD check again |
| Monorepo gates | release-notes, test:frontend, typecheck:web, build:frontend:pwa pass; lint fails only on the pre-existing upstream `ui-playback` max-lines (#2211) |

Harness note: `android-touch.mjs` run right after `android-background-media.mjs`
fails at "synthetic playlist did not load". The **before APK fails the same
way**, and it passes when run on its own, so it's an existing ordering
problem between the two suites. Run android-touch first, or on its own.

### Next (follow-ups, one commit each)
1. **Readable Android error for oversized answers.** The backend's code
   `ERR_RESPONSE_TOO_LARGE` isn't one the UI maps (it knows
   `PROVIDER_RESPONSE_TOO_LARGE`), so it falls back to "Something went
   wrong". The UI's existing too-large text tells users to raise a Docker env
   var, which is wrong on Android. Needs an Android-appropriate message and a
   `.changes/` note.
2. **"Portal unavailable" while the account is Active.** On provider A the
   label came from `resolveInitBlockReason(portalStatus)`, with the store's
   status still `'unavailable'` (its initial value in `with-portal.feature.ts`)
   although `get_account_info` answered Active. Cause not yet found; frontend,
   upstream code.
3. **Partial import.** One failed catalogue blocks every tab, including Live
   that loaded. Upstream behaviour in `with-content.feature.ts`.

## Fix: live channels that fail or hang in the built-in player (2026-11-16)

Found in real-world testing on the Pixel 10 (WebView/Chrome 157). On Xtream
provider A, many live channels either showed "The browser player reported a
playback error" or stayed on the loading screen forever. The same channels
play in another player.

### Diagnosis
- Captured a redacting WebView CDP trace (console, network, Audits, Media
  domain; hosts replaced by labels, paths and query values stripped as they
  were recorded) plus filtered logcat. In-page probes returned only codec
  statistics, never URLs.
- **Network ruled out.** The m3u8 answers 302 to another host. Every hop and
  segment has `Access-Control-Allow-Origin: *`, segments are 200
  `video/mp2t`, and there are no CORS, mixed-content or blocked requests.
- **"Loading forever" = HEVC in MPEG-TS.** The PMT has video stream type 0x24
  (HEVC) and AAC audio. Video.js 8 / mux.js 7.1 only knows H.264 (0x1B), so
  it drops the video without an error. Chromium plays the audio only
  (`videoWidth` 0, 0 decoded frames). The WebView supports HEVC through MSE,
  and the HTML5 player (hls.js 1.7.3, which has an HEVC-in-TS parser)
  plays these channels.
- **The decode error = open-GOP H.264.** High@4.0, I-frames are non-IDR with
  a recovery-point SEI (`recovery_frame_cnt=0`), and leading B-frames
  reference the previous GOP. Chromium's MediaCodec reports "initial decode
  error", the FFmpeg fallback rejects the first non-key packet, then
  `PIPELINE_ERROR_DECODE`. It fails the same way with Video.js and with
  hls.js, so no WebView engine can start it. Native players (Media3 HLS sets
  `FLAG_ALLOW_NON_IDR_KEYFRAMES`, VLC) drop the leading pictures.
- The vague message: Video.js `MEDIA_ERR_DECODE` was classified as
  `unknown-playback-error`.

### Fix, phase 1 (three commits)
1. `fix(playback)`: a decode failure on the media element itself is
   reported as `media-decode-error` (VHS's own code 3, "no working
   playlists", stays unknown). `tv.js` already offers the external player
   for that code.
2. `fix(playback)`: `MissingVideoWatchdog` on the Video.js and HTML5
   elements reports `unsupported-codec` after 8 s of playback with no
   decoded video frame (audio containers excluded), so the spinner ends and
   the external-player offer appears.
3. `feat(tv-shell)`: shell capability descriptor (below). In touch mode the
   shell prefers the HTML5 player, applied only when no player choice is
   stored; TV keeps Video.js.

### Roadmap step 3 (capability hook): what this covers
- **Done:** the descriptor itself. `assets/app-config.js` (ShellSite and
  the devserver) carries `__IPTVNATOR_CONFIG__.SHELL = { kind, mode,
  features: { externalPlayer, backupExport }, preferredPlayer? }`, validated
  by `parseShellDescriptor` (`@iptvnator/shared/interfaces`) and read
  through `RuntimeCapabilitiesService` (`shell`, `hasShellFeature()`). The
  first consumer is the default player.
- **Remaining:** migrate the `tv-shell` class readers to the capability:
  `settings-backup.facade.ts` (`supportsExport` → `backupExport`),
  `grid-window.ts` and `styles.scss`. Gate "open in external player" in the
  app on `externalPlayer`. Add feature flags when SAF export lands (roadmap
  step 4).

### Results
| Check | Result |
|---|---|
| Monorepo gates (each commit) | lint, test:frontend, typecheck:web, build:frontend:pwa pass; release notes valid |
| `pnpm nx test ui-playback` alone | two Windows-only palette spec failures (path separators, line endings), unrelated upstream files, same before the fork's change |
| `:backend:check` | 71 passed |
| `node --test tv-shell/tests/*.test.mjs` | 16 passed (+1: descriptor) |
| `tv-remote.mjs` / `tv-longlist.mjs` (Chromium, devserver, TV) | all checks passed; TV descriptor has no `preferredPlayer` |
| `android-bridge` / `-protocol-discovery` / `-background-media` (TV AVD, new APK) | 1 / 1 / 4 PASS, same as the TV baseline |
| Pixel 10, real provider A (`adb install -r`, data kept) | PASS. HEVC channels play with the HTML5 player. With Video.js selected, the HEVC channel shows the unsupported-codec diagnostic and the external-player offer instead of loading forever. The open-GOP channel shows the decode diagnostic and the external-player offer. In VLC with default settings (hardware decoding) the open-GOP channel plays sound over a black screen; with VLC's hardware acceleration disabled (software decoding) it plays normally. No other player tested |

### Phase 2 (later, after roadmap step 2, security): native Media3 player
The only in-app fix for open-GOP H.264 (and for AC3/E-AC3 and raw `.ts`).
A separate plan, not started:
- add `media3-exoplayer`, `-hls`, `-ui` to `tv-shell/android/app` (about
  2–3 MB of APK);
- a `NativePlayerActivity` with `PlayerView`; Back returns to the WebView;
- offered first in `ExternalOffer` ("Play with built-in player"); later,
  optionally route there automatically after a decode error;
- the URL arrives through the existing `onPlaybackError` bridge; logs go
  through `DiagnosticRedactor`;
- **verify on the exact open-GOP stream first.** The phone's hardware
  decoder (MediaCodec) can't start it, and software decoding can (VLC: sound
  and black screen with hardware decoding, normal playback in software).
  Media3 uses MediaCodec by default, so it will likely need a
  software-decoder fallback when the hardware decoder fails, e.g. the Media3
  FFmpeg decoder extension (more APK size, and a native build or a
  prebuilt);
- trade-off: no app overlay, EPG or zapping inside it unless bridged later.
  Electron and PWA are unaffected.

Rejected: streaming through the loopback backend or `shouldInterceptRequest`
(nothing network-side fails), and patching hls.js to drop leading pictures
(speculative; belongs upstream).

## Fix: phone live lists that don't scroll and share the screen with a placeholder (2026-11-16)

Found on the phone AVD (`LisBora_API37`, 411×840 CSS px) with the mock
provider: on Xtream Live › a category, the channel list could not be scrolled
and the lower half of the screen showed the empty "Please select a channel to
start playback" panel.

### Diagnosis (measured on the AVD)
| Box | Size |
|---|---|
| `.sidebar` | 353 px, capped by the live-layout mixin's `max-height: 42vh` |
| `app-portal-channels-list` | 295 px, `overflow: hidden` |
| `cdk-virtual-scroll-viewport` | **670 px**: global `.scroll-viewport-portals { min-height: calc(100vh - 170px) !important }` in `apps/web/src/styles.scss` |
| `.content-container` (placeholder) | 379 px (`flex: 1 1 auto`, 240 px floor) |

- The viewport was taller than the box that clips it, so it had nothing to
  scroll and the rows past 295 px could not be reached, even while playing.
  The same min-height was also what held the phone list open: without it the
  list fell to its 72 px floor under the player.
- The mixin stacked the content pane under the list even when it only held
  the placeholder. M3U did the same with its 50/50 split (placeholder on top).

### Fix (`fix(ui)` commit, CSS only, all rules ≤640 px)
- `styles.scss`: no min-height on `.scroll-viewport-portals` at phone width.
- Xtream live layout: the phone list states its own share, `height: 42vh`
  (the playing split stays 353 / 379 px).
- Live-layout mixin: with an expanded list and a content pane that holds
  only the placeholder, the pane is hidden and the list takes the whole body.
  This covers Xtream, Stalker, the Favorites/Recent live tab and M3U. A
  collapsed list keeps its restore state. M3U's deferred-list skeleton keeps
  the old split (see below).
- E2E (`mobile-layout.e2e.ts`): new portrait Xtream, M3U and Stalker cases;
  the landscape case now checks the idle list, then the playing split. The
  new checks failed on the unfixed tree (placeholder pane visible).

### The M3U skeleton and the idle trigger
The first version expanded M3U's loading skeleton to full height too.
`android-touch.mjs` then failed twice at "synthetic playlist did not load".
- The M3U list is a `@defer` (on idle) block. On the AVD, while the skeleton
  shimmers, the page sometimes gets no idle time at all (`requestIdleCallback`
  never fires for 40 s+), so the list doesn't render.
- **This happens with the unfixed APK too.** Cold loads: unfixed 5 of 7
  rendered within 1.1 s, the others stayed stuck for 40 s+; the first fixed
  version 3 of 8.
- The final version keeps the skeleton at the old 50/50 split (the loading
  phase is unchanged), and the list takes the full height once it renders.
  Cold loads: 2 of 3 within 1.1 s. During loading, 366 / 366 px; after,
  732 px.
- Follow-up (upstream, not done): the deferred M3U list can wait 40 s+ for
  idle on a slow device. A timer fallback on the `@defer` trigger would cap it.

### Results
| Check | Result |
|---|---|
| lint / typecheck:web / test:frontend / build:frontend:pwa | pass |
| release notes | valid (`.changes/live-phone-channel-list.md`) |
| `mobile-layout.e2e.ts` (9 tests) | pass with `--workers=2`. In one run with the default workers and the AVD running, 5 setup steps timed out; they pass on a rerun |
| `inline-player-layout.e2e.ts`, `xtream.e2e.ts` channel scrollbar (desktop) | pass |
| Phone AVD, Xtream | idle: list 732 px, no placeholder. Playing: 353 / 379 px, the list scrolls to its last row (`scrollTop` 0 → 128.8, the maximum). Before: rows 5 and 6 of 6 cut off, no scrolling |
| Phone AVD, M3U | idle: list 732 px. Playing: 366 / 366 px, as before. A collapsed list shows "The channels list is hidden" with its restore button |
| Phone AVD, landscape (864 px) | desktop side-by-side layout, unchanged |
| Virtual list growing back (Chromium, 2 736 px Xtream list) | no blank rows at any scroll depth after the list grows without a window resize |
| `android-touch.mjs` phone | 6/7 PASS. The 7th, "Back did not return to the page" (launcher shortcut), **fails the same way with the unfixed APK today** |
| `tv-remote.mjs` / `tv-longlist.mjs` (Chromium, devserver TV, `width=1280`) | 133 / 19 checks passed |
| TV AVD screens | not re-run: every rule is inside `max-width: 640px`, and the TV page is pinned at 1280 or 960 CSS px |

The check APKs were built with JDK 21 (no JDK 17 on this machine any more;
web assets only).
