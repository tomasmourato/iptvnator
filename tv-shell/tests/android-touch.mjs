/** Touch-mode checks on a phone or tablet emulator. Requires a debug APK and the mock provider
 * (run-dev.sh), then:
 * ANDROID_HOME=/path/to/sdk node tv-shell/tests/android-touch.mjs emulator-5554 [--shots dir]
 */
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { _android } from '@playwright/test';

const serial = process.argv[2];
assert.ok(serial?.startsWith('emulator-'), 'An explicit emulator serial is required; physical devices are excluded.');
assert.ok(process.env.ANDROID_HOME, 'Set ANDROID_HOME to the Android SDK.');
const shotsAt = process.argv.indexOf('--shots');
const shots = shotsAt > 0 ? process.argv[shotsAt + 1] : null;
if (shots) mkdirSync(shots, { recursive: true });
const adbPath = join(process.env.ANDROID_HOME, 'platform-tools/adb');
const adb = (...args) => execFileSync(adbPath, ['-s', serial, ...args], { encoding: 'utf8', timeout: 30000, maxBuffer: 64 << 20 });
const shot = (name) => shots && writeFileSync(join(shots, `${name}.png`), execFileSync(adbPath, ['-s', serial, 'exec-out', 'screencap', '-p'], { maxBuffer: 64 << 20 }));
const rotate = (quarterTurns) => adb('shell', 'settings', 'put', 'system', 'user_rotation', String(quarterTurns));
async function until(check, message, tries = 40) {
    for (let i = 0; i < tries; i++) {
        if (await check()) return;
        await delay(250);
    }
    assert.fail(message);
}

/** On-screen bounds [left, top, right, bottom] of the shell's WebView, from a UI dump. */
async function webViewBounds() {
    for (let attempt = 0; attempt < 6; attempt++) {
        try {
            adb('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/touch-ui.xml');
            const xml = adb('exec-out', 'cat', '/sdcard/touch-ui.xml');
            const node = (xml.match(/<node\b[^>]+>/g) || []).find((n) => n.includes('class="android.webkit.WebView"'));
            if (node) return /bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/.exec(node).slice(1).map(Number);
        } catch {
            // "could not get idle state" while the page animates
        }
        await delay(700);
    }
    assert.fail('WebView not found in the UI dump');
}
/** Frame [top, bottom] and visibility of a system inset source (statusBars, navigationBars, ime). */
function insetSource(type) {
    const match = new RegExp(`type=${type} frame=\\[\\d+,(\\d+)\\]\\[\\d+,(\\d+)\\] visible=(\\w+)`).exec(adb('shell', 'dumpsys', 'window'));
    return match ? { top: +match[1], bottom: +match[2], visible: match[3] === 'true' } : null;
}
/** A real touchscreen tap on an element (CSS rect → device px, offset by the WebView's bounds). */
async function tap(page, locator) {
    const r = await locator.evaluate((el) => {
        el.scrollIntoView({ block: 'center' });
        const b = el.getBoundingClientRect();
        return { x: b.left + b.width / 2, y: b.top + b.height / 2, k: window.visualViewport.scale * window.devicePixelRatio };
    });
    const [left, top] = await webViewBounds();
    adb('shell', 'input', 'tap', String(Math.round(left + r.x * r.k)), String(Math.round(top + r.y * r.k)));
}

