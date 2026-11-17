/** Requires a debug APK on a test emulator. Clears the app's data (cold start from a fresh profile).
 * ANDROID_HOME=/path/to/sdk node tv-shell/tests/android-loopback-secret.mjs emulator-5554
 *
 * 1. Cold starts: the /api script is registered before the page loads, so the bootstrap probe of
 *    /api/v1/instance is never refused (a refusal would leave the app in server mode for good).
 * 2. Other local servers: page requests for /api on another loopback port are blocked, other local
 *    URLs (a local proxy's streams) still load, and nothing, a redirect included, delivers the
 *    secret there. An `adb reverse`d listener on the host plays another app's server on the device.
 * 3. Other processes: requests without the secret, over `adb forward` and from `adb shell` (its own
 *    UID, like another app), get 403; a previous launch's secret is refused too.
 * 4. Fallback port: with the saved port taken, the script follows the port the server bound.
 */
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createServer } from 'node:http';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { _android } from '@playwright/test';

const PKG = 'org.tvshell.iptvnator';
const REFUSAL = 'refused an /api request without the shell secret';
const serial = process.argv[2];
assert.ok(serial?.startsWith('emulator-') && process.env.ANDROID_HOME, 'Set ANDROID_HOME and pass an emulator serial.');
const adb = (...args) => execFileSync(join(process.env.ANDROID_HOME, 'platform-tools/adb'), ['-s', serial, ...args], { encoding: 'utf8', timeout: 30000 }).trim();

// Another app's local server: the device reaches it as 127.0.0.1:<local> (adb reverse), and as
// 10.0.2.2:<host> (the emulator's address for the host), which is not loopback for the WebView.
const seen = [];
let local;
const other = createServer((req, res) => {
    seen.push({ path: req.url, secret: req.headers['x-tvshell-api'] ?? null, cookie: req.headers.cookie ?? '' });
    if (req.url.startsWith('/redirect/')) {
        res.writeHead(302, { Location: `http://127.0.0.1:${local}/api/leak-redirect-${req.url.slice('/redirect/'.length)}` }).end();
        return;
    }
    // Credentialed CORS: a request the guard let through would also be readable by the page.
    res.writeHead(200, { 'Content-Type': 'text/plain', 'Access-Control-Allow-Origin': req.headers.origin ?? '*',
        'Access-Control-Allow-Credentials': 'true', 'Access-Control-Allow-Headers': '*' }).end('synthetic');
});
await new Promise((resolve) => other.listen(0, '127.0.0.1', resolve));
const host = other.address().port;
let device;
let devices = [];
let forwarded;
let blockedPort;

async function launch({ clear }) {
    adb('shell', 'am', 'force-stop', PKG);
    if (clear) {
        adb('shell', 'pm', 'clear', PKG);
        await delay(2000); // pm clear finishes asynchronously and would kill a process started right away
    }
    adb('logcat', '-c');
    adb('shell', 'am', 'start', '-W', '-n', `${PKG}/.ui.MainActivity`);
    if (!device) {
        devices = await _android.devices({ omitDriverInstall: true });
        device = devices.find((value) => value.serial() === serial);
    }
    assert.ok(device, 'Emulator was not found by Playwright.');
    const page = await (await device.webView({ socketName: `webview_devtools_remote_${adb('shell', 'pidof', PKG)}` })).page();
    await page.waitForFunction(() => !document.getElementById('initial-splash') && document.querySelector('app-root')?.childElementCount > 0,
        null, { timeout: 60000 });
    return page;
}

