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

## Where step 1 stopped (2026-11-15, end of session)
- **Done:** commits 1–3 (split, touch mode and viewport, bars and insets,
  keyboard, fullscreen orientation).
- **Uncommitted work in progress for commit 4** ("skip the remote-control
  focus layer in touch mode"), left in the working tree:
  - `tv-shell/android/app/src/main/assets/tvshell/tv.js`: with
    `data-tvshell-mode="touch"`, `init()` adds `tv-shell-touch` and skips the
    style layer, the focus mirror, the D-pad key handler and the initial
    focus (moved into `installRemoteControl()`). `scheduleRefocus()` is a
    no-op, and `api.back()` skips its focus side effects. All bridge hooks
    are kept.
  - `tv-shell/tests/tv-touch.mjs`: a new Chromium test under Pixel 7
    emulation against `node tv-shell/devserver/server.mjs --www dist/apps/web
    --port 43332 --touch`. It passes all checks with the new `tv.js`; six
    checks fail with the committed `tv.js`.
- **Still to do for commit 4:**
  - route `MainActivity`/`FullscreenController`/`ExternalOffer`
    `webView.requestFocus(View.FOCUS_DOWN)` through a touch-aware
    `focusPage()`. The row-30 list scroll doesn't reproduce in Chromium, so
    it most likely comes from this native call;
  - add a ring and list-at-top check to `android-touch.mjs`;
  - re-run the TV Chromium suites (`tv-remote.mjs`, `tv-longlist.mjs`, which
    now load the modified `tv.js`), the TV parity run and the phone/tablet
    checks;
  - add `tv-touch.mjs` to `.github/workflows/build-tv-shell-apk.yml`.
- **Next:**
  - commit 5 (launcher shortcut to the shell settings);
  - commit 6 (README, `.changes/` note, final summary here);
  - then one upstream rebase, and re-run the TV checks and the phone
    checklist.
- **Findings so far for the remaining checklist items:**
  - item 8: a phone in landscape (864–914 CSS px, above the app's 640 px
    breakpoint) still shows the desktop header with `Ctrl+K` and the
    shortcuts button. That needs an apps/web follow-up, gated by
    `(pointer: coarse)`;
  - item 9: no change needed.
- **To resume:**
  - restart the AVDs: phone `LisBora_API37` (`emulator-5554`), TV
    `TvShell_TV_API34` (`-port 5556`), tablet `TvShell_Tablet_API37`
    (`-port 5558`);
  - run `WWW=$PWD/dist/apps/web tv-shell/devserver/run-dev.sh` (mock on
    43331) and the touch devserver above;
  - build with JDK 17. The TV baseline shots and the screenshot and compare
    scripts are in the session scratchpad, not the repo; if they're gone,
    recapture the baseline from `8bc278232`.