const [, density] = /(\d+)\s*$/.exec(adb('shell', 'wm', 'density').trim());
const [, widthPx] = /(\d+)x\d+/.exec(adb('shell', 'wm', 'size'));
const screenDp = Math.round(+widthPx / (+density / 160));
let device;
adb('reverse', 'tcp:43331', 'tcp:43331');
adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0');
rotate(0);
try {
    adb('shell', 'pm', 'clear', 'org.tvshell.iptvnator');
    await delay(2000); // pm clear finishes asynchronously and would kill a process started right away
    adb('shell', 'am', 'start', '-n', 'org.tvshell.iptvnator/.ui.MainActivity');
    await until(() => /topResumedActivity=.*org\.tvshell\.iptvnator/.test(adb('shell', 'dumpsys', 'activity', 'activities')), 'the shell did not start');
    device = (await _android.devices({ omitDriverInstall: true })).find((value) => value.serial() === serial);
    assert.ok(device, 'Emulator was not found by Playwright.');
    const page = await (await device.webView({ pkg: 'org.tvshell.iptvnator' })).page();
    await page.waitForFunction(() => !document.getElementById('initial-splash') && document.querySelector('app-root')?.childElementCount > 0, null, { timeout: 60000 });
    await delay(1500);

    // 1. Device-width page at scale 1: the app's own responsive layout applies, pinch-zoom works.
    const layout = await page.evaluate(() => ({
        viewport: document.querySelector('meta[name=viewport]')?.content,
        width: window.innerWidth,
        scale: window.visualViewport.scale,
    }));
    assert.equal(layout.viewport, 'width=device-width, initial-scale=1');
    assert.ok(Math.abs(layout.width - screenDp) <= 2, `page width ${layout.width} CSS px follows the screen (${screenDp} dp)`);
    assert.equal(layout.scale, 1);
    if (layout.width <= 640) {
        await until(() => page.locator('.workspace-header').isVisible(), 'workspace header did not render');
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), true, 'no horizontal page scroll at phone width');
    }
    shot('touch-01-home');
    // Pinch coordinates are relative to the visual viewport, so aim at its centre each time.
    const cdp = await page.context().newCDPSession(page);
    const pinch = async (scaleFactor) => {
        const [x, y] = await page.evaluate(() => [window.visualViewport.width / 2, window.visualViewport.height / 2]);
        await cdp.send('Input.synthesizePinchGesture', { x, y, scaleFactor, gestureSourceType: 'touch' });
    };
    await pinch(2);
    await until(async () => (await page.evaluate(() => window.visualViewport.scale)) > 1.2, 'pinch did not zoom the page');
    shot('touch-02-pinched');
    await pinch(0.5);
    await until(async () => (await page.evaluate(() => window.visualViewport.scale)) < 1.05, 'pinch out did not return to scale 1');
    console.log(`PASS device-width page (${layout.width} CSS px at scale 1) and pinch-zoom`);

    // 2. Rotation keeps the page and the playback (no reload).
    await page.goto(new URL('/workspace/sources', page.url()).href);
    await page.getByRole('button', { name: /Add M3U/i }).click();
    const dialog = page.locator('mat-dialog-container');
    await dialog.locator('input').nth(0).fill('http://127.0.0.1:43331/sample.m3u');
    await dialog.getByRole('button', { name: /Add playlist/i }).click();
    await until(() => page.locator('.channel-name', { hasText: 'Playable clip' }).count(), 'synthetic playlist did not load', 80);

    // 5. No remote-control focus layer: no ring, nothing made focusable, the list opens at the top.
    await delay(1500); // the TV layer would have restored its focus by now
    const fresh = await page.evaluate(() => ({
        touchClass: document.documentElement.classList.contains('tv-shell-touch'),
        rings: document.querySelectorAll('.tv-focus').length,
        madeFocusable: document.querySelectorAll('[data-tv-focusable]').length,
        top: document.querySelector('cdk-virtual-scroll-viewport')?.scrollTop ?? -1,
        first: document.querySelector('.channel-name')?.textContent?.trim(),
    }));
    shot('touch-03a-list');
    assert.ok(fresh.touchClass, 'tv.js runs in touch mode');
    assert.equal(fresh.rings, 0, 'no TV focus ring after loading the playlist');
    assert.equal(fresh.madeFocusable, 0, 'nothing made focusable for the D-pad');
    assert.equal(fresh.top, 0, 'the channel list opens at the top');
    assert.match(fresh.first || '', /^1\./, 'the first row is channel 1');
    console.log('PASS no TV focus ring and the channel list opens at the top');

    await page.locator('.channel-name', { hasText: 'Playable clip' }).first().click();
    await until(() => page.evaluate(() => !!document.querySelector('video')?.currentSrc), 'the clip did not start');
    assert.equal(await page.evaluate(() => document.querySelectorAll('.tv-focus').length), 0, 'no TV focus ring after opening a channel');
    await page.evaluate(() => {
        const video = document.querySelector('video');
        video.loop = true;
        video.muted = true;
        window.__touchPageMarker = 'kept';
        return video.play();
    });
    const before = await page.evaluate(() => ({ url: location.pathname, width: window.innerWidth }));
    rotate(1);
    await until(async () => (await page.evaluate(() => window.innerWidth)) !== before.width, 'the page did not follow the rotation');
    await delay(1500);
    const t0 = await page.evaluate(() => document.querySelector('video')?.currentTime ?? -1);
    await delay(1200);
    const after = await page.evaluate(() => ({
        marker: window.__touchPageMarker,
        url: location.pathname,
        width: window.innerWidth,
        playing: !!document.querySelector('video') && !document.querySelector('video').paused,
        time: document.querySelector('video')?.currentTime ?? -1,
    }));
    shot('touch-03-rotated-playing');
    assert.equal(after.marker, 'kept', 'rotation reloaded the page');
    assert.equal(after.url, before.url);
    assert.ok(after.playing && after.time !== t0, 'playback continues after the rotation');
    rotate(0);
    await until(async () => (await page.evaluate(() => window.innerWidth)) === before.width, 'the page did not rotate back');
    assert.equal(await page.evaluate(() => window.__touchPageMarker), 'kept', 'rotating back reloaded the page');
    console.log(`PASS rotation keeps the page and playback (${before.width} → ${after.width} CSS px, no reload)`);

    // 3. System bars stay visible outside fullscreen and the page sits between them; no immersive prompt.
    const status = insetSource('statusBars');
    const nav = insetSource('navigationBars');
    assert.ok(status?.visible, 'status bar visible');
    const [, webTop, , webBottom] = await webViewBounds();
    assert.ok(webTop >= status.bottom, `page starts below the status bar (${webTop} >= ${status.bottom})`);
    if (nav?.visible) assert.ok(webBottom <= nav.top, `page ends above the navigation bar (${webBottom} <= ${nav.top})`);
    assert.doesNotMatch(adb('exec-out', 'cat', '/sdcard/touch-ui.xml'), /Viewing full screen/, 'no immersive-mode prompt');
    shot('touch-04-bars');
    console.log(`PASS system bars visible, page between them (${webTop}..${webBottom} px)`);

    // 6a. Fullscreen video hides the bars and turns to landscape; leaving it restores both.
    await page.evaluate(() => document.querySelector('video').requestFullscreen());
    await until(async () => (await page.evaluate(() => window.innerWidth > window.innerHeight)), 'fullscreen video did not turn to landscape');
    await until(() => insetSource('statusBars')?.visible === false, 'bars still visible in fullscreen');
    shot('touch-05-fullscreen');
    await page.evaluate(() => document.exitFullscreen());
    await until(async () => (await page.evaluate(() => window.innerWidth)) === before.width, 'leaving fullscreen did not restore the orientation');
    await until(() => insetSource('statusBars')?.visible === true, 'bars did not come back after fullscreen');
    console.log('PASS fullscreen video turns to landscape without bars, and back');

    // 6b. The soft keyboard resizes the page: the focused field stays visible above it.
    await page.goto(new URL('/workspace/settings', page.url()).href);
    const search = page.locator('input').first();
    await until(() => search.isVisible(), 'settings search field missing');
    const heightBefore = await page.evaluate(() => window.innerHeight);
    await tap(page, search);
    await until(() => /mInputShown=true/.test(adb('shell', 'dumpsys', 'input_method')), 'the soft keyboard did not open');
    await until(async () => (await page.evaluate(() => window.innerHeight)) < heightBefore, 'the page did not resize for the keyboard');
    const ime = insetSource('ime');
    const [, , , bottomWithIme] = await webViewBounds();
    assert.ok(!ime || bottomWithIme <= ime.top + 1, `page ends above the keyboard (${bottomWithIme} <= ${ime?.top})`);
    assert.ok(await page.evaluate(() => document.activeElement.getBoundingClientRect().bottom <= window.innerHeight), 'focused field visible');
    shot('touch-06-keyboard');
    adb('shell', 'input', 'keyevent', '111'); // Escape: close the keyboard
    console.log(`PASS the keyboard resizes the page (${heightBefore} → ${await page.evaluate(() => window.innerHeight)} CSS px)`);
} finally {
    rotate(0);
    adb('reverse', '--remove', 'tcp:43331');
    setTimeout(() => process.exit(process.exitCode || 0), 5000).unref(); // _android close() can hang
    await device?.close();
}