/** The launch's state as the page sees it, and the secret its /api request carried (if CDP shows it). */
async function inspect(page) {
    const request = page.waitForRequest((value) => value.url().endsWith('/api/health'), { timeout: 10000 }).catch(() => null);
    const state = await page.evaluate(async () => ({
        port: location.port,
        server: localStorage.getItem('iptvnator:source-server'),
        health: (await fetch('/api/health')).status,
        instance: (await fetch('/api/v1/instance')).status,
    }));
    const secret = (await (await request)?.allHeaders())?.['x-tvshell-api'] ?? null;
    if (secret) {
        assert.equal(await page.evaluate((value) => document.documentElement.outerHTML.includes(value)
            || String(window.fetch).includes(value) || Object.keys(window).some((key) => {
            try {
                return String(window[key]).includes(value);
            } catch {
                return false;
            }
        }), secret),
        false, 'page globals do not expose the secret');
    }
    assert.equal(adb('logcat', '-d', '-s', 'TvShell:*').includes(REFUSAL), false, 'an /api request went out without its secret');
    assert.equal(state.server, null, 'the bootstrap probe was refused: the app switched to server mode');
    assert.equal(state.health, 200, 'the page reaches /api');
    assert.equal(state.instance, 404, '/api/v1/instance answers as before');
    return { ...state, secret };
}

