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
