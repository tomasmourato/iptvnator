/** An Xtream portal with ~90 MiB get_vod_streams and get_series answers (synthetic, from the mock
 * provider's --large-catalog) imports through the APK's embedded backend on a phone or tablet
 * emulator: every tab lists its items and no error view appears. Requires a debug APK; starts its
 * own mock provider on port 43339. Clears the app's data on the emulator.
 * ANDROID_HOME=/path/to/sdk node tv-shell/tests/android-large-catalog.mjs emulator-5554 [--mib 90]
 */
import assert from 'node:assert/strict';
import { execFileSync, spawn } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { _android } from '@playwright/test';
import { largeCatalogCount } from '../devserver/large-catalog.mjs';

const serial = process.argv[2];
assert.ok(serial?.startsWith('emulator-'), 'An explicit emulator serial is required; physical devices are excluded.');
assert.ok(process.env.ANDROID_HOME, 'Set ANDROID_HOME to the Android SDK.');
const mibAt = process.argv.indexOf('--mib');
const MIB = mibAt > 0 ? Number(process.argv[mibAt + 1]) : 90;
const PORT = 43339; // clear of the shell's own 43330-43334 and of run-dev.sh's mock on 43331
const PROVIDER = `http://127.0.0.1:${PORT}`;
const adbPath = join(process.env.ANDROID_HOME, 'platform-tools/adb');
const adb = (...args) => execFileSync(adbPath, ['-s', serial, ...args], { encoding: 'utf8', timeout: 30000 });
async function until(check, message, tries = 40, every = 250) {
    for (let i = 0; i < tries; i++) {
        if (await check()) return;
        await delay(every);
    }
    assert.fail(message);
}

const mockPath = join(dirname(fileURLToPath(import.meta.url)), '../devserver/mock-provider.mjs');
const mock = spawn(process.execPath, [mockPath, '--port', String(PORT), '--large-catalog', String(MIB)], { stdio: ['ignore', 'pipe', 'inherit'] });
await new Promise((resolve, reject) => {
    mock.stdout.once('data', resolve);
    mock.once('exit', (code) => reject(new Error(`mock provider exited (${code})`)));
});
let device;
adb('reverse', `tcp:${PORT}`, `tcp:${PORT}`);
try {
    adb('shell', 'pm', 'clear', 'org.tvshell.iptvnator');
    await delay(2000); // pm clear finishes asynchronously and would kill a process started right away
    adb('shell', 'am', 'start', '-n', 'org.tvshell.iptvnator/.ui.MainActivity');
    await until(() => /topResumedActivity=.*org\.tvshell\.iptvnator/.test(adb('shell', 'dumpsys', 'activity', 'activities')), 'the shell did not start');
    device = (await _android.devices({ omitDriverInstall: true })).find((value) => value.serial() === serial);
    assert.ok(device, 'Emulator was not found by Playwright.');
    const page = await (await device.webView({ pkg: 'org.tvshell.iptvnator' })).page();
    await page.waitForFunction(() => !document.getElementById('initial-splash') && document.querySelector('app-root')?.childElementCount > 0, null, { timeout: 60000 });

    // The catalogue answers as the backend served them: status, size and the request to replay.
    const answers = {};
    page.on('response', (response) => {
        const url = new URL(response.url());
        const action = url.searchParams.get('action');
        if (url.pathname.endsWith('/api/xtream') && (action === 'get_vod_streams' || action === 'get_series')) {
            answers[action] = { url: response.url(), status: response.status(), bytes: Number(response.headers()['content-length'] ?? -1) };
        }
    });

    await page.goto(new URL('/workspace/sources', page.url()).href);
    await page.getByRole('button', { name: /Add Xtream/i }).first().click();
    const dialog = page.locator('mat-dialog-container');
    await dialog.locator('#title').fill('Large synthetic portal');
    await dialog.locator('#serverUrl').fill(PROVIDER);
    await dialog.locator('#username').fill('user');
    await dialog.locator('#password').fill('pass');
    await dialog.getByRole('button', { name: /Add playlist/i }).click();
    const started = Date.now();
    const errorView = page.locator('app-playlist-error-view');
    await until(async () => (answers.get_vod_streams && answers.get_series) || (await errorView.count()) > 0, 'the import neither finished nor failed', 240, 500);
    const seconds = ((Date.now() - started) / 1000).toFixed(1);

    // 1. Both answers carry the whole catalogue: the envelope parses and holds every synthetic item.
    for (const [action, kind] of [['get_vod_streams', 'vod'], ['get_series', 'series']]) {
        const answer = answers[action];
        assert.ok(answer, `${action} was never requested: the import stopped before it`);
        assert.equal(answer.status, 200, `${action} status`);
        assert.ok(answer.bytes > MIB * 1024 * 1024, `${action} answered ${answer.bytes} bytes, not the whole catalogue`);
        const parsed = await page.evaluate(async (url) => {
            const body = await (await fetch(url)).json();
            return { items: Array.isArray(body.payload) ? body.payload.length : -1, code: body.code ?? null, first: body.payload?.[0]?.name };
        }, answer.url);
        assert.equal(parsed.code, null, `${action} error code`);
        assert.equal(parsed.items, largeCatalogCount(kind, MIB), `${action} item count`);
        assert.match(parsed.first, /sint[ée]tic[oa] 1 – Ação$/, `${action} non-ASCII names intact`);
        console.log(`PASS ${action}: ${parsed.items} items in ${(answer.bytes / 1048576).toFixed(1)} MiB`);
    }

    // 2. The import completes: every tab lists its items, no error view.
    const portal = page.url().replace(/\/(vod|live|series)(\?.*)?$/, '');
    for (const [tab, text] of [['vod', /Filme sintético \d+/], ['series', /Série sintética \d+/], ['live', /News Channel \d+/]]) {
        if (!page.url().includes(`/${tab}`)) await page.goto(`${portal}/${tab}`);
        await until(async () => text.test(await page.evaluate(() => document.body.innerText)) || (await errorView.count()) > 0,
            `the ${tab} tab shows neither items nor an error`, 120, 500);
        assert.equal(await errorView.count(), 0, `the ${tab} tab shows an error view`);
        console.log(`PASS the ${tab} tab lists its items`);
    }
    console.log(`PASS large catalogue imported (both answers within ${seconds} s)`);
} finally {
    adb('reverse', '--remove', `tcp:${PORT}`);
    mock.kill();
    setTimeout(() => process.exit(process.exitCode || 0), 5000).unref(); // _android close() can hang
    await device?.close();
}
