/** The built-in player's decoder fallback, on a debug APK on a test emulator. Requires ffmpeg
 * with libx264 and an emulator with both a hardware and a software H.264 decoder (the phone
 * AVDs have c2.goldfish.h264 and c2.android.avc):
 * ANDROID_HOME=/path/to/sdk node tv-shell/tests/android-native-fallback.mjs emulator-5554
 * TVSHELL_PACKAGE picks the installed app (default org.tvshell.iptvnator; the StaticTV flavor is
 * io.github.tomasmourato.tv). The checks use the debug-only files/debug-force-stall hook. StaticTV
 * has an FFmpeg decoder provider, so it falls back to ffmpeg instead of the platform's software
 * decoder; the hook never stalls that decoder, so the remembered-stall and all-stall steps are
 * skipped there.
 */
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { _android, expect } from '@playwright/test';

const serial = process.argv[2];
assert.ok(serial?.startsWith('emulator-') && process.env.ANDROID_HOME,
    'Set ANDROID_HOME and pass an emulator serial.');
const app = process.env.TVSHELL_PACKAGE || 'org.tvshell.iptvnator';
const main = `${app}/org.tvshell.iptvnator.ui.MainActivity`;
const withProvider = app === 'io.github.tomasmourato.tv';
const fallback = withProvider ? 'ffmpeg' : 'software';
const fallbackDecoder = withProvider ? String.raw`\S+ via ffmpeg` : String.raw`c2\.android\.avc\.decoder`;
const adb = (...args) => execFileSync(join(process.env.ANDROID_HOME, 'platform-tools/adb'),
    ['-s', serial, ...args], { encoding: 'utf8', timeout: 20000 });
const dumpLayout = () => adb('exec-out', 'uiautomator', 'dump', '--compressed', '/dev/tty');
const topActivity = () => adb('shell', 'dumpsys', 'activity', 'activities')
    .split('\n').find((line) => line.includes('topResumedActivity')) || '';
const appLog = () => adb('logcat', '-d', '-v', 'brief', `--pid=${adb('shell', 'pidof', app).trim()}`);
const decoderLog = () => appLog().split('\n').filter((line) => line.includes('native decoder:'));
// One argument, so the redirect runs inside run-as; files/ is gone after a pm clear.
const forceStall = (content) => adb('shell', `run-as ${app} sh -c 'mkdir -p files && echo ${content} > files/debug-force-stall'`);

