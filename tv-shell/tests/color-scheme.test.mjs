import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const TEMPLATE = readFileSync(new URL('../android/app/src/main/assets/tvshell/color-scheme.js', import.meta.url), 'utf8');

/** The shipped script, filled in the way ColorSchemeScript fills it, in a minimal page. */
function page(dark) {
    const nativeQueries = [];
    const window = {
        matchMedia(query) {
            nativeQueries.push(query);
            return { media: query, matches: false, native: true };
        },
    };
    assert.equal(TEMPLATE.split('__TVSHELL_DARK__').length, 2, 'the placeholder appears once');
    runInNewContext(TEMPLATE.replace('__TVSHELL_DARK__', String(dark)), { window, EventTarget, Event, WeakRef, Object, Set, String });
    return { window, nativeQueries };
}

test('answers prefers-color-scheme from the shell and nothing else', () => {
    const { window, nativeQueries } = page(true);
    assert.equal(window.matchMedia('(prefers-color-scheme: dark)').matches, true);
    assert.equal(window.matchMedia(' ( PREFERS-COLOR-SCHEME : light ) ').matches, false);
    assert.equal(window.matchMedia('(max-width: 640px)').native, true);
    assert.deepEqual(nativeQueries, ['(max-width: 640px)']);
});

test('announces a change to every kind of listener', () => {
    const { window } = page(false);
    const dark = window.matchMedia('(prefers-color-scheme: dark)');
    const light = window.matchMedia('(prefers-color-scheme: light)');
    const seen = [];
    dark.addEventListener('change', (event) => seen.push(['event', event.matches, event.media]));
    dark.onchange = (event) => seen.push(['onchange', event.matches]);
    light.addListener((event) => seen.push(['legacy', event.matches]));

    window.__tvShellColorScheme(true);

    assert.equal(dark.matches, true);
    assert.equal(light.matches, false);
    assert.deepEqual(seen, [
        ['event', true, '(prefers-color-scheme: dark)'],
        ['onchange', true],
        ['legacy', false],
    ]);
});

test('ignores a repeated or malformed state', () => {
    const { window } = page(true);
    const dark = window.matchMedia('(prefers-color-scheme: dark)');
    let changes = 0;
    dark.addEventListener('change', () => changes++);
    window.__tvShellColorScheme(true);
    window.__tvShellColorScheme('false');
    assert.equal(changes, 0);
    assert.equal(dark.matches, true);
});

test('a removed legacy listener hears nothing', () => {
    const { window } = page(false);
    const dark = window.matchMedia('(prefers-color-scheme: dark)');
    let calls = 0;
    const listener = () => calls++;
    dark.addListener(listener);
    dark.removeListener(listener);
    window.__tvShellColorScheme(true);
    assert.equal(calls, 0);
});
