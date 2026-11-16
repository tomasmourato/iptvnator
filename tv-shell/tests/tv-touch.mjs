/**
 * Touch mode (phones, tablets): the remote-control layer stays out of the way.
 * Run a touch dev server next to run-dev.sh's mock provider, then:
 *   node tv-shell/devserver/server.mjs --www dist/apps/web --port 43332 --touch
 *   node tv-shell/tests/tv-touch.mjs [--url http://127.0.0.1:43332] [--shots dir]
 */
import { mkdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const { chromium, devices } = require('@playwright/test');
const args = process.argv.slice(2);
const opt = (name, def) => {
    const i = args.indexOf(name);
    return i === -1 ? def : args[i + 1];
};
const URL_ = opt('--url', 'http://127.0.0.1:43332');
const PROVIDER = opt('--provider', 'http://127.0.0.1:43331');
const SHOTS = opt('--shots', join(dirname(fileURLToPath(import.meta.url)), 'shots'));
mkdirSync(SHOTS, { recursive: true });

const browser = await chromium.launch();
const context = await browser.newContext({ ...devices['Pixel 7'] });
const page = await context.newPage();
let failures = 0;
const check = (c, m) => { if (!c) failures++; console.log((c ? '  ✓ ' : '  ✗ ') + m); };
/** A tap once the element is visible. Animated surfaces (empty-state cards, dialogs opening) never
 *  count as stable for Playwright, so the tap is forced once the element is on screen. */
async function touch(locator) {
    await locator.waitFor({ state: 'visible' });
    await locator.scrollIntoViewIfNeeded();
    await page.waitForTimeout(300);
    await locator.tap({ force: true });
}
const rings = () => page.evaluate(() => ({
    focus: document.querySelectorAll('.tv-focus').length,
    madeFocusable: document.querySelectorAll('[data-tv-focusable]').length,
}));

try {
    await page.goto(URL_ + '/workspace/dashboard', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500); // the TV layer would have placed its initial focus by now

    console.log('1. the page is the touch page');
    const setup = await page.evaluate(() => ({
        viewport: document.querySelector('meta[name=viewport]')?.content,
        classes: document.documentElement.className,
        style: !!document.querySelector('style[data-tvshell]'),
        active: document.activeElement === document.body,
        width: window.innerWidth,
    }));
    check(setup.viewport === 'width=device-width, initial-scale=1', 'device-width viewport: ' + setup.viewport);
    check(/\btv-shell\b/.test(setup.classes) && /\btv-shell-touch\b/.test(setup.classes), 'html marked tv-shell and tv-shell-touch: ' + setup.classes);
    check(!setup.style, 'no remote-control stylesheet (ring, cursor, hidden scrollbars)');
    check(setup.active, 'nothing focused on load');
    check(setup.width <= 640, 'phone width: ' + setup.width + ' CSS px');

    console.log('2. phone header without desktop hints');
    const header = await page.evaluate(() => {
        const shown = (sel) => [...document.querySelectorAll(sel)].some((el) => el.getClientRects().length && getComputedStyle(el).display !== 'none' && getComputedStyle(el).visibility !== 'hidden');
        return { shortcut: shown('.command-trigger__shortcut'), keyboard: shown('.shortcuts-trigger') };
    });
    check(!header.shortcut, 'no Ctrl+K badge');
    check(!header.keyboard, 'no keyboard-shortcuts button');

    console.log('3. taps leave no TV focus ring, the list opens at the top');
    await page.goto(URL_ + '/workspace/sources', { waitUntil: 'networkidle' });
    await touch(page.getByRole('button', { name: /Add M3U/i }));
    const dialog = page.locator('mat-dialog-container');
    await touch(dialog.locator('input').nth(0));
    await dialog.locator('input').nth(0).fill(PROVIDER + '/sample.m3u');
    let r = await rings();
    check(r.focus === 0, 'no ring on the tapped URL field');
    await touch(dialog.getByRole('button', { name: /Add playlist/i }));
    await page.locator('.channel-name', { hasText: 'Playable clip' }).first().waitFor({ timeout: 15000 });
    await page.waitForTimeout(1200);
    const list = await page.evaluate(() => ({
        top: document.querySelector('cdk-virtual-scroll-viewport')?.scrollTop ?? -1,
        first: document.querySelector('.channel-name')?.textContent?.trim(),
    }));
    check(list.top === 0, 'channel list at the top (scrollTop ' + list.top + ')');
    check(/^1\./.test(list.first || ''), 'first row is channel 1: ' + list.first);
    await touch(page.locator('.channel-name', { hasText: 'Playable clip' }).first());
    await page.waitForTimeout(1500);
    r = await rings();
    check(r.focus === 0 && r.madeFocusable === 0, 'no ring and nothing made focusable after tapping a channel: ' + JSON.stringify(r));
    await page.keyboard.press('ArrowDown');
    await page.waitForTimeout(300);
    check((await rings()).focus === 0, 'arrow keys do not drive a TV focus ring');
    await page.screenshot({ path: join(SHOTS, 'touch-channel-list.png') });

    console.log('4. Back still goes through the shell layer');
    const before = new URL(page.url()).pathname;
    await page.goto(URL_ + '/workspace/settings', { waitUntil: 'networkidle' });
    const consumed = await page.evaluate(() => window.__tvShell.back());
    await page.waitForTimeout(800);
    check(consumed === true, '__tvShell.back() consumed by the page');
    check(new URL(page.url()).pathname !== '/workspace/settings', 'Back left the settings page (now ' + new URL(page.url()).pathname + ', came from ' + before + ')');
} catch (error) {
    failures++;
    console.log('  ✗ ' + (error && error.message ? error.message.split('\n')[0] : error));
} finally {
    await browser.close();
}
console.log(failures ? failures + ' check(s) failed' : 'all checks passed');
process.exit(failures ? 1 : 0);
