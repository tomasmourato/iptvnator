import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';
import { apiSecretScript, hasApiSecret } from '../devserver/api-secret.mjs';
import { handleApi } from '../devserver/server.mjs';

const SECRET = 'a'.repeat(64);
const ORIGIN = 'http://127.0.0.1:43330';
const listen = (server) => new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const close = (...servers) => Promise.all(servers.map((server) => new Promise((resolve) => server.close(resolve))));

/** The shipped script in a minimal page at the shell's origin; returns what fetch/XHR sent. */
function page() {
    const sent = [];
    class PageRequest extends Request {
        constructor(input, init) {
            super(typeof input === 'string' ? new URL(input, `${ORIGIN}/`) : input, init);
        }
    }
    class FakeXhr {
        open(method, url) { this.request = { url: String(url), headers: {} }; }
        setRequestHeader(name, value) { this.request.headers[name.toLowerCase()] = value; }
        send() { sent.push(this.request); }
    }
    const window = {
        URL, Request: PageRequest, Headers, XMLHttpRequest: FakeXhr, location: { href: `${ORIGIN}/workspace`, origin: ORIGIN },
        fetch: async (input, init) => {
            const request = input instanceof Request ? input : new PageRequest(input, init);
            sent.push({ url: request.url, headers: Object.fromEntries(request.headers), method: request.method });
        },
    };
    runInNewContext(apiSecretScript(SECRET), { window });
    return { window, sent };
}

test('the script adds the secret to same-origin /api requests only', async () => {
    const { window, sent } = page();
    window.URL = class { constructor() { return { origin: ORIGIN, pathname: '/api/' }; } }; // later page changes do not count
    for (const url of ['/api/health', '/api', `${ORIGIN}/api/xtream?action=x`, new window.Request('/api/parse')]) await window.fetch(url);
    await window.fetch('/api/provider-targets', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
    for (const url of ['/apifoo', '/assets/app-config.js', 'http://127.0.0.1:43331/api/x', 'http://localhost:43330/api/x', 'https://provider.example/api/x']) {
        await window.fetch(url);
    }
    const carried = sent.map((request) => request.headers['x-tvshell-api'] === SECRET);
    assert.deepEqual(carried, [true, true, true, true, true, false, false, false, false, false]);
    assert.equal(sent[4].method, 'POST');
    assert.equal(sent[4].headers['content-type'], 'application/json');
    const xhr = new window.XMLHttpRequest();
    xhr.open('GET', '/api/xtream');
    xhr.send();
    xhr.open('GET', 'http://127.0.0.1:43331/api/x'); // the same object reused for another origin
    xhr.send();
    assert.deepEqual(sent.slice(-2).map((request) => request.headers['x-tvshell-api'] ?? null), [SECRET, null]);
    assert.equal(String(window.fetch).includes(SECRET), false, 'the secret is not in the wrapper source');
});

test('the header check accepts only the exact secret', () => {
    for (const value of [undefined, '', 'b'.repeat(64), `${SECRET}0`, ` ${SECRET}`, `${SECRET}, ${SECRET}`]) {
        assert.equal(hasApiSecret(value, SECRET), false);
    }
    assert.equal(hasApiSecret(SECRET, SECRET), true);
});

test('with a secret, /api refuses requests without it and never forwards it', async () => {
    const seen = [];
    const provider = createServer((req, res) => {
        seen.push(req.headers);
        res.end('{"synthetic":true}');
    });
    await listen(provider);
    const allowed = new URL(`http://127.0.0.1:${provider.address().port}`);
    const proxy = createServer((req, res) => handleApi(req, res, new URL(req.url, 'http://127.0.0.1'), [allowed], SECRET));
    await listen(proxy);
    const base = `http://127.0.0.1:${proxy.address().port}/api`;
    const good = { 'X-TvShell-Api': SECRET };
    const register = (headers) => fetch(`${base}/provider-targets`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify({ url: allowed.href }),
    });
    try {
        for (const headers of [{}, { 'X-TvShell-Api': 'b'.repeat(64) }, { Cookie: `tvshell_api=${SECRET}` }]) {
            for (const response of [await fetch(`${base}/health`, { headers }), await register(headers),
                await fetch(`${base}/xtream?targetId=x`, { headers }), await fetch(`${base}/health`, { method: 'OPTIONS', headers })]) {
                assert.equal(response.status, 403);
                assert.deepEqual(await response.json(), { message: 'Forbidden', status: 403 });
                assert.equal(response.headers.get('www-authenticate'), null, 'never a sign-in challenge');
            }
        }
        assert.equal(seen.length, 0, 'a refused request never reaches the provider');
        assert.equal((await fetch(`${base}/health`, { headers: good })).status, 200);
        const { targetId } = await (await register(good)).json();
        const result = await (await fetch(`${base}/xtream?targetId=${targetId}&action=get_live_categories`, { headers: good })).json();
        assert.deepEqual(result.payload, { synthetic: true });
        assert.equal(seen.length, 1);
        assert.equal(seen[0]['x-tvshell-api'], undefined, 'the secret never travels to the provider');
    } finally {
        await close(proxy, provider);
    }
});

test('without a secret the dev server behaves as before', async () => {
    const proxy = createServer((req, res) => handleApi(req, res, new URL(req.url, 'http://127.0.0.1'), [], null));
    await listen(proxy);
    try {
        assert.equal((await fetch(`http://127.0.0.1:${proxy.address().port}/api/health`)).status, 200);
    } finally {
        await close(proxy);
    }
});
