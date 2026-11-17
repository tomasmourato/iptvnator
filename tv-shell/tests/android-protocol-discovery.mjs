/** Requires a debug APK on a test emulator.
 * ANDROID_HOME=/path/to/sdk node tv-shell/tests/android-protocol-discovery.mjs emulator-5554
 */
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { join } from 'node:path';
import { _android } from '@playwright/test';

const serial = process.argv[2];
assert.ok(serial?.startsWith('emulator-') && process.env.ANDROID_HOME, 'Set ANDROID_HOME and pass an emulator serial.');
const adb = (...args) => execFileSync(join(process.env.ANDROID_HOME, 'platform-tools/adb'), ['-s', serial, ...args], { encoding: 'utf8', timeout: 20000 }).trim();

const closedPort = 44332;
const closed = `https://127.0.0.1:${closedPort}`;
const mock = createServer((_, res) => res.writeHead(302, { Location: closed }).end());
mock.on('clientError', (_, socket) => socket.end('HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n'));
mock.listen(0, '127.0.0.1');
let reversed;
let devices = [];
try {
    await once(mock, 'listening');
    adb('shell', 'am', 'start', '-W', '-n', 'org.tvshell.iptvnator/.ui.MainActivity');
    reversed = adb('reverse', 'tcp:0', `tcp:${mock.address().port}`);
    devices = await _android.devices({ omitDriverInstall: true });
    const device = devices.find((value) => value.serial() === serial);
    assert.ok(device, 'Emulator was not found by Playwright.');
    // /api answers only the shell's WebView (per-launch cookie), so the requests run in the page.
    const page = await (await device.webView({ pkg: 'org.tvshell.iptvnator' })).page();
    await page.waitForFunction(() => !document.getElementById('initial-splash'), null, { timeout: 60000 });
    async function connectionTest(url) {
        const result = await page.evaluate(async (providerUrl) => {
            const registration = await fetch('/api/provider-targets', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url: providerUrl }),
            });
            if (registration.status !== 200) return { registration: registration.status };
            const { targetId } = await registration.json();
            const params = new URLSearchParams({ targetId, connectionTest: 'true', username: 'synthetic-account', password: 'synthetic-secret' });
            const response = await fetch(`/api/xtream?${params}`, { signal: AbortSignal.timeout(20000) });
            return { registration: 200, status: response.status, body: await response.json() };
        }, url);
        assert.equal(result.registration, 200);
        assert.equal(result.status, 200);
        return result.body.connectionFailure;
    }
    assert.deepEqual(await connectionTest(closed), { kind: 'connection', canTryHttp: true });
    assert.equal((await connectionTest(`https://127.0.0.1:${reversed}`)).canTryHttp, false);
    assert.equal((await connectionTest(`http://127.0.0.1:${reversed}`)).canTryHttp, false);
    console.log('PASS Android preserves direct TCP refusal and rejects ambiguous TLS and redirect failures');
} finally {
    mock.close();
    if (reversed) adb('reverse', '--remove', `tcp:${reversed}`);
    for (const value of devices) await value.close(); // each device object polls adb until closed
}