const fixtures = mkdtempSync(join(tmpdir(), 'iptvnator-fallback-test-'));
let server;
let port;
let device;
try {
    execFileSync('ffmpeg', ['-nostdin', '-loglevel', 'error',
        '-f', 'lavfi', '-i', 'testsrc2=size=320x180:rate=25',
        '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=48000',
        '-t', '60', '-c:v', 'libx264', '-g', '25', '-pix_fmt', 'yuv420p', '-c:a', 'aac',
        '-hls_time', '2', '-hls_list_size', '0', join(fixtures, 'fallback.m3u8')]);
    const manifestRequests = { count: 0 };
    server = createServer((req, res) => {
        const name = req.url.slice(1);
        if (!/^fallback\d*\.(m3u8|ts)$/.test(name)) return res.writeHead(404).end();
        if (name.endsWith('.m3u8')) manifestRequests.count++;
        // No CORS: the WebView player cannot load it, so the page offers the built-in player.
        res.writeHead(200, { 'Content-Type': name.endsWith('.ts') ? 'video/mp2t' : 'application/vnd.apple.mpegurl' });
        res.end(readFileSync(join(fixtures, name)));
    });
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    port = server.address().port;
    adb('reverse', `tcp:${port}`, `tcp:${port}`);
    const stream = `http://127.0.0.1:${port}/fallback.m3u8`;

    adb('shell', 'am', 'start', '-n', main);
    device = (await _android.devices({ omitDriverInstall: true })).find((item) => item.serial() === serial);
    assert.ok(device, 'Emulator was not found.');
    const page = await (await device.webView({ pkg: app })).page();
    page.setDefaultTimeout(20000);
    await page.waitForFunction(() => window.__tvShell && !document.getElementById('initial-splash'));
    async function setNativePlayback(enabled) {
        await page.evaluate(() => window.TvShell.postMessage('["openSettings"]'));
        await expect.poll(topActivity).toContain('.ui.SettingsActivity');
        const pattern = new RegExp(`<node [^>]*resource-id="${app.replaceAll('.', '\\.')}:id/native_playback"[^>]*>`);
        let checkbox;
        await expect.poll(() => (checkbox = dumpLayout().match(pattern)?.[0]), { timeout: 30000 }).toBeDefined();
        if (checkbox.includes('checked="true"') !== enabled) {
            const [left, top, right, bottom] = checkbox.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/).slice(1).map(Number);
            adb('shell', 'input', 'tap', String((left + right) / 2), String((top + bottom) / 2));
        }
        adb('shell', 'input', 'keyevent', '4');
        await page.waitForFunction((value) => window.iptvnatorAndroidPlaybackEnabled === value, enabled);
    }
    const waitNative = () => expect.poll(topActivity).toContain('.ui.NativePlayerActivity');
    async function back() {
        adb('shell', 'input', 'keyevent', '4');
        await expect.poll(topActivity).toContain('.ui.MainActivity');
    }
    await setNativePlayback(true);
    await page.evaluate(() => window.addEventListener('iptvnator:android-playback-result', (event) => {
        window.__nativeResult = event.detail.result;
    }));

    async function play() {
        adb('logcat', '-c');
        await page.evaluate((url) => {
            delete window.__nativeResult;
            window.TvShell.postMessage(JSON.stringify(['playNative', crypto.randomUUID(), { url, hls: true }]));
        }, stream);
        await waitNative();
    }
    const memory = () => adb('shell', 'run-as', app, 'cat', 'shared_prefs/decoder_memory.xml');
    adb('shell', 'run-as', app, 'rm', '-f', 'shared_prefs/decoder_memory.xml');

    // 1. The hardware decoder takes input and shows nothing: the next decoder takes over.
    forceStall('first');
    await play();
    const requestsBefore = manifestRequests.count;
    await expect.poll(decoderLog, { timeout: 30000 }).toEqual(expect.arrayContaining([
        expect.stringMatching(new RegExp(String.raw`native decoder: hardware -> ${fallback} \(stall\), decoder=\S+, queued=\d+, rendered=0`)),
        expect.stringMatching(new RegExp(`native decoder: ${fallback} rendering, decoder=${fallbackDecoder}`)),
    ]));
    assert.ok(manifestRequests.count > requestsBefore, `The ${fallback} stage reloads the stream.`);
    await back();
    await page.waitForFunction(() => window.__nativeResult !== undefined);
    const played = await page.evaluate(() => window.__nativeResult);
    assert.equal(played.failed, false, `The ${fallback} stage played the stream.`);
    assert.equal(played.started, true);

    // 2. The next playback of that stream starts on the decoder that worked; only a hash is kept.
    await expect.poll(memory).toMatch(new RegExp(String.raw`name="[0-9a-f]{64}">${fallback}:\d+<`));
    assert.ok(!memory().includes(`:${port}`) && !memory().includes('fallback'), 'The memory keeps a stream address.');
    adb('shell', 'run-as', app, 'rm', '-f', 'files/debug-force-stall');
    await play();
    await expect.poll(decoderLog, { timeout: 20000 }).toEqual([
        expect.stringMatching(new RegExp(String.raw`native decoder: starting on ${fallback} \(remembered\)`)),
        expect.stringMatching(new RegExp(`native decoder: ${fallback} rendering, decoder=${fallbackDecoder}`)),
    ]);
    let log = appLog();
    await back();
    if (withProvider) {
        assert.ok(!log.includes(`:${port}`) && !log.includes('fallback.m3u8'), 'The app log contains the stream URL.');
        await setNativePlayback(false);
        console.log('PASS a stalled hardware decoder falls back to the ffmpeg provider, which the next playback '
            + 'starts on; no stream URL in the log');
        console.log('SKIP the remembered-stall and all-stall steps: the debug hook never stalls the provider');
    } else {
        // 3. The remembered decoder stalls: the entry goes and the chain starts over on hardware.
        forceStall('first'); // the first stage is now the remembered software decoder
        await play();
        await expect.poll(decoderLog, { timeout: 30000 }).toEqual(expect.arrayContaining([
            expect.stringMatching(/native decoder: software -> hardware \(stall, remembered stage dropped\)/),
            expect.stringMatching(/native decoder: hardware rendering, decoder=c2\.goldfish\.h264\.decoder/),
        ]));
        await back();
        await expect.poll(memory).not.toMatch(/[0-9a-f]{64}/);

        // 4. Every decoder stalls: failed, and the page offers an external player.
        forceStall('all');
        adb('logcat', '-c');
        await page.goto(new URL('/', page.url()).href);
        await page.getByRole('button', { name: 'Add playlist' }).click();
        const dialog = page.getByRole('dialog');
        await dialog.getByRole('radio', { name: /Raw m3u text/i }).click();
        await dialog.getByLabel('Insert m3u(8) playlist as text').fill(
            ['#EXTM3U', '#EXTINF:-1 group-title="Live",Synthetic Fallback', stream].join('\n'));
        await Promise.all([
            page.waitForURL(/\/workspace\/playlists\/.+\/all$/),
            dialog.getByRole('button', { name: 'Add playlist', exact: true }).click(),
        ]);
        const entry = page.locator('[data-test-id="channel-item"]').filter({ hasText: 'Synthetic Fallback' });
        await expect(entry).toBeVisible({ timeout: 60000 });
        await entry.click();
        const action = page.locator('app-web-player-view').getByRole('button', { name: 'Try built-in player' });
        await expect(action).toBeVisible({ timeout: 30000 });
        await action.click();
        await waitNative();
        await expect.poll(() => dumpLayout().includes('The built-in player could not play this stream'),
            { timeout: 40000 }).toBe(true);
        const steps = decoderLog();
        assert.ok(steps.some((line) => line.includes('hardware -> software (stall)')), steps.join('\n'));
        assert.ok(steps.some((line) => line.includes('software -> failed (stall)')), steps.join('\n'));
        log = appLog();
        await back();
        await expect.poll(() => dumpLayout().includes('This stream cannot be played here')).toBe(true);

        // 5. The decoder lines and the rest of the app's log never carry the stream address.
        assert.ok(!log.includes(`:${port}`) && !log.includes('fallback.m3u8'), 'The app log contains the stream URL.');
        adb('shell', 'input', 'keyevent', '4');
        await setNativePlayback(false);
        console.log('PASS a stalled hardware decoder falls back to the software decoder, which the next playback starts '
            + 'on until it stalls; a stall on every stage fails and offers an external player; no stream URL in the log');
    }
} finally {
    try {
        adb('shell', 'run-as', app, 'rm', '-f', 'files/debug-force-stall', 'shared_prefs/decoder_memory.xml');
        if (topActivity().includes('.ui.NativePlayerActivity')) adb('shell', 'input', 'keyevent', '4');
        if (port) adb('reverse', '--remove', `tcp:${port}`);
    } finally {
        await device?.close();
        server?.closeAllConnections();
        server?.close();
        rmSync(fixtures, { recursive: true, force: true });
    }
}
