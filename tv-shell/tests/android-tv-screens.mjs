/** TV screen parity: eight screens and a state fingerprint per screen, captured on an Android TV
 * emulator, then compared between two builds made from the same PWA bundle.
 * Capture (clears the app's data; keep run-dev.sh running for the mock provider):
 *   ANDROID_HOME=/path/to/sdk node tv-shell/tests/android-tv-screens.mjs emulator-5554 --out dir [--package id]
 * Compare two captures (no device needed; exits 1 on any difference):
 *   node tv-shell/tests/android-tv-screens.mjs --compare baseline-dir candidate-dir
 * Two captures of one build normally have identical facts and 0 differing pixels. The initial
 * focus after a page load (01-home, 08-settings) races Angular's first render, so capture each
 * build several times and compare where the ring lands across the runs.
 */
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const argAfter = (flag) => {
    const at = process.argv.indexOf(flag);
    return at > 0 ? process.argv[at + 1] : null;
};

/** Pixel comparison of two PNG files: share of pixels with any differing channel, plus a diff image. */
async function comparePixels(sharp, a, b, diffPath) {
    const [x, y] = await Promise.all([a, b].map((file) => sharp(file).ensureAlpha().raw().toBuffer({ resolveWithObject: true })));
    if (x.info.width !== y.info.width || x.info.height !== y.info.height) return { percent: 100, size: `${x.info.width}x${x.info.height} vs ${y.info.width}x${y.info.height}` };
    const out = Buffer.alloc(x.data.length);
    let differing = 0;
    for (let i = 0; i < x.data.length; i += 4) {
        const same = x.data[i] === y.data[i] && x.data[i + 1] === y.data[i + 1] && x.data[i + 2] === y.data[i + 2] && x.data[i + 3] === y.data[i + 3];
        if (!same) differing++;
        const grey = (x.data[i] + x.data[i + 1] + x.data[i + 2]) / 12;
        out.set(same ? [grey, grey, grey, 255] : [255, 0, 0, 255], i);
    }
    if (differing) await sharp(out, { raw: x.info }).png().toFile(diffPath);
    return { percent: (100 * differing) / (x.data.length / 4), differing };
}

async function compare(baseDir, candDir) {
    const { default: sharp } = await import('sharp');
    const base = JSON.parse(readFileSync(join(baseDir, 'facts.json'), 'utf8'));
    const cand = JSON.parse(readFileSync(join(candDir, 'facts.json'), 'utf8'));
    let different = false;
    for (const name of Object.keys(base)) {
        if (!cand[name]) {
            console.log(`${name}: missing from ${candDir}`);
            different = true;
            continue;
        }
        const keys = [...new Set([...Object.keys(base[name]), ...Object.keys(cand[name])])];
        const changed = keys.filter((key) => JSON.stringify(base[name][key]) !== JSON.stringify(cand[name][key]));
        const px = await comparePixels(sharp, join(baseDir, `${name}.png`), join(candDir, `${name}.png`), join(candDir, `diff-${name}.png`));
        console.log(`${name}: facts ${changed.length ? 'DIFFER' : 'identical'}, pixels ${px.percent.toFixed(3)}%${px.size ? ` (${px.size})` : ''}`);
        for (const key of changed) console.log(`  ${key}: ${JSON.stringify(base[name][key])} -> ${JSON.stringify(cand[name][key])}`);
        different ||= changed.length > 0 || px.percent > 0;
    }
    const html = ['index.html'].filter((file) => existsSync(join(baseDir, file)));
    for (const file of html) {
        const same = readFileSync(join(baseDir, file)).equals(readFileSync(join(candDir, file)));
        console.log(`served ${file}: ${same ? 'byte-identical' : 'DIFFERS'}`);
        different ||= !same;
    }
    console.log(different ? 'RESULT differences found' : 'RESULT identical');
    process.exitCode = different ? 1 : 0;
}

