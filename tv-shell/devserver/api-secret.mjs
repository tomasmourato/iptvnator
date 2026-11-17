/**
 * The per-launch /api secret, as ApiSecret.java checks it: the request's X-TvShell-Api header must
 * equal the secret. In the APK, a document-start script (android/.../assets/tvshell/api-secret.js)
 * adds the header to the page's same-origin /api requests; tests inject the same script with
 * Playwright's addInitScript (apiSecretScript). The value is never logged.
 */
import { timingSafeEqual } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const API_SECRET_HEADER = 'x-tvshell-api';
const PLACEHOLDER = '__TVSHELL_API_SECRET__';
const TEMPLATE = join(dirname(fileURLToPath(import.meta.url)), '..', 'android', 'app', 'src', 'main', 'assets', 'tvshell', 'api-secret.js');

/** The script the APK injects, filled in with `secret` (see ApiSecret.startScript). */
export function apiSecretScript(secret) {
    const template = readFileSync(TEMPLATE, 'utf8');
    if (template.split(PLACEHOLDER).length !== 2) throw new Error('the /api script template must contain the placeholder once');
    return template.replace(PLACEHOLDER, secret);
}

/** Whether a request's X-TvShell-Api header value is `secret` (constant-time). */
export function hasApiSecret(headerValue, secret) {
    if (typeof headerValue !== 'string' || !secret) return false;
    const presented = Buffer.from(headerValue);
    const expected = Buffer.from(secret);
    return presented.length === expected.length && timingSafeEqual(presented, expected);
}
