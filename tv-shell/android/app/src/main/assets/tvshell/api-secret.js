/*
 * IPTVnator TV shell — /api secret.
 *
 * Injected natively before any page script (AndroidX WebKit document-start script, registered
 * only for the shell's own loopback origin), never served by the local server. It adds the
 * launch's secret as the X-TvShell-Api header to same-origin /api requests made with fetch or
 * XMLHttpRequest, the only ways the web app reaches its backend. Nothing attaches the header
 * automatically, so a redirect or a URL pointing at another local server never carries it.
 * The secret stays in this closure; the shell fills in the placeholder per launch.
 */
(() => {
    'use strict';
    const HEADER = 'X-TvShell-Api';
    const SECRET = '__TVSHELL_API_SECRET__';
    // Captured before page scripts run, so later changes to these globals cannot redirect the secret.
    const NativeURL = window.URL;
    const nativeFetch = window.fetch;
    const NativeRequest = window.Request;
    const setFetchHeader = window.Headers?.prototype.set;
    const xhr = window.XMLHttpRequest?.prototype;
    const open = xhr?.open;
    const send = xhr?.send;
    const setXhrHeader = xhr?.setRequestHeader;
    const apiRequests = new WeakSet();
    const { add: markApi, delete: unmark, has: isMarked } = WeakSet.prototype;

    const isApi = (url) => {
        try {
            const target = new NativeURL(String(url), window.location.href);
            return target.origin === window.location.origin && (target.pathname === '/api' || target.pathname.startsWith('/api/'));
        } catch {
            return false;
        }
    };

    if (nativeFetch && NativeRequest && setFetchHeader) {
        window.fetch = function (...args) {
            const [input, init] = args;
            if (!isApi(input instanceof NativeRequest ? input.url : input)) return nativeFetch.apply(window, args);
            const request = new NativeRequest(input, init);
            setFetchHeader.call(request.headers, HEADER, SECRET);
            return nativeFetch.call(window, request);
        };
    }

    if (open && send && setXhrHeader) {
        xhr.open = function (...args) {
            if (isApi(args[1])) markApi.call(apiRequests, this);
            else unmark.call(apiRequests, this);
            return open.apply(this, args);
        };
        xhr.send = function (...args) {
            if (isMarked.call(apiRequests, this)) setXhrHeader.call(this, HEADER, SECRET);
            return send.apply(this, args);
        };
    }
})();