async function capture(serial, out, pkg) {
    const { _android } = await import('@playwright/test');
    assert.ok(serial?.startsWith('emulator-'), 'An explicit emulator serial is required; physical devices are excluded.');
    assert.ok(process.env.ANDROID_HOME, 'Set ANDROID_HOME to the Android SDK.');
    mkdirSync(out, { recursive: true });
    const adbPath = join(process.env.ANDROID_HOME, 'platform-tools/adb');
    const adb = (...args) => execFileSync(adbPath, ['-s', serial, ...args], { encoding: 'utf8', timeout: 30000, maxBuffer: 64 << 20 });
    const key = (code) => adb('shell', 'input', 'keyevent', code);
    const resumed = () => /topResumedActivity=\S+ \S+ (\S+)/.exec(adb('shell', 'dumpsys', 'activity', 'activities'))?.[1] ?? '';
    const imeShown = () => /mInputShown=true/.test(adb('shell', 'dumpsys', 'input_method'));
    const rotation = () => /mCurrentRotation=(\w+)/.exec(adb('shell', 'dumpsys', 'window', 'displays'))?.[1] ?? 'unknown';
    async function until(check, message, tries = 60) {
        for (let i = 0; i < tries; i++) {
            if (await check()) return;
            await delay(250);
        }
        assert.fail(message);
    }
    assert.match(adb('shell', 'pm', 'has-feature', 'android.software.leanback'), /true/, 'This check is for an Android TV emulator.');

    const facts = {};
    let page;
    /** Settles the page (no caret blink), then records the screenshot and the state fingerprint. */
    async function shot(name, settleMs = 2000) {
        await page.evaluate(() => {
            if (document.getElementById('tv-screens-caret')) return;
            const style = document.createElement('style');
            style.id = 'tv-screens-caret';
            style.textContent = '* { caret-color: transparent !important; }';
            document.head.append(style);
        });
        await delay(settleMs);
        const state = await page.evaluate(() => {
            const describe = (el) => {
                if (!el || el === document.body) return el ? 'body' : null;
                const id = el.id ? `#${el.id}` : '';
                const test = el.getAttribute('data-test-id') ? `[${el.getAttribute('data-test-id')}]` : '';
                const label = (el.getAttribute('aria-label') || el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 60);
                return `${el.tagName.toLowerCase()}${id}${test} "${label}"`;
            };
            return {
                path: location.pathname.replace(/\/playlists\/[^/]+/, '/playlists/:id'), // the id is random
                viewport: document.querySelector('meta[name=viewport]')?.content ?? null,
                size: [window.innerWidth, window.innerHeight, window.devicePixelRatio, window.visualViewport.scale],
                htmlClasses: [...document.documentElement.classList].sort(),
                rings: [...document.querySelectorAll('.tv-focus')].map(describe),
                active: describe(document.activeElement),
                fullscreen: !!document.fullscreenElement,
            };
        });
        facts[name] = { ...state, ime: imeShown(), rotation: rotation(), activity: resumed() };
        writeFileSync(join(out, `${name}.png`), execFileSync(adbPath, ['-s', serial, 'exec-out', 'screencap', '-p'], { maxBuffer: 64 << 20 }));
        console.log(`captured ${name}: rings ${JSON.stringify(facts[name].rings)}`);
    }
    /** Focuses an element (or a label's focusable row) as the D-pad would, then presses OK. */
    async function ok(locator) {
        await locator.evaluate((el) => (el.closest('button, a[href], input, [tabindex]') || el).focus());
        await delay(300);
        key('KEYCODE_DPAD_CENTER');
    }

    let device;
    adb('reverse', 'tcp:43331', 'tcp:43331');
    try {
        adb('shell', 'pm', 'clear', pkg);
        await delay(2000); // pm clear finishes asynchronously and would kill a process started right away
        adb('shell', 'am', 'start', '-n', `${pkg}/org.tvshell.iptvnator.ui.MainActivity`);
        await until(() => resumed().startsWith(`${pkg}/`), 'the shell did not start');
        device = (await _android.devices({ omitDriverInstall: true })).find((value) => value.serial() === serial);
        assert.ok(device, 'Emulator was not found by Playwright.');
        page = await (await device.webView({ pkg })).page();
        await page.waitForFunction(() => !document.getElementById('initial-splash') && document.querySelector('app-root')?.childElementCount > 0, null, { timeout: 60000 });
        writeFileSync(join(out, 'index.html'), await page.evaluate(() => fetch('/').then((response) => response.text())));
        await shot('01-home', 3000);

        await page.goto(new URL('/workspace/sources', page.url()).href);
        await ok(page.getByRole('button', { name: 'Add M3U', exact: true }));
        const dialog = page.locator('mat-dialog-container');
        await until(() => dialog.isVisible(), 'the add-playlist dialog did not open');
        await shot('02-add-dialog');

        const url = dialog.locator('input').nth(0);
        await ok(url);
        await until(imeShown, 'OK on the URL field did not open the keyboard');
        await shot('03-keyboard');
        await url.fill('http://127.0.0.1:43331/sample.m3u');
        await page.evaluate(() => document.activeElement?.blur());
        await until(() => !imeShown(), 'the keyboard did not close');

        await ok(dialog.getByRole('button', { name: /Add playlist/i }));
        await until(() => page.locator('.channel-name', { hasText: 'Playable clip' }).count(), 'synthetic playlist did not load', 240);
        await shot('04-list', 3000);
        for (let i = 0; i < 3; i++) {
            key('KEYCODE_DPAD_DOWN');
            await delay(400);
        }
        await shot('05-dpad-three-down');

        await ok(page.locator('.channel-name', { hasText: 'Playable clip' }).first());
        await until(() => page.evaluate(() => !!document.querySelector('video')?.currentSrc), 'the clip did not start');
        const freeze = () => page.evaluate(() => new Promise((resolve) => {
            const video = document.querySelector('video');
            video.muted = true;
            video.pause();
            video.addEventListener('seeked', resolve, { once: true });
            video.currentTime = 0.5; // the synthetic clip is 1 s long
        }));
        await until(() => page.evaluate(() => document.querySelector('video').readyState >= 2), 'the clip did not decode');
        await freeze();
        await shot('06-playback', 3000);
        await page.evaluate(() => document.querySelector('video').requestFullscreen());
        await until(() => page.evaluate(() => !!document.fullscreenElement), 'fullscreen did not start');
        await freeze();
        await shot('07-fullscreen', 3000);
        await page.evaluate(() => document.exitFullscreen());
        await until(() => page.evaluate(() => !document.fullscreenElement), 'fullscreen did not end');

        await page.goto(new URL('/workspace/settings', page.url()).href);
        await until(() => page.locator('app-root').evaluate((root) => root.childElementCount > 0), 'settings did not render');
        await shot('08-settings', 3000);
        writeFileSync(join(out, 'facts.json'), JSON.stringify(facts, null, 2) + '\n');
        console.log(`PASS captured ${Object.keys(facts).length} TV screens into ${out}`);
    } finally {
        adb('reverse', '--remove', 'tcp:43331');
        setTimeout(() => process.exit(process.exitCode || 0), 5000).unref(); // _android close() can hang
        await device?.close();
    }
}

if (process.argv[2] === '--compare') await compare(process.argv[3], process.argv[4]);
else await capture(process.argv[2], argAfter('--out') ?? assert.fail('Pass --out dir.'), argAfter('--package') || 'org.tvshell.iptvnator');