try {
    // 1. Cold starts, the first from a fresh profile.
    let page;
    const secrets = [];
    let savedPort;
    for (const clear of [true, false, false]) {
        page = await launch({ clear });
        const state = await inspect(page);
        savedPort ??= state.port;
        assert.equal(state.port, savedPort, 'the app stays on its saved port');
        secrets.push(state.secret);
    }
    console.log('PASS cold starts: no /api request without its secret, no server-mode switch');
    if (secrets.every(Boolean)) {
        assert.equal(new Set(secrets).size, secrets.length, 'each launch has its own secret');
        console.log('PASS per launch: three starts, three secrets');
    }

    // 2. Requests to other local ports are blocked; nothing delivers the secret there.
    local = adb('reverse', 'tcp:0', `tcp:${host}`);
    const outcome = await page.evaluate(async ({ local, host }) => {
        const settle = (element, events) => new Promise((resolve) => {
            const timer = setTimeout(() => resolve('timeout'), 5000);
            for (const name of events) element.addEventListener(name, () => { clearTimeout(timer); resolve(name); }, { once: true });
        });
        const image = (src) => { const img = new Image(); const done = settle(img, ['load', 'error']); img.src = src; return done; };
        const frame = (src) => { const f = document.createElement('iframe'); const done = settle(f, ['load', 'error']); f.src = src; document.body.append(f); return done.finally(() => f.remove()); };
        const video = (src) => { const v = document.createElement('video'); v.muted = true; const done = settle(v, ['error', 'loadedmetadata']); v.src = src; document.body.append(v); return done.finally(() => v.remove()); };
        const get = (url) => fetch(url, { credentials: 'include' }).then((r) => r.status, () => 'blocked');
        const xhr = (url) => new Promise((resolve) => {
            const request = new XMLHttpRequest();
            request.onload = () => resolve(request.status);
            request.onerror = () => resolve('blocked');
            request.open('GET', url);
            request.send();
        });
        const loopback = `http://127.0.0.1:${local}/api`;
        const outside = `http://10.0.2.2:${host}`;
        return {
            control: await get(`${outside}/control`),
            localStream: await get(`http://127.0.0.1:${local}/live/synthetic-stream`),
            fetch: await get(`${loopback}/leak-fetch`),
            xhr: await xhr(`${loopback}/leak-xhr`),
            localhost: await get(`http://localhost:${local}/api/leak-localhost`),
            image: await image(`${loopback}/leak-image.png`),
            frame: await frame(`${loopback}/leak-frame`),
            video: await video(`${loopback}/leak-video.mp4`),
            redirects: [await image(`${outside}/redirect/image`), await frame(`${outside}/redirect/frame`),
                await video(`${outside}/redirect/video`), await get(`${outside}/redirect/fetch`), await xhr(`${outside}/redirect/xhr`)],
        };
    }, { local, host });
    await delay(1500);
    assert.equal(outcome.control, 200, 'the page reaches the listener through a non-loopback address');
    assert.equal(outcome.localStream, 200, "a local server's non-/api URLs still load (local proxy streams)");
    assert.equal(seen.find((entry) => entry.path === '/live/synthetic-stream')?.secret ?? null, null, 'no secret on local stream requests');
    for (const kind of ['fetch', 'xhr', 'localhost']) assert.equal(outcome[kind], 'blocked', `${kind} to another local port`);
    const direct = seen.filter((entry) => entry.path.startsWith('/api/leak-') && !entry.path.startsWith('/api/leak-redirect-'));
    assert.deepEqual(direct.map((entry) => entry.path), [], 'requests to another local port must not leave the WebView');
    const leaked = seen.filter((entry) => entry.secret || entry.cookie.includes('tvshell_api')).map((entry) => entry.path);
    assert.deepEqual(leaked, [], 'the secret reached another local server');
    const redirected = seen.filter((entry) => entry.path.startsWith('/api/leak-redirect-')).map((entry) => entry.path);
    console.log(`PASS other local ports: /api requests blocked, local streams load, the secret never arrived (redirects that reached the server: ${redirected.join(', ') || 'none'})`);

    // 3. Other processes on the device, through adb.
    const port = savedPort;
    forwarded = adb('forward', 'tcp:0', `tcp:${port}`);
    const api = `http://127.0.0.1:${forwarded}/api`;
    const register = { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ url: 'http://synthetic.example' }) };
    const wrong = ['0'.repeat(64), ...secrets.slice(0, -1).filter(Boolean)]; // a made-up secret and the previous launches' secrets
    for (const secret of [null, ...wrong]) {
        for (const [path, init] of [['/health', {}], ['/provider-targets', register], ['/xtream?targetId=synthetic', {}], ['/health', { method: 'OPTIONS' }]]) {
            const response = await fetch(api + path, { ...init, headers: { ...init.headers, ...(secret ? { 'X-TvShell-Api': secret } : {}) } });
            assert.equal(response.status, 403, `${init.method ?? 'GET'} ${path} ${secret ? 'with a wrong or stale secret' : 'without a secret'}`);
        }
    }
    if (secrets.at(-1)) assert.equal((await fetch(`${api}/health`, { headers: { 'X-TvShell-Api': secrets.at(-1) } })).status, 200, 'the current secret works');
    console.log(`PASS adb forward: /api refuses requests without the secret, with a wrong one${secrets[0] ? ' or a previous launch\'s' : ''}`);
    const uid = adb('shell', 'id', '-u');
    if (adb('shell', 'command -v nc || true')) {
        const answer = adb('shell', `printf 'GET /api/health HTTP/1.0\\r\\nHost: 127.0.0.1\\r\\n\\r\\n' | nc -w 5 127.0.0.1 ${port}`);
        assert.match(answer.split('\n')[0], /^HTTP\/1\.[01] 403/, 'adb shell request without the secret');
        console.log(`PASS adb shell (uid ${uid}): /api answers 403 without the secret`);
    } else {
        console.log('SKIP adb shell leg: no nc on this image (the adb forward leg covers other processes)');
    }
    adb('forward', '--remove', `tcp:${forwarded}`);
    forwarded = null;

    // 4. Saved port taken (adbd holds it through a reverse): this run serves on a fallback port.
    adb('shell', 'am', 'force-stop', PKG);
    for (let attempt = 0; !blockedPort; attempt++) {
        try {
            adb('reverse', `tcp:${savedPort}`, `tcp:${host}`);
            blockedPort = savedPort;
        } catch (error) {
            if (attempt >= 20) throw error; // the stopped process may hold the port for a moment
            await delay(500);
        }
    }
    page = await launch({ clear: false });
    const fallback = await inspect(page);
    assert.notEqual(fallback.port, savedPort, 'the saved port was taken: the server should be on a fallback port');
    console.log(`PASS fallback port ${fallback.port}: the /api script follows the bound origin`);
} finally {
    if (forwarded) adb('forward', '--remove', `tcp:${forwarded}`);
    if (local) adb('reverse', '--remove', `tcp:${local}`);
    if (blockedPort) {
        adb('reverse', '--remove', `tcp:${blockedPort}`);
        adb('shell', 'am', 'force-stop', PKG); // the next start returns to the saved port and its data
    }
    for (const value of devices) await value.close(); // each device object polls adb until closed
    other.closeAllConnections(); // the WebView keeps its connections to the listener alive
    other.close();
}
